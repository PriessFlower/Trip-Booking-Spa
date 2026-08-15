package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongRestCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongOrderDetailRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongRequestEnvelope;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderDetailResponse;
import com.trip.booking.spa.gateway.application.order.AbstractOrderQuerySyncSupportService;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 艺龙查单。bean 名 {@code elongOrderQuerySyncService} 供能力注册表发现。
 *
 * <p>兑现下单三态契约：下单回报 UNKNOWN 时上游凭我方单号（AffiliateConfirmationId）反查。
 * 供应商订单号可选——有则优先（官方建议，按我方单号反查有底层同步延迟）。
 *
 * <p><b>NOT_FOUND 判据只认 H001054</b>（官方：订单不存在）。该判定许可上游安全重下——
 * 即便撞上"订单生成初期反查延迟"的窗口误判了，重下用同一 AffiliateConfirmationId，
 * 供应商幂等返回原单号，不会重复建单（官方文档，双保险）。
 */
@Slf4j
@Service("elongOrderQuerySyncService")
public class ElongOrderQuerySyncServiceImpl
        extends AbstractOrderQuerySyncSupportService<ElongOrderDetailResponse> {

    /** 我方订单状态码，取值含义见 {@link OrderRespDTO#orderStatus} */
    private static final int ORDER_STATUS_BOOKING = 20;
    private static final int ORDER_STATUS_BOOK_SUCCESS = 21;
    private static final int ORDER_STATUS_BOOK_FAIL = 22;
    private static final int ORDER_STATUS_CANCELING = 30;
    private static final int ORDER_STATUS_CANCEL_SUCCESS = 31;

    @Resource
    private ElongProperties properties;

    @Override
    public ElongOrderDetailResponse doOrderQuery(OrderQueryReq req) {
        if (!properties.isConfigured()) {
            log.error("艺龙查单：凭证未配置,orderId={}", req.getOrderId());
            return null;
        }
        Long supplierOrderId = parseLongQuietly(req.getSupplierOrderId());
        // OrderId 优先；按我方单号反查时必须显式传 0（cursor 生产教训：缺省报语义不清的 H001054）
        ElongOrderDetailRequest request = ElongOrderDetailRequest.builder()
                .orderId(supplierOrderId == null ? 0L : supplierOrderId)
                .affiliateConfirmationId(req.getOrderId())
                .build();
        String dataJson = JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(), request));
        ResponseResult<ElongOrderDetailResponse> result = new QueryOrderAccess(properties)
                .access(new ElongRestCall("hotel.order.detail", dataJson));
        return result == null ? null : result.getData();
    }

    @Override
    public OrderRespDTO orderQueryRespConvert(ElongOrderDetailResponse resp) {
        if (!resp.isSucc()) {
            String errorCode = StringUtils.trimToEmpty(resp.errorCode());
            if (errorCode.startsWith("H001054")) {
                // 官方：订单不存在。可安全判"确实没这单"（幂等双保险见类注释）
                return OrderRespDTO.builder()
                        .presence(OrderPresence.NOT_FOUND)
                        .message("供应商确认订单不存在(H001054)")
                        .build();
            }
            log.warn("艺龙查单：业务错误按不确定处理,code={}", resp.getCode());
            return OrderRespDTO.builder()
                    .presence(OrderPresence.INDETERMINATE)
                    .message("查单未取得确定结果(" + errorCode + ")")
                    .build();
        }
        ElongOrderDetailResponse.Result result = resp.getResult();
        if (result == null || result.getOrderId() == null) {
            log.error("艺龙查单：响应自相矛盾——成功但无订单号");
            return OrderRespDTO.builder()
                    .presence(OrderPresence.INDETERMINATE)
                    .message("查单响应自相矛盾：报告成功但未给出订单号")
                    .build();
        }
        Integer orderStatus = mapOrderStatus(result.getStatus());
        if (orderStatus == null) {
            // §6.2.1：映射不上不是常态，必须有落点；状态原文随响应透出
            log.warn("艺龙查单：状态原文无法映射,sOrderId={},status={}", result.getOrderId(), result.getStatus());
        }
        return OrderRespDTO.builder()
                .presence(OrderPresence.FOUND)
                .supplierOrderId(String.valueOf(result.getOrderId()))
                .orderStatus(orderStatus)
                .supplierOrderStatus(result.getStatus())
                .confirmationNumber(extractConfirmationNumber(result.getOrderRooms()))
                .totalPrice(yuanToCents(result.getTotalPrice()))
                .createTime(result.getCreationDate())
                .build();
    }

    /**
     * 艺龙订单状态原文 → 我方状态码（官方状态表，2026-08-15 核对；含义见
     * {@link ElongOrderDetailResponse} javadoc）。
     *
     * <p>映射纪律：只映射语义铁定的取值，<b>识别不出的一律返回 null</b> 并保留原文
     * ——猜默认值会把未知状态说成已知（cursor 反面：把 H"变更"映成"已取消"）。
     * <ul>
     *   <li>已成立：A已确认 / C已结账 / F已入住 / B NO-SHOW（订单成立过且未取消）→ 21</li>
     *   <li>处理中：N新单 / V已审 / B1 B2 B3 待查类 / G变价 / H变更 → 20</li>
     *   <li>取消中：E1（官方表外，真单实测 2026-08-15 单 101067194262：取消受理后
     *       立即出现、约 30 秒后翻转为 D——即"取消处理中"）→ 30</li>
     *   <li>已取消：E取消 / D删除 / Z删除另换酒店 → 31</li>
     *   <li>未成立：O满房 / U特殊满房 → 22（下单被满房打回）</li>
     *   <li>S特殊：语义不明，不映射</li>
     * </ul>
     */
    static Integer mapOrderStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        switch (status.trim().toUpperCase()) {
            case "A":
            case "B":
            case "C":
            case "F":
                return ORDER_STATUS_BOOK_SUCCESS;
            case "N":
            case "V":
            case "B1":
            case "B2":
            case "B3":
            case "G":
            case "H":
                return ORDER_STATUS_BOOKING;
            case "E1":
                return ORDER_STATUS_CANCELING;
            case "E":
            case "D":
            case "Z":
                return ORDER_STATUS_CANCEL_SUCCESS;
            case "O":
            case "U":
                return ORDER_STATUS_BOOK_FAIL;
            default:
                return null;
        }
    }

    /** 酒店确认号埋在 OrderRooms[i].RoomConfirmationNumber，取首个非空（cursor 实证） */
    static String extractConfirmationNumber(JsonNode orderRooms) {
        if (orderRooms == null || !orderRooms.isArray()) {
            return null;
        }
        for (JsonNode room : orderRooms) {
            for (String field : new String[]{"RoomConfirmationNumber", "HotelConfirmationNumber", "ConfirmationNumber"}) {
                JsonNode v = room.get(field);
                if (v != null && StringUtils.isNotBlank(v.asText(null))) {
                    return v.asText();
                }
            }
        }
        return null;
    }

    private static Integer yuanToCents(BigDecimal yuan) {
        return yuan == null ? null : yuan.multiply(BigDecimal.valueOf(100)).intValue();
    }

    private static Long parseLongQuietly(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
