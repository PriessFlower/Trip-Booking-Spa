package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.booking;

import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.booking.client.CreateOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyCreateResponse;
import com.trip.booking.spa.gateway.application.booking.AbstractBookingSyncSupportService;
import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.domain.booking.BookingResult;
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
public class FliggyBookingSyncServiceImpl extends AbstractBookingSyncSupportService {

    private static final String METHOD_CREATE = "taobao.xhotel.order.international.distribution.create";

    @Resource
    private FliggyProperties properties;
    @Resource
    private OfferStore offerStore;

    @Override
    protected String bookingGateKey() {
        return "supplier.fliggy.booking-enabled";
    }

    @Override
    protected boolean bookingAllowed() {
        return properties.isBookingEnabled();
    }

    @Override
    protected BookingResult doBooking(BookingCommand command) {
        if (!properties.isConfigured()) {
            return BookingResult.failed(command.orderId(), "credentials_missing", "飞猪凭证未配置，供应商侧未发生任何动作", "飞猪凭证未配置，供应商侧未发生任何动作");
        }
        if (StringUtils.isBlank(command.offerId())) {
            return BookingResult.failed(command.orderId(), "missing_offer_id", "缺少 offerId，请先验价并回传该报价句柄", "缺少 offerId，请先验价并回传该报价句柄");
        }
        Offer offer = offerStore.resolve(command.offerId());
        if (offer == null) {
            return BookingResult.failed(command.orderId(), "offer_unresolvable", "报价已过期或不存在，请重新验价后下单", "报价已过期或不存在，请重新验价后下单");
        }
        if (!Integer.valueOf(SupplierSourceEnum.FLIGGY.getCode()).equals(offer.getSupplierId())) {
            return BookingResult.failed(command.orderId(), "offer_supplier_mismatch", "报价句柄不属于飞猪，请核对供应商", "报价句柄不属于飞猪，请核对供应商");
        }
        for (String key : FliggyOfferCredentials.REQUIRED_FOR_BOOKING) {
            if (StringUtils.isBlank(offer.credential(key))) {
                String msg = "报价句柄内容不完整（缺 " + key + "），请重新验价后下单";
                return BookingResult.failed(command.orderId(), "offer_credential_missing", msg, msg);
            }
        }
        if (StringUtils.isAllBlank(command.personName(), command.contactName())
                || StringUtils.isBlank(command.contactPhone())) {
            // 参数缺失是确定性失败,不许滑进 UNKNOWN 让上游白跑一次反查
            return BookingResult.failed(command.orderId(), "missing_guest_or_contact",
                    "缺少入住人姓名或联系人电话，供应商侧未发生任何动作",
                    "缺少入住人姓名或联系人电话，供应商侧未发生任何动作");
        }

        ResponseResult<FliggyCreateResponse> result = new CreateOrderAccess(properties)
                .access(createCall(command, offer), CallPurpose.ORDER);
        FliggyCreateResponse resp = result == null ? null : result.getData();
        if (resp == null) {
            // 没拿到回应≠没成单：超时的下单可能已在供应商侧生效
            return BookingResult.unknown(command.orderId(), null, "下单未取得结果，请凭我方单号反查确证", "下单未取得结果，请凭我方单号反查确证");
        }
        if (resp.isPlatformError()) {
            if (resp.isCredentialFailure()) {
                Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.FLIGGY));
                log.error("[auth-config] 飞猪下单：我方凭据/配置病，供应商无辜、重试无效、需人工处理。"
                        + "platformError={},orderId={}", resp.platformError(), command.orderId());
            }
            // 平台网关拒绝=请求未达业务，供应商侧未发生——确定性失败，修正后可重试
            return BookingResult.failed(command.orderId(), resp.metricErrorCode(), "供应商平台拒绝了请求：" + resp.platformError(), "供应商平台拒绝了请求：" + resp.platformError());
        }
        if (resp.isSucc()) {
            offerStore.consume(command.offerId());
            return BookingResult.success(command.orderId(), resp.fliggyOrderId(), null, "下单成功");
        }
        // 业务层失败：官方码表空白，无法证明请求未在供应商侧生效——UNKNOWN，绝不擅判失败
        log.warn("飞猪下单：业务层未通过,orderId={},bizErrorCode={}", command.orderId(), resp.bizErrorCode());
        return BookingResult.unknown(command.orderId(), resp.metricErrorCode(), "供应商未确认下单结果，请凭我方单号反查确证", "供应商未确认下单结果，请凭我方单号反查确证");
    }

    private FliggyTopCall createCall(BookingCommand command, Offer offer) {
        int rooms = command.roomNum() == null ? 1 : command.roomNum();
        // customers：按间号分组（cursor 生产同构）。契约只给一位入住人姓名，各间同名占位
        Map<String, Object> customers = new LinkedHashMap<>();
        for (int i = 1; i <= rooms; i++) {
            customers.put(String.valueOf(i), List.of(Map.of(
                    "name", StringUtils.defaultIfBlank(command.personName(), command.contactName()),
                    "customer_type", "1")));
        }
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("hotel_contact", Map.of(
                "name", StringUtils.defaultIfBlank(command.contactName(), command.personName()),
                "phone", StringUtils.defaultString(command.contactPhone())));
        createReq.put("number_of_rooms", rooms);
        createReq.put("customers", customers);
        createReq.put("create_key", offer.credential(FliggyOfferCredentials.CREATE_KEY));
        createReq.put("rate_key", offer.credential(FliggyOfferCredentials.RATE_KEY));
        createReq.put("total_room_price",
                Integer.parseInt(offer.credential(FliggyOfferCredentials.TOTAL_ROOM_PRICE_CENTS)));
        createReq.put("out_order_id", command.orderId());
        createReq.put("distributor", properties.getDistributor());
        createReq.put("check_in", command.checkIn());
        createReq.put("check_out", command.checkOut());
        // 到店时间窗与 cursor 生产同款；官方示例带时刻,纯日期形态待首单实测校正
        createReq.put("hotel_arrival_time", Map.of(
                "earliest_arrival_time", command.checkIn() + " 14:00:00",
                "latest_arrival_time", command.checkIn() + " 18:00:00"));
        return new FliggyTopCall(METHOD_CREATE, Map.of("create_req", JsonUtils.writeObject2Json(createReq)));
    }

}
