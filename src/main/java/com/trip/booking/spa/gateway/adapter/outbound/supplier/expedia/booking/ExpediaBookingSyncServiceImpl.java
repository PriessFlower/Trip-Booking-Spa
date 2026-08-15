package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.booking;

import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.booking.client.CreateOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.request.CreateOrderRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CreateOrderResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryOrderResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaBookingContact;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.booking.ExpediaBookingClassifier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.booking.ExpediaBookingClassifier.Classification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.application.booking.AbstractBookingSyncSupportService;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.util.JsonUtils;
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
    @Resource
    private OfferStore offerStore;

    @Override
    public BookingOutcomeHolder doBooking(BookingReq req) {
        BookingOutcomeHolder holder = bookInternal(req);
        // 一次性票据语义：确定成功即核销句柄，重复下单在网关内就得到确定性失败，
        // 不再把防线寄放在供应商幂等上。FAILED/UNKNOWN 不核销——理由见 OfferStore#consume
        if (holder != null && holder.outcome == BookingOutcome.SUCCESS) {
            offerStore.consume(req.getOfferId());
        }
        return holder;
    }

    private BookingOutcomeHolder bookInternal(BookingReq req) {
        // 以下三种判定都在向 Expedia 发出任何请求之前完成，供应商侧不会发生任何事，
        // 故一律判确定失败而非「结果不确定」——上游可以放心地不去查单
        if (StringUtils.isBlank(req.getOfferId())) {
            return BookingOutcomeHolder.failed(req.getOrderId(), "missing_offer_id",
                    "缺少 offerId，请先验价并回传该报价句柄");
        }
        Offer offer = offerStore.resolve(req.getOfferId());
        if (offer == null) {
            return BookingOutcomeHolder.failed(req.getOrderId(), "offer_unresolvable",
                    "报价已过期或不存在，请重新验价后下单");
        }
        if (!Integer.valueOf(SupplierSourceEnum.EXPEDIA.getCode()).equals(offer.getSupplierId())) {
            // 拿 A 家的报价来 B 家下单，属调用方串号
            log.error("expedia booking 报价句柄归属供应商不符 orderId={}, offerSupplierId={}",
                    req.getOrderId(), offer.getSupplierId());
            return BookingOutcomeHolder.failed(req.getOrderId(), "offer_supplier_mismatch",
                    "该报价句柄不属于本供应商，请核对下单请求的供应商");
        }
        String bookHref = offer.credential(ExpediaOfferCredentials.BOOK_HREF);
        if (StringUtils.isBlank(bookHref)) {
            log.error("expedia booking 报价句柄缺少下单链接 orderId={}, offerId={}",
                    req.getOrderId(), req.getOfferId());
            return BookingOutcomeHolder.failed(req.getOrderId(), "offer_credential_missing",
                    "报价句柄内容不完整，请重新验价后下单");
        }
        try {
            bookingContact.requireUsable();
        } catch (IllegalStateException e) {
            return BookingOutcomeHolder.failed(req.getOrderId(), "booking_contact_unusable", e.getMessage());
        }

        ExpediaBookingContact.Contact contact = bookingContact.getContact();
        String body = JsonUtils.writeObject2Json(buildRequest(req, contact));

        ResponseResult<CreateOrderResponse> result = new CreateOrderAccess(
                host, bookHref, "zh-CN", expediaUtils.signGeneration(),
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
