package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking.ElongBookingClassifier.Classification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking.client.CreateOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongRestCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongOrderCreateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongOrderDetailRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongRequestEnvelope;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCreateResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderDetailResponse;
import com.trip.booking.spa.gateway.application.booking.AbstractBookingSyncSupportService;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 艺龙下单编排。bean 名 {@code elongBookingSyncService} 供能力注册表发现。
 *
 * <p>凭据全部取自验价句柄（{@link ElongOfferCredentials}，七项会话凭证 + 验后价 + 住期），
 * <b>不重取不缓存</b>：句柄 TTL（10 分钟）短于马甲官方有效期（30 分钟），句柄活着凭证
 * 必活——cursor 头号病灶（下单复用验价缓存里的死马甲，45/47 全灭）在结构上不成立。
 *
 * <p>幂等与幽灵单防线：AffiliateConfirmationId=我方订单号，供应商侧同值不建新单、
 * 返回既有单号（官方文档）；疑似重复（H001043/H001045）转按我方单号反查后再定；
 * 一切不确定形态回报 UNKNOWN 交上游查单确证——绝不把不确定说成失败。
 */
@Slf4j
@Service("elongBookingSyncService")
public class ElongBookingSyncServiceImpl
        extends AbstractBookingSyncSupportService<ElongBookingSyncServiceImpl.BookingOutcomeHolder> {

    private static final String PAYMENT_TYPE_PREPAY = "Prepay";

    private static final String CONFIRMATION_TYPE_NO_NEED = "NoNeed";

    private static final String CURRENCY_RMB = "RMB";

    /** 入住日拼最早/最晚到店时间；最晚固定 23:59:59（移植风险⑩，勿接上游到店时间） */
    private static final String EARLIEST_ARRIVAL_HMS = " 14:00:00";
    private static final String LATEST_ARRIVAL_HMS = " 23:59:59";

    @Resource
    private ElongProperties properties;

    @Resource
    private OfferStore offerStore;

    @Override
    public BookingOutcomeHolder doBooking(BookingReq req) {
        BookingOutcomeHolder holder = bookInternal(req);
        // 一次性票据：确定成功即核销句柄（PR #53 纪律）。FAILED/UNKNOWN 不核销——
        // FAILED 允许上游修正后用同一报价重试，UNKNOWN 的对账反查可能仍需它
        if (holder != null && holder.outcome == BookingOutcome.SUCCESS) {
            offerStore.consume(req.getOfferId());
        }
        return holder;
    }

    private BookingOutcomeHolder bookInternal(BookingReq req) {
        // 以下判定全部在向艺龙发出任何请求之前完成，供应商侧不会发生任何事，
        // 一律确定失败而非"结果不确定"——上游可以放心地不去查单
        if (!properties.isBookingEnabled()) {
            // §3.8.4：闸口拦截必须可检索
            log.info("闸口 elong.booking-enabled 关闭，拒绝下单,orderId={},sHotelId={}",
                    req.getOrderId(), req.getSHotelId());
            return BookingOutcomeHolder.failed(req.getOrderId(), "booking_disabled",
                    "艺龙下单未开通（安全护栏关闭），供应商侧未发生任何动作");
        }
        if (!properties.isConfigured()) {
            log.error("艺龙下单：凭证未配置，无法下单,orderId={}", req.getOrderId());
            return BookingOutcomeHolder.failed(req.getOrderId(), "credentials_missing",
                    "艺龙凭证未配置，供应商侧未发生任何动作");
        }
        if (StringUtils.isBlank(req.getOfferId())) {
            return BookingOutcomeHolder.failed(req.getOrderId(), "missing_offer_id",
                    "缺少 offerId，请先验价并回传该报价句柄");
        }
        Offer offer = offerStore.resolve(req.getOfferId());
        if (offer == null) {
            return BookingOutcomeHolder.failed(req.getOrderId(), "offer_unresolvable",
                    "报价已过期或不存在，请重新验价后下单");
        }
        if (!Integer.valueOf(SupplierSourceEnum.ELONG.getCode()).equals(offer.getSupplierId())) {
            log.error("艺龙下单：报价句柄归属供应商不符,orderId={},offerSupplierId={}",
                    req.getOrderId(), offer.getSupplierId());
            return BookingOutcomeHolder.failed(req.getOrderId(), "offer_supplier_mismatch",
                    "该报价句柄不属于本供应商，请核对下单请求的供应商");
        }
        // 七项会话凭证 + 验后价 + 住期缺一不可（cursor 侧任一缺失即拒单）
        for (String key : List.of(ElongOfferCredentials.HOTEL_ID, ElongOfferCredentials.HOTEL_CODE,
                ElongOfferCredentials.ROOM_TYPE_ID, ElongOfferCredentials.RATE_PLAN_ID,
                ElongOfferCredentials.GOODS_UNIQ_ID, ElongOfferCredentials.LITTLE_MAJIA_ID,
                ElongOfferCredentials.SUPPLIER_ID, ElongOfferCredentials.SUB_SUPPLIER_ID,
                ElongOfferCredentials.SHOPPER_PRODUCT_ID, ElongOfferCredentials.TOTAL_PRICE,
                ElongOfferCredentials.CHECK_IN, ElongOfferCredentials.CHECK_OUT)) {
            if (StringUtils.isBlank(offer.credential(key))) {
                log.error("艺龙下单：报价句柄缺少凭据,orderId={},missingKey={}", req.getOrderId(), key);
                return BookingOutcomeHolder.failed(req.getOrderId(), "offer_credential_missing",
                        "报价句柄内容不完整（缺 " + key + "），请重新验价后下单");
            }
        }
        // 住期以验价句柄为准；上游传参不一致=调用方串单，价必对不上，拒于本地
        if (!offer.credential(ElongOfferCredentials.CHECK_IN).equals(req.getCheckIn())
                || !offer.credential(ElongOfferCredentials.CHECK_OUT).equals(req.getCheckOut())) {
            log.error("艺龙下单：住期与验价不符,orderId={},req={}~{},offer={}~{}",
                    req.getOrderId(), req.getCheckIn(), req.getCheckOut(),
                    offer.credential(ElongOfferCredentials.CHECK_IN), offer.credential(ElongOfferCredentials.CHECK_OUT));
            return BookingOutcomeHolder.failed(req.getOrderId(), "stay_mismatch",
                    "下单住期与验价时不一致，请重新验价后下单");
        }

        ElongOrderCreateRequest createRequest = buildRequest(req, offer);
        String dataJson = JsonUtils.writeObject2Json(
                new ElongRequestEnvelope(properties.getVersion(), createRequest));
        ResponseResult<ElongOrderCreateResponse> result = new CreateOrderAccess(properties)
                .access(new ElongRestCall("hotel.order.create", dataJson));

        ElongOrderCreateResponse data = result == null ? null : result.getData();
        Classification classification = ElongBookingClassifier.classifyCreate(data);
        log.info("艺龙下单：分类结果,orderId={},classification={},errorCode={}",
                req.getOrderId(), classification, data == null ? null : data.getCode());

        switch (classification) {
            case SUCCESS:
                log.info("艺龙下单：成单,orderId={},sOrderId={},cancelTime={},instantConfirm={}",
                        req.getOrderId(), data.orderId(), data.getResult().getCancelTime(),
                        data.getResult().getIsInstantConfirm());
                return BookingOutcomeHolder.success(req.getOrderId(), String.valueOf(data.orderId()));
            case DUPLICATE_SUSPECT:
                return resolveDuplicateSuspect(req, data);
            case DETERMINISTIC_FAILURE:
                return BookingOutcomeHolder.failed(req.getOrderId(), data.errorCode(), data.getCode());
            case INDETERMINATE:
            default:
                return BookingOutcomeHolder.unknown(req.getOrderId(),
                        data == null ? null : data.errorCode(),
                        "下单结果不确定，请稍后凭我方订单号反查确证");
        }
    }

    /**
     * 疑似重复（H001043 过快提交 / H001045 疑似重单）：首单可能已成立，按我方单号反查。
     * 反查到 → 收敛为成功；确证无单 → H001045 是风控拒单可判失败，H001043 首发可能仍在
     * 处理中，只能不确定；反查本身不确定 → 不确定。
     */
    private BookingOutcomeHolder resolveDuplicateSuspect(BookingReq req, ElongOrderCreateResponse data) {
        ElongOrderDetailResponse detail = queryQuietly(req.getOrderId());
        if (detail != null && detail.isSucc() && detail.getResult() != null
                && detail.getResult().getOrderId() != null) {
            log.info("艺龙下单：疑似重复经反查收敛为成功,orderId={},sOrderId={}",
                    req.getOrderId(), detail.getResult().getOrderId());
            return BookingOutcomeHolder.success(req.getOrderId(),
                    String.valueOf(detail.getResult().getOrderId()));
        }
        String errorCode = data.errorCode();
        boolean confirmedAbsent = detail != null && !detail.isSucc()
                && StringUtils.trimToEmpty(detail.errorCode()).startsWith("H001054");
        if (confirmedAbsent && errorCode.startsWith("H001045")) {
            // 确证我方单号名下无单：与他单撞了风控（入住日期+手机号+姓名重复），确定拒单
            log.info("艺龙下单：疑似重单且确证无我方单，判确定失败,orderId={},errorCode={}",
                    req.getOrderId(), errorCode);
            return BookingOutcomeHolder.failed(req.getOrderId(), errorCode, data.getCode());
        }
        log.warn("艺龙下单：疑似重复但反查未能确证,orderId={},errorCode={},反查确证无单={}",
                req.getOrderId(), errorCode, confirmedAbsent);
        return BookingOutcomeHolder.unknown(req.getOrderId(), errorCode,
                "供应商报重复/过快提交，反查未能确证，请稍后凭我方订单号反查");
    }

    /** 反查一次，任何异常只记录不外抛——调用方的结论不应因补充信息失败而改变 */
    private ElongOrderDetailResponse queryQuietly(String orderId) {
        try {
            // 按我方单号反查时 OrderId 必须显式传 0（cursor 生产教训）
            String dataJson = JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(),
                    ElongOrderDetailRequest.builder().orderId(0L).affiliateConfirmationId(orderId).build()));
            ResponseResult<ElongOrderDetailResponse> result = new QueryOrderAccess(properties)
                    .access(new ElongRestCall("hotel.order.detail", dataJson));
            return result == null ? null : result.getData();
        } catch (Exception e) {
            log.error("艺龙下单：反查异常,orderId={}", orderId, e);
            return null;
        }
    }

    private ElongOrderCreateRequest buildRequest(BookingReq req, Offer offer) {
        int rooms = req.getRoomNum() == null || req.getRoomNum() < 1 ? 1 : req.getRoomNum();
        int adults = parseIntOrDefault(offer.credential(ElongOfferCredentials.ADULT_COUNT), 1);
        List<ElongOrderCreateRequest.OrderRoom> orderRooms = buildOrderRooms(req.getPersonName(), rooms);
        int customers = orderRooms.stream().mapToInt(r -> r.getCustomers().size()).sum();
        String checkIn = offer.credential(ElongOfferCredentials.CHECK_IN);
        return ElongOrderCreateRequest.builder()
                .affiliateConfirmationId(req.getOrderId())
                .hotelId(offer.credential(ElongOfferCredentials.HOTEL_ID))
                .roomTypeId(offer.credential(ElongOfferCredentials.ROOM_TYPE_ID))
                .ratePlanId(Long.valueOf(offer.credential(ElongOfferCredentials.RATE_PLAN_ID)))
                .arrivalDate(checkIn)
                .departureDate(offer.credential(ElongOfferCredentials.CHECK_OUT))
                .paymentType(PAYMENT_TYPE_PREPAY)
                .numberOfRooms(rooms)
                .numberOfCustomers(Math.max(customers, rooms))
                // 验后价（元）：与验价句柄同源，禁止另算——不符艺龙报 H001084
                .totalPrice(new BigDecimal(offer.credential(ElongOfferCredentials.TOTAL_PRICE)))
                .currencyCode(CURRENCY_RMB)
                .earliestArrivalTime(checkIn + EARLIEST_ARRIVAL_HMS)
                .latestArrivalTime(checkIn + LATEST_ARRIVAL_HMS)
                .confirmationType(CONFIRMATION_TYPE_NO_NEED)
                .contact(ElongOrderCreateRequest.Contact.builder()
                        .name(req.getContactName())
                        .mobile(req.getContactPhone())
                        .build())
                .orderRooms(orderRooms)
                .customerIPAddress(properties.getCustomerIpFallback())
                .littleMajiaId(offer.credential(ElongOfferCredentials.LITTLE_MAJIA_ID))
                .goodsUniqId(offer.credential(ElongOfferCredentials.GOODS_UNIQ_ID))
                .hotelCode(offer.credential(ElongOfferCredentials.HOTEL_CODE))
                .supplierId(offer.credential(ElongOfferCredentials.SUPPLIER_ID))
                .subSupplierId(offer.credential(ElongOfferCredentials.SUB_SUPPLIER_ID))
                .shopperProductId(offer.credential(ElongOfferCredentials.SHOPPER_PRODUCT_ID))
                .numberOfAdults(adults)
                .nat("CN")
                .isGuaranteeOrCharged(Boolean.TRUE)
                .build();
    }

    /**
     * 入住人分配：契约的 personName 允许以 、 / ，逗号分隔多人；每间房必须 ≥1 人
     * （艺龙校验），人数不足时复用第一位——渠道单常只有一位代表入住人。
     */
    static List<ElongOrderCreateRequest.OrderRoom> buildOrderRooms(String personName, int rooms) {
        String[] names = StringUtils.trimToEmpty(personName).split("[,，、/]");
        List<ElongOrderCreateRequest.OrderRoom> result = new ArrayList<>(rooms);
        for (int i = 0; i < rooms; i++) {
            String name = i < names.length && StringUtils.isNotBlank(names[i]) ? names[i].trim() : names[0].trim();
            result.add(ElongOrderCreateRequest.OrderRoom.builder()
                    .roomSequence(i + 1)
                    .customers(List.of(ElongOrderCreateRequest.Customer.builder()
                            .name(name)
                            .isChild(Boolean.FALSE)
                            .nationality("CN")
                            .build()))
                    .build());
        }
        return result;
    }

    private static int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public BookingRespDTO bookingRespConvert(BookingOutcomeHolder holder) {
        return BookingRespDTO.builder()
                .outcome(holder.outcome)
                .orderId(holder.orderId)
                .sOrderId(holder.sOrderId)
                .supplierErrorCode(holder.errorCode)
                .supplierErrorMessage(holder.errorMessage)
                .orderDesc(holder.errorMessage)
                .build();
    }

    /** 编排结果的中间载体，仅本类使用 */
    public static class BookingOutcomeHolder {
        BookingOutcome outcome;
        String orderId;
        String sOrderId;
        String errorCode;
        String errorMessage;

        static BookingOutcomeHolder success(String orderId, String sOrderId) {
            BookingOutcomeHolder h = new BookingOutcomeHolder();
            h.outcome = BookingOutcome.SUCCESS;
            h.orderId = orderId;
            h.sOrderId = sOrderId;
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
