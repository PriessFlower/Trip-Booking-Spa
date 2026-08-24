package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.cancellation;

import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.cancellation.client.CancelRoomAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CancelRoomResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CreateOrderResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryOrderResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaBookingContact;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
import com.trip.booking.spa.gateway.application.cancellation.AbstractCancelSyncSupportService;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Expedia 取消订单。
 *
 * <p><b>取消必然是两步</b>：Expedia 不提供「按单号取消」的接口，取消链接只在反查响应的
 * {@code rooms[].links.cancel.href} 里给出。故流程固定为「先凭我方单号反查取链接 →
 * 再逐房 DELETE」。这也意味着取消的前提是反查成功——反查不通时不能断言取消失败。
 *
 * <p><b>为什么以我方单号为坐标</b>：最需要取消的场景恰是下单结果不确定时，而那时上游
 * 没有供应商订单号。故与查单一致，用 {@code affiliate_reference_id} 反查。
 *
 * <p><b>部分成功的判读</b>：一笔多房订单可能取消到一半失败。此时既不能判 SUCCESS
 * （还有房间占着），也不能判 FAILED（已有房间被取消，状态已改变，上游若据此认为
 * 订单完好会与实际不符）。故一律判 UNKNOWN，由上游查单确证后再处置。
 */
