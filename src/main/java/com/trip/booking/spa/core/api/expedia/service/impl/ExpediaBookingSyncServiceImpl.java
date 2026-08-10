package com.trip.booking.spa.core.api.expedia.service.impl;

import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.BookingOutcome;
import com.trip.booking.spa.core.api.dto.BookingRespDTO;
import com.trip.booking.spa.core.api.expedia.access.CreateOrderAccess;
import com.trip.booking.spa.core.api.expedia.access.QueryOrderAccess;
import com.trip.booking.spa.core.api.expedia.bean.request.CreateOrderRequest;
import com.trip.booking.spa.core.api.expedia.bean.response.CreateOrderResponse;
import com.trip.booking.spa.core.api.expedia.bean.response.QueryOrderResponse;
import com.trip.booking.spa.core.api.expedia.config.ExpediaBookingContact;
import com.trip.booking.spa.core.api.expedia.service.ExpediaBookingClassifier;
import com.trip.booking.spa.core.api.expedia.service.ExpediaBookingClassifier.Classification;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaUtils;
import com.trip.booking.spa.core.api.request.BookingReq;
import com.trip.booking.spa.core.api.service.AbstractBookingSyncSupportService;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Expedia 下单编排。
 *
 * <p>bean 名 {@code expediaBookingSyncService} 供 {@code SpaController} 按供应商路由发现。
 *
 * <p><b>本类的核心职责是把供应商侧的各种含糊结果收敛为确定的三态</b>，
 * 让上游拿到的结论尽可能确定。两处自动收敛：
 * <ul>
 *   <li>{@code duplicate_itinerary}（HTTP 400 但含义为「首次已成功」）→ 反查取回既有订单号 → SUCCESS</li>
 *   <li>下单成功后再反查一次以补齐酒店确认号——该字段只在反查响应里，下单响应中没有</li>
 * </ul>
 * 反查失败不影响已确定的结论：订单已成立就是 SUCCESS，仅确认号缺失。
 */
