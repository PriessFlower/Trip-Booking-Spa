package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.DidaBookingClassifier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order.client.BookingSearchAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingDetails;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.gateway.application.order.AbstractOrderQuerySyncSupportService;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.shared.Money;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 道旅查单。bean 名 {@code didaOrderQuerySyncService} 供能力注册表发现。
 *
 * <p>坐标：有供应商单号按 {@code BookingID} 查，否则按 {@code ClientReference}=我方单号查
 * （两种都是官方标「推荐」的查法）——下单回报 UNKNOWN 时上游恰恰没有供应商单号（B5）。
 *
 * <p><b>NOT_FOUND 永不判</b>：官方明示空返回不能视为终态；cursor 生产一周 18,424 次查单里
 * 19 次空列表全是按我方单号查、紧跟在下单之后（如 2026-09-12 22:15:07 同号两次皆空），
 * 即"单还在建、查不到"的窗口——此时判不存在等于放上游重下。
 */
@Slf4j
@Service("didaOrderQuerySyncService")
public class DidaOrderQuerySyncServiceImpl extends AbstractOrderQuerySyncSupportService<DidaBookingSearchResponse> {

    @Resource
    private DidaProperties properties;

    @Override
    public DidaBookingSearchResponse doOrderQuery(OrderQueryReq req) {
        if (!properties.isConfigured()) {
            log.error("道旅查单：凭证未配置,orderId={}", req.getOrderId());
            return null; // 模板兜底为「结果不确定」
        }
        String bookingId = StringUtils.trimToNull(req.getSupplierOrderId());
        ResponseResult<DidaBookingSearchResponse> result = new BookingSearchAccess(properties).access(
                bookingId != null ? BookingSearchAccess.byBookingId(properties, bookingId)
                        : BookingSearchAccess.byClientReference(properties, req.getOrderId()),
                CallPurpose.ORDER);
        return result == null ? null : result.getData();
    }

    @Override
    public OrderRespDTO orderQueryRespConvert(DidaBookingSearchResponse resp) {
        if (!resp.isSucc()) {
            String code = StringUtils.trimToEmpty(resp.errorCode());
            if (DidaBookingClassifier.isAuthConfig(code)) {
                Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.DIDA));
                log.error("[auth-config] 道旅查单：我方凭据/配置病，需人工处理。code={},message={}", code, resp.errorMessage());
            } else {
                log.warn("道旅查单：业务错误按不确定处理,code={},message={}", code, resp.errorMessage());
            }
            return OrderRespDTO.builder().presence(OrderPresence.INDETERMINATE)
                    .message("查单未取得确定结果(" + code + "：" + resp.errorMessage() + ")").build();
        }
        int hits = resp.bookings().size();
        if (hits != 1) {
            // 空列表不是"不存在"的证据（类注释）；多笔则坐标有歧义，不许挑一笔当答案
            log.warn("道旅查单：命中数不为 1，按不确定处理,hits={}", hits);
            return OrderRespDTO.builder().presence(OrderPresence.INDETERMINATE)
                    .message(hits == 0 ? "供应商未返回该订单（空返回不能视为不存在，可能仍在建单），请稍后重试查单"
                            : "供应商返回了 " + hits + " 笔订单，坐标有歧义，请携带供应商单号重查").build();
        }
        DidaBookingDetails d = resp.bookings().get(0);
        Integer orderStatus = DidaBookingClassifier.toOrderStatus(d.getStatus());
        if (orderStatus == null) {
            // §6.2.1：映射不上不是常态，必须有落点；状态原文随响应透出
            log.warn("道旅查单：状态原文无法映射,sOrderId={},status={}", d.getBookingId(), d.getStatus());
        }
        String currency = d.currency();
        if (d.getTotalPrice() != null && StringUtils.isBlank(currency)) {
            // 金额不许无币种流转（Money）：币种缺席就不报金额
            log.warn("道旅查单：总价无币种，不报金额,sOrderId={},totalPrice={}", d.getBookingId(), d.getTotalPrice());
        }
        boolean priceReportable = d.getTotalPrice() != null && StringUtils.isNotBlank(currency);
        return OrderRespDTO.builder()
                .presence(OrderPresence.FOUND)
                .supplierOrderId(d.getBookingId())
                .orderStatus(orderStatus)
                .supplierOrderStatus(d.getStatus() == null ? null : String.valueOf(d.getStatus()))
                .confirmationNumber(StringUtils.trimToNull(d.getConfirmationCode()))
                .totalPrice(priceReportable ? Money.toCents(d.getTotalPrice()) : null)
                .totalPriceCurrency(priceReportable ? currency.trim().toUpperCase() : null)
                .createTime(d.getOrderDate())
                .build();
    }
}
