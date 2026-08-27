package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.booking;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.booking.client.CreateOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyCreateResponse;
import com.trip.booking.spa.gateway.application.booking.AbstractBookingSyncSupportService;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞猪下单：句柄取回双钥与验价总价，我方单号进 {@code out_order_id}、
 * 飞猪单号从 {@code tid} 回收（B5）。拿到 tid 才 SUCCESS（随即核销）；
 * 平台层拒绝=请求未达业务的确定失败（凭据病走 AUTH_CONFIG）；
 * 业务层失败无法证明未生效，一律 UNKNOWN 引导反查。
 */
@Slf4j
@Service("fliggyBookingSyncService")
public class FliggyBookingSyncServiceImpl extends AbstractBookingSyncSupportService<BookingRespDTO> {

    private static final String METHOD_CREATE = "taobao.xhotel.order.international.distribution.create";

    @Resource
    private FliggyProperties properties;
    @Resource
    private OfferStore offerStore;

    @Override
    public BookingRespDTO doBooking(BookingReq req) {
        if (!properties.isConfigured()) {
            return failed(req.getOrderId(), "credentials_missing", "飞猪凭证未配置，供应商侧未发生任何动作");
        }
        if (StringUtils.isBlank(req.getOfferId())) {
            return failed(req.getOrderId(), "missing_offer_id", "缺少 offerId，请先验价并回传该报价句柄");
        }
        Offer offer = offerStore.resolve(req.getOfferId());
        if (offer == null) {
            return failed(req.getOrderId(), "offer_unresolvable", "报价已过期或不存在，请重新验价后下单");
        }
        if (!Integer.valueOf(SupplierSourceEnum.FLIGGY.getCode()).equals(offer.getSupplierId())) {
            return failed(req.getOrderId(), "offer_supplier_mismatch", "报价句柄不属于飞猪，请核对供应商");
        }
        for (String key : FliggyOfferCredentials.REQUIRED_FOR_BOOKING) {
            if (StringUtils.isBlank(offer.credential(key))) {
                return failed(req.getOrderId(), "offer_credential_missing",
                        "报价句柄内容不完整（缺 " + key + "），请重新验价后下单");
            }
        }
        if (StringUtils.isAllBlank(req.getPersonName(), req.getContactName())
                || StringUtils.isBlank(req.getContactPhone())) {
            // 参数缺失是确定性失败,不许滑进 UNKNOWN 让上游白跑一次反查
            return failed(req.getOrderId(), "missing_guest_or_contact",
                    "缺少入住人姓名或联系人电话，供应商侧未发生任何动作");
        }

        ResponseResult<FliggyCreateResponse> result = new CreateOrderAccess(properties)
                .access(createCall(req, offer), CallPurpose.ORDER);
        FliggyCreateResponse resp = result == null ? null : result.getData();
        if (resp == null) {
            // 没拿到回应≠没成单：超时的下单可能已在供应商侧生效
            return unknown(req.getOrderId(), null, "下单未取得结果，请凭我方单号反查确证");
        }
        if (resp.isPlatformError()) {
            if (resp.isCredentialFailure()) {
                Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.FLIGGY));
                log.error("[auth-config] 飞猪下单：我方凭据/配置病，供应商无辜、重试无效、需人工处理。"
                        + "platformError={},orderId={}", resp.platformError(), req.getOrderId());
            }
            // 平台网关拒绝=请求未达业务，供应商侧未发生——确定性失败，修正后可重试
            return failed(req.getOrderId(), resp.metricErrorCode(), "供应商平台拒绝了请求：" + resp.platformError());
        }
        if (resp.isSucc()) {
            offerStore.consume(req.getOfferId());
            return BookingRespDTO.builder()
                    .outcome(BookingOutcome.SUCCESS)
                    .orderId(req.getOrderId())
                    .sOrderId(resp.fliggyOrderId())
                    .orderDesc("下单成功")
                    .build();
        }
        // 业务层失败：官方码表空白，无法证明请求未在供应商侧生效——UNKNOWN，绝不擅判失败
        log.warn("飞猪下单：业务层未通过,orderId={},bizErrorCode={}", req.getOrderId(), resp.bizErrorCode());
        return unknown(req.getOrderId(), resp.metricErrorCode(), "供应商未确认下单结果，请凭我方单号反查确证");
    }

    @Override
    public BookingRespDTO bookingRespConvert(BookingRespDTO dto) {
        return dto;
    }

    private FliggyTopCall createCall(BookingReq req, Offer offer) {
        int rooms = req.getRoomNum() == null ? 1 : req.getRoomNum();
        // customers：按间号分组（cursor 生产同构）。契约只给一位入住人姓名，各间同名占位
        Map<String, Object> customers = new LinkedHashMap<>();
        for (int i = 1; i <= rooms; i++) {
            customers.put(String.valueOf(i), List.of(Map.of(
                    "name", StringUtils.defaultIfBlank(req.getPersonName(), req.getContactName()),
                    "customer_type", "1")));
        }
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("hotel_contact", Map.of(
                "name", StringUtils.defaultIfBlank(req.getContactName(), req.getPersonName()),
                "phone", StringUtils.defaultString(req.getContactPhone())));
        createReq.put("number_of_rooms", rooms);
        createReq.put("customers", customers);
        createReq.put("create_key", offer.credential(FliggyOfferCredentials.CREATE_KEY));
        createReq.put("rate_key", offer.credential(FliggyOfferCredentials.RATE_KEY));
        createReq.put("total_room_price",
                Integer.parseInt(offer.credential(FliggyOfferCredentials.TOTAL_ROOM_PRICE_CENTS)));
        createReq.put("out_order_id", req.getOrderId());
        createReq.put("distributor", properties.getDistributor());
        createReq.put("check_in", req.getCheckIn());
        createReq.put("check_out", req.getCheckOut());
        // 到店时间窗与 cursor 生产同款；官方示例带时刻,纯日期形态待首单实测校正
        createReq.put("hotel_arrival_time", Map.of(
                "earliest_arrival_time", req.getCheckIn() + " 14:00:00",
                "latest_arrival_time", req.getCheckIn() + " 18:00:00"));
        return new FliggyTopCall(METHOD_CREATE, Map.of("create_req", JsonUtils.writeObject2Json(createReq)));
    }

    private static BookingRespDTO failed(String orderId, String code, String message) {
        return BookingRespDTO.builder().outcome(BookingOutcome.FAILED).orderId(orderId)
                .supplierErrorCode(code).supplierErrorMessage(message).orderDesc(message).build();
    }

    private static BookingRespDTO unknown(String orderId, String code, String message) {
        return BookingRespDTO.builder().outcome(BookingOutcome.UNKNOWN).orderId(orderId)
                .supplierErrorCode(code).supplierErrorMessage(message).orderDesc(message).build();
    }
}
