package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.order;

import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CreateOrderResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryOrderResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaBookingContact;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.application.order.AbstractOrderQuerySyncSupportService;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Expedia 查单。
 *
 * <p>bean 名 {@code expediaOrderQuerySyncService} 供 {@code SpaController} 按供应商路由发现。
 *
 * <p><b>本类兑现的是下单三态契约里那句承诺</b>：下单回报 {@code UNKNOWN} 时上游可凭我方单号
 * 反查确证。故入参只需 {@link OrderQueryReq#getOrderId()}，不要求供应商订单号——那种场景下
 * 上游本来就没有它。
 *
 * <p>反查响应比下单响应丰富：<b>酒店确认号与取消链接只在这里出现</b>，下单响应中没有。
 */
@Slf4j
@Service("expediaOrderQuerySyncService")
public class ExpediaOrderQuerySyncServiceImpl
        extends AbstractOrderQuerySyncSupportService<QueryOrderResponse> {

    /** Expedia 房间状态原文 */
    private static final String STATUS_BOOKED = "booked";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CANCELED = "canceled";

    /** 我方订单状态码，取值含义见 {@link OrderRespDTO#orderStatus} */
    private static final int ORDER_STATUS_BOOKING = 20;
    private static final int ORDER_STATUS_BOOK_SUCCESS = 21;
    private static final int ORDER_STATUS_CANCEL_SUCCESS = 31;

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
    @Autowired
    private ExpediaBookingContact bookingContact;

    @Override
    public QueryOrderResponse doOrderQuery(OrderQueryReq req) {
        // 邮箱必须与下单时一致，否则 Expedia 不返回结果；故取同一份配置，不由调用方传入
        String email = bookingContact.getContact().getEmail();
        if (email == null || email.isBlank()) {
            // 配置缺失时不能报「订单不存在」——那会诱导上游重新下单
            log.error("expedia 查单缺少 booking-contact 邮箱，无法反查, orderId={}", req.getOrderId());
            return null;
        }

        ResponseResult<QueryOrderResponse> result = new QueryOrderAccess(
                host, req.getOrderId(), email, expediaUtils.generateSign(),
                ownIp, sessionId, rateLimiter).access("", CallPurpose.ORDER);

        return result == null ? null : result.getData();
    }

    @Override
    public OrderRespDTO orderQueryRespConvert(QueryOrderResponse resp) {
        if (resp.getPresence() != QueryOrderResponse.Presence.FOUND) {
            return OrderRespDTO.builder()
                    .presence(resp.getPresence() == QueryOrderResponse.Presence.NOT_FOUND
                            ? OrderPresence.NOT_FOUND : OrderPresence.INDETERMINATE)
                    .message(resp.getMessage())
                    .build();
        }

        QueryOrderResponse.Itinerary itinerary = resp.firstItinerary();
        if (itinerary == null || itinerary.getItinerary_id() == null || itinerary.getItinerary_id().isBlank()) {
            // 说 FOUND 却拿不出订单号，属响应自相矛盾，按不确定处理
            log.error("expedia 查单返回 FOUND 但无订单号，按 INDETERMINATE 处理");
            return OrderRespDTO.builder()
                    .presence(OrderPresence.INDETERMINATE)
                    .message("查单响应自相矛盾：报告订单存在但未给出订单号")
                    .build();
        }

        List<CreateOrderResponse.Room> rooms = itinerary.getRooms();
        return OrderRespDTO.builder()
                .presence(OrderPresence.FOUND)
                .supplierOrderId(itinerary.getItinerary_id())
                .orderStatus(mapOrderStatus(rooms))
                .supplierOrderStatus(firstRoomStatus(rooms))
                .confirmationNumber(firstConfirmationNumber(rooms))
                .build();
    }

    /**
     * 由各房间状态归纳订单状态。
     *
     * <p>规则：全部房间取消 → 取消成功；否则任一房间已订 → 预定成功；否则任一在处理 → 预定中。
     * <b>识别不出的取值一律返回 null</b>，由 {@code supplierOrderStatus} 保留原文——
     * 猜一个默认值会把未知状态说成已知，上游据此做的每一步都是错的。
     */
    static Integer mapOrderStatus(List<CreateOrderResponse.Room> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return null;
        }
        boolean anyBooked = false;
        boolean anyPending = false;
        boolean allCanceled = true;
        boolean anyRecognized = false;
        for (CreateOrderResponse.Room room : rooms) {
            String status = room == null ? null : room.getStatus();
            if (STATUS_BOOKED.equalsIgnoreCase(status)) {
                anyBooked = true;
                anyRecognized = true;
                allCanceled = false;
            } else if (STATUS_PENDING.equalsIgnoreCase(status)) {
                anyPending = true;
                anyRecognized = true;
                allCanceled = false;
            } else if (STATUS_CANCELED.equalsIgnoreCase(status)) {
                anyRecognized = true;
            } else {
                allCanceled = false;
            }
        }
        if (!anyRecognized) {
            return null;
        }
        if (allCanceled) {
            return ORDER_STATUS_CANCEL_SUCCESS;
        }
        if (anyBooked) {
            return ORDER_STATUS_BOOK_SUCCESS;
        }
        return anyPending ? ORDER_STATUS_BOOKING : null;
    }

    private static String firstRoomStatus(List<CreateOrderResponse.Room> rooms) {
        if (rooms == null || rooms.isEmpty() || rooms.get(0) == null) {
            return null;
        }
        return rooms.get(0).getStatus();
    }

    private static String firstConfirmationNumber(List<CreateOrderResponse.Room> rooms) {
        if (rooms == null || rooms.isEmpty() || rooms.get(0) == null) {
            return null;
        }
        CreateOrderResponse.ConfirmationId id = rooms.get(0).getConfirmation_id();
        return id == null ? null : id.getExpedia();
    }
}