@Slf4j
@Service("expediaCancelSyncService")
public class ExpediaCancelSyncServiceImpl
        extends AbstractCancelSyncSupportService<ExpediaCancelSyncServiceImpl.CancelResult> {

    /** Expedia 房间状态原文：已取消 */
    private static final String STATUS_CANCELED = "canceled";

    /** 对外的取消状态码：0 取消成功 */
    private static final int S_ORDER_STATUS_CANCELED = 0;
    /** 对外的取消状态码：1 取消中／结果待确证 */
    private static final int S_ORDER_STATUS_PENDING = 1;
    /** 对外的取消状态码：2 取消失败 */
    private static final int S_ORDER_STATUS_FAILED = 2;

    @Value("${expedia.url.host}")
    private String host;
    @Value("${expedia.session}")
    private String sessionId;
    @Value("${expedia.ownIp}")
    private String ownIp;

    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;
    @Resource
    private ExpediaBookingContact bookingContact;

    @Override
    public CancelResult doCancel(CancelReq req) {
        // 邮箱必须与下单时一致，否则 Expedia 不返回结果；故取同一份配置，不由调用方传入
        String email = bookingContact.getContact().getEmail();
        if (!StringUtils.hasText(email)) {
            // 配置缺失属我方问题，且重试不会改变——但也不能判 FAILED：
            // 订单可能好端端存在，只是我们查不到它
            log.error("expedia 取消缺少 booking-contact 邮箱，无法反查, orderId={}", req.getOrderId());
            return CancelResult.unknown("取消所需的反查邮箱未配置，无法确认订单状态");
        }

        ResponseResult<QueryOrderResponse> lookup = new QueryOrderAccess(
                host, req.getOrderId(), email, expediaUtils.generateSign(),
                ownIp, sessionId, rateLimiter).access("", CallPurpose.ORDER);

        QueryOrderResponse resp = lookup == null ? null : lookup.getData();
        if (resp == null || resp.getPresence() == QueryOrderResponse.Presence.INDETERMINATE) {
            // 反查不通，无从得知订单是否存在，更无从取消
            return CancelResult.unknown("反查订单未取得结果，取消未执行，请稍后重试或查单确证");
        }
        if (resp.getPresence() == QueryOrderResponse.Presence.NOT_FOUND) {
            // 确证订单不存在。重试必得同样结果，故判 FAILED 而非 UNKNOWN
            return CancelResult.failed("供应商侧不存在该订单，无可取消");
        }

        QueryOrderResponse.Itinerary itinerary = resp.firstItinerary();
        List<CreateOrderResponse.Room> rooms = itinerary == null ? null : itinerary.getRooms();
        if (rooms == null || rooms.isEmpty()) {
            log.error("expedia 取消：反查报告订单存在但无房间明细, orderId={}", req.getOrderId());
            return CancelResult.unknown("反查响应自相矛盾：订单存在但无房间明细");
        }

        String itineraryId = itinerary.getItinerary_id();
        int alreadyCanceled = 0;
        int canceled = 0;
        List<String> failures = new ArrayList<>();

        for (CreateOrderResponse.Room room : rooms) {
            if (STATUS_CANCELED.equalsIgnoreCase(room.getStatus())) {
                alreadyCanceled++;
                continue;
            }
            String href = room.getLinks() == null || room.getLinks().getCancel() == null
                    ? null : room.getLinks().getCancel().getHref();
            if (!StringUtils.hasText(href)) {
                // 房间未取消却没给取消链接——多为已过取消期限，属确定性拒绝，但本类不越权判定，
                // 交由整体判 UNKNOWN，让上游查单看真实状态
                failures.add("房间无取消链接（可能已过取消期限）");
                continue;
            }
            ResponseResult<CancelRoomResponse> result = new CancelRoomAccess(
                    absolute(href), expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access("", CallPurpose.ORDER);
            if (result != null && isCancelAccepted(result)) {
                canceled++;
            } else {
                failures.add(describeFailure(result));
            }
        }

        if (!failures.isEmpty()) {
            // 部分成功同样落此分支：已有房间被取消，状态已改变，不能报 FAILED
            log.error("expedia 取消未全部成功, orderId={}, 成功={}, 原已取消={}, 失败={}",
                    req.getOrderId(), canceled, alreadyCanceled, failures);
            return CancelResult.unknown(itineraryId,
                    "部分房间未能取消（成功 " + canceled + " 间，失败 " + failures.size()
                            + " 间），订单状态已改变，请查单确证：" + String.join("；", failures));
        }
        if (canceled == 0 && alreadyCanceled > 0) {
            // 全部本就已取消，等同于取消成功
            return CancelResult.success(itineraryId, "订单的全部房间此前已取消");
        }
        return CancelResult.success(itineraryId, null);
    }

    @Override
    public CancelRespDTO cancelRespConvert(CancelResult result) {
        return CancelRespDTO.builder()
                .outcome(result.outcome)
                .sOrderId(result.itineraryId)
                .sOrderStatus(statusCodeOf(result.outcome))
                .message(result.message)
                .orderDesc(result.message)
                .build();
    }

    /**
     * 取消是否被接受。
     *
     * <p>判据是 <b>2xx</b> 而非 200：取消成功时 Expedia 返回 204。
     *
     * <p>另有两类响应虽非 2xx，但同样表示「目的已达成」，故一并算作成功：
     * <ul>
     *   <li><b>已取消</b>——该房间正处于我们想要的终态。报失败会让上游误以为还占着房</li>
     *   <li><b>不存在</b>——该房间已不在 Expedia 侧，无可取消</li>
     * </ul>
     * 二者重试都不会改变结果，判失败只会诱使上游反复重试一个永远不变的状态。
     */
    private boolean isCancelAccepted(ResponseResult<CancelRoomResponse> result) {
        int code = result.getHttpStatus();
        if (code >= 200 && code < 300) {
            return true;
        }
        CancelRoomResponse data = result.getData();
        if (data == null) {
            return false;
        }
        String type = data.getType() == null ? "" : data.getType().toLowerCase();
        String message = data.getMessage() == null ? "" : data.getMessage().toLowerCase();
        return type.contains("not_found")
                || type.contains("already_cancel")
                || message.contains("already cancelled")
                || message.contains("already canceled");
    }

    private String describeFailure(ResponseResult<CancelRoomResponse> result) {
        if (result == null) {
            return "无响应";
        }
        CancelRoomResponse data = result.getData();
        String detail = data == null ? null : data.getMessage();
        return "HTTP " + result.getHttpStatus() + (StringUtils.hasText(detail) ? "：" + detail : "");
    }

    /** 反查给出的链接可能是相对路径，补全为绝对地址 */
    private String absolute(String href) {
        return href.startsWith("http") ? href : host + href;
    }

    private Integer statusCodeOf(CancelOutcome outcome) {
        if (outcome == CancelOutcome.SUCCESS) {
            return S_ORDER_STATUS_CANCELED;
        }
        return outcome == CancelOutcome.FAILED ? S_ORDER_STATUS_FAILED : S_ORDER_STATUS_PENDING;
    }

    /** 取消编排的中间结果；仅在本实现内部流转，不出网关 */
    public static final class CancelResult {
        private final CancelOutcome outcome;
        private final String itineraryId;
        private final String message;

        private CancelResult(CancelOutcome outcome, String itineraryId, String message) {
            this.outcome = outcome;
            this.itineraryId = itineraryId;
            this.message = message;
        }

        static CancelResult success(String itineraryId, String message) {
            return new CancelResult(CancelOutcome.SUCCESS, itineraryId, message);
        }

        static CancelResult failed(String message) {
            return new CancelResult(CancelOutcome.FAILED, null, message);
        }

        static CancelResult unknown(String message) {
            return new CancelResult(CancelOutcome.UNKNOWN, null, message);
        }

        static CancelResult unknown(String itineraryId, String message) {
            return new CancelResult(CancelOutcome.UNKNOWN, itineraryId, message);
        }
    }
}