@Slf4j
@Service("expediaBookingSyncService")
public class ExpediaBookingSyncServiceImpl
        extends AbstractBookingSyncSupportService<ExpediaBookingSyncServiceImpl.BookingOutcomeHolder> {

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
    public BookingOutcomeHolder doBooking(BookingReq req) {
        // 令牌缺失属调用方问题，重试不会改变，直接判确定失败——不必打扰 Expedia
        if (StringUtils.isBlank(req.getPrebookToken())) {
            return BookingOutcomeHolder.failed(req.getOrderId(), "missing_prebook_token",
                    "缺少 prebookToken，请先验价并回传该令牌");
        }
        try {
            bookingContact.requireUsable();
        } catch (IllegalStateException e) {
            return BookingOutcomeHolder.failed(req.getOrderId(), "booking_contact_unusable", e.getMessage());
        }

        ExpediaBookingContact.Contact contact = bookingContact.getContact();
        String body = JsonUtils.writeObject2Json(buildRequest(req, contact));

        ResponseResult<CreateOrderResponse> result = new CreateOrderAccess(
                host, req.getPrebookToken(), "zh-CN", expediaUtils.signGeneration(),
                ownIp, sessionId, rateLimiter).access(body);

        int httpStatus = result == null ? 0 : result.getHttpStatus();
        CreateOrderResponse data = result == null ? null : result.getData();
        String raw = result == null ? null : result.getOrigData();

        Classification classification = ExpediaBookingClassifier.classify(httpStatus, data, raw);
        log.info("expedia booking 分类结果 orderId={}, httpStatus={}, classification={}",
                req.getOrderId(), httpStatus, classification);

        switch (classification) {
            case SUCCESS:
                return succeeded(req, contact, data.getItinerary_id());
            case DUPLICATE:
                // A 方案：网关内部消化，上游无感。反查取回首次已成立的订单号
                return resolveDuplicate(req, contact);
            case DETERMINISTIC_FAILURE:
                return BookingOutcomeHolder.failed(req.getOrderId(),
                        data == null ? String.valueOf(httpStatus) : data.getType(),
                        data == null ? "供应商拒单" : data.getMessage());
            case INDETERMINATE:
            default:
                return BookingOutcomeHolder.unknown(req.getOrderId(),
                        data == null ? String.valueOf(httpStatus) : data.getType(),
                        "下单结果不确定，请稍后凭订单号反查确证");
        }
    }

    /** 下单成功后补齐确认号；反查失败不改变已确定的成功结论 */
    private BookingOutcomeHolder succeeded(BookingReq req, ExpediaBookingContact.Contact contact, String itineraryId) {
        BookingOutcomeHolder holder = BookingOutcomeHolder.success(req.getOrderId(), itineraryId);
        QueryOrderResponse.Itinerary itinerary = queryQuietly(req, contact);
        if (itinerary != null) {
            holder.confirmationNumber = firstConfirmationNumber(itinerary);
        }
        return holder;
    }

    /**
     * 处理重复下单：Expedia 侧已有同一业务单号的订单。
     *
     * <p>反查到即判成功；反查不到或查单失败则退为不确定——<b>不可判失败</b>，
     * 因为 Expedia 已明确告知「该单号的订单已存在」。
     */
    private BookingOutcomeHolder resolveDuplicate(BookingReq req, ExpediaBookingContact.Contact contact) {
        QueryOrderResponse.Itinerary itinerary = queryQuietly(req, contact);
        if (itinerary != null && StringUtils.isNotBlank(itinerary.getItinerary_id())) {
            log.info("expedia booking 重复下单已收敛为成功 orderId={}, itineraryId={}",
                    req.getOrderId(), itinerary.getItinerary_id());
            BookingOutcomeHolder holder = BookingOutcomeHolder.success(
                    req.getOrderId(), itinerary.getItinerary_id());
            holder.confirmationNumber = firstConfirmationNumber(itinerary);
            return holder;
        }
        log.warn("expedia booking 报重复下单但反查未取回订单，退为不确定 orderId={}", req.getOrderId());
        return BookingOutcomeHolder.unknown(req.getOrderId(), ExpediaBookingClassifier.DUPLICATE_ITINERARY,
                "供应商报订单已存在，但反查未能取回，请稍后重试反查");
    }

    /** 反查一次，任何异常都只记录不外抛——调用方的结论不应因补充信息失败而改变 */
    private QueryOrderResponse.Itinerary queryQuietly(BookingReq req, ExpediaBookingContact.Contact contact) {
        try {
            ResponseResult<QueryOrderResponse> qr = new QueryOrderAccess(
                    host, req.getOrderId(), contact.getEmail(), expediaUtils.signGeneration(),
                    ownIp, sessionId, rateLimiter).access("");
            QueryOrderResponse qd = qr == null ? null : qr.getData();
            if (qd == null || qd.getPresence() != QueryOrderResponse.Presence.FOUND) {
                log.info("expedia booking 反查未取回订单 orderId={}, presence={}",
                        req.getOrderId(), qd == null ? null : qd.getPresence());
                return null;
            }
            return qd.firstItinerary();
        } catch (Exception e) {
            log.error("expedia booking 反查异常 orderId={}", req.getOrderId(), e);
            return null;
        }
    }

    private String firstConfirmationNumber(QueryOrderResponse.Itinerary itinerary) {
        if (itinerary.getRooms() == null || itinerary.getRooms().isEmpty()) {
            return null;
        }
        CreateOrderResponse.ConfirmationId id = itinerary.getRooms().get(0).getConfirmation_id();
        return id == null ? null : id.getExpedia();
    }

    private CreateOrderRequest buildRequest(BookingReq req, ExpediaBookingContact.Contact contact) {
        List<CreateOrderRequest.Room> rooms = new ArrayList<>();
        // 一间房一个条目。姓名与联系方式一律用我方固定值，不提交旅客真实信息——
        // 已与 Expedia 商定，理由与边界见 ExpediaBookingContact 类注释
        for (int i = 0; i < req.getRoomNum(); i++) {
            rooms.add(CreateOrderRequest.Room.builder()
                    .given_name(contact.getGivenName())
                    .family_name(contact.getFamilyName())
                    .build());
        }
        return CreateOrderRequest.builder()
                .affiliate_reference_id(req.getOrderId())
                .hold(false)
                .email(contact.getEmail())
                .phone(CreateOrderRequest.Phone.builder()
                        .country_code(contact.getPhoneCountryCode())
                        .number(contact.getPhoneNumber())
                        .build())
                .rooms(rooms)
                .payments(List.of(CreateOrderRequest.Payment.builder()
                        .type("affiliate_collect")
                        .billing_contact(CreateOrderRequest.BillingContact.builder()
                                .given_name(contact.getGivenName())
                                .family_name(contact.getFamilyName())
                                .address(CreateOrderRequest.Address.builder()
                                        .line_1(contact.getAddressLine1())
                                        .city(contact.getCity())
                                        .state_province_code(contact.getStateProvinceCode())
                                        .postal_code(contact.getPostalCode())
                                        .country_code(contact.getCountryCode())
                                        .build())
                                .build())
                        .build()))
                .build();
    }


    @Override
    public BookingRespDTO bookingRespConvert(BookingOutcomeHolder holder) {
        return BookingRespDTO.builder()
                .outcome(holder.outcome)
                .orderId(holder.orderId)
                .sOrderId(holder.itineraryId)
                .sConfirmationNumber(holder.confirmationNumber)
                .supplierErrorCode(holder.errorCode)
                .supplierErrorMessage(holder.errorMessage)
                .orderDesc(holder.errorMessage)
                .build();
    }

    /** 编排结果的中间载体，仅本类使用 */
    public static class BookingOutcomeHolder {
        BookingOutcome outcome;
        String orderId;
        String itineraryId;
        String confirmationNumber;
        String errorCode;
        String errorMessage;

        static BookingOutcomeHolder success(String orderId, String itineraryId) {
            BookingOutcomeHolder h = new BookingOutcomeHolder();
            h.outcome = BookingOutcome.SUCCESS;
            h.orderId = orderId;
            h.itineraryId = itineraryId;
            return h;
        }

        static BookingOutcomeHolder failed(String orderId, String code, String message) {
            BookingOutcomeHolder h = new BookingOutcomeHolder();
            h.outcome = BookingOutcome.FAILED;
            h.orderId = orderId;
            h.errorCode = code;
            h.errorMessage = message;
            return h;
        }

        static BookingOutcomeHolder unknown(String orderId, String code, String message) {
            BookingOutcomeHolder h = new BookingOutcomeHolder();
            h.outcome = BookingOutcome.UNKNOWN;
            h.orderId = orderId;
            h.errorCode = code;
            h.errorMessage = message;
            return h;
        }
    }
}
