package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingResult;
import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.DidaBookingClassifier.CreateClassification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.client.BookingConfirmAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order.client.BookingSearchAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingDetails;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaContact;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaGuestInfo;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaGuestRoom;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaName;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRequestHeader;
import com.trip.booking.spa.gateway.application.booking.AbstractBookingSyncSupportService;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 道旅下单。bean 名 {@code didaBookingSyncService} 供能力注册表发现。
 *
 * <p>凭据全部取自验价句柄（{@link DidaOfferCredentials}）：{@code ReferenceNo} 是下单唯一入口，
 * 住期/间数/占用必须与 PriceConfirm 那次一致（官方 booking-confirm：不一致即 3001/3005/3008），
 * 故一律从句柄回放、不取上游传参。
 *
 * <p>幂等与幽灵单防线：{@code ClientReference}=我方单号，道旅侧一对一绑定、同号重发报 3019；
 * 凡错误码说的是"一笔已存在的订单"（3018/3019/3020/…）或错误响应自带 BookingID，转按我方
 * 单号反查后再定；一切不确定形态回报 UNKNOWN 交上游查单——cursor 生产一周（2026-09-07~14）
 * 19 次下单里 2 次读超时，同期确认取消的超时实证已在道旅侧生效，超时绝不能报失败。
 */
@Slf4j
@Service("didaBookingSyncService")
public class DidaBookingSyncServiceImpl extends AbstractBookingSyncSupportService {

    @Resource
    private DidaProperties properties;

    @Resource
    private OfferStore offerStore;

    @Override
    protected String bookingGateKey() {
        return "dida.booking-enabled";
    }

    @Override
    protected boolean bookingAllowed() {
        return properties.isBookingEnabled();
    }

    @Override
    protected BookingResult doBooking(BookingCommand req) {
        // 以下判定全部在向道旅发出任何请求之前完成，供应商侧不会发生任何事，一律确定失败
        if (!properties.isConfigured()) {
            log.error("道旅下单：凭证未配置，无法下单,orderId={}", req.orderId());
            return failed(req.orderId(), null, "credentials_missing", "道旅凭证未配置，供应商侧未发生任何动作");
        }
        if (StringUtils.isBlank(req.offerId())) {
            return failed(req.orderId(), null, "missing_offer_id", "缺少 offerId，请先验价并回传该报价句柄");
        }
        Offer offer = offerStore.resolve(req.offerId());
        if (offer == null) {
            return failed(req.orderId(), null, "offer_unresolvable", "报价已过期或不存在，请重新验价后下单");
        }
        if (!Integer.valueOf(SupplierSourceEnum.DIDA.getCode()).equals(offer.getSupplierId())) {
            log.error("道旅下单：报价句柄归属供应商不符,orderId={},offerSupplierId={}", req.orderId(), offer.getSupplierId());
            return failed(req.orderId(), null, "offer_supplier_mismatch", "该报价句柄不属于道旅，请核对下单请求的供应商");
        }
        for (String key : DidaOfferCredentials.REQUIRED_FOR_BOOKING) {
            if (StringUtils.isBlank(offer.credential(key))) {
                log.error("道旅下单：报价句柄缺少凭据,orderId={},missingKey={}", req.orderId(), key);
                return failed(req.orderId(), null, "offer_credential_missing",
                        "报价句柄内容不完整（缺 " + key + "），请重新验价后下单");
            }
        }
        // 住期以验价句柄为准；上游传参不一致=调用方串单，道旅也必报 3001，拒于本地
        if (!offer.credential(DidaOfferCredentials.CHECK_IN).equals(req.checkIn())
                || !offer.credential(DidaOfferCredentials.CHECK_OUT).equals(req.checkOut())) {
            log.error("道旅下单：住期与验价不符,orderId={},req={}~{},offer={}~{}", req.orderId(),
                    req.checkIn(), req.checkOut(),
                    offer.credential(DidaOfferCredentials.CHECK_IN), offer.credential(DidaOfferCredentials.CHECK_OUT));
            return failed(req.orderId(), null, "stay_mismatch", "下单住期与验价时不一致，请重新验价后下单");
        }
        if (StringUtils.isAllBlank(req.personName(), req.contactName()) || StringUtils.isBlank(req.contactPhone())) {
            return failed(req.orderId(), null, "missing_guest_or_contact", "缺少入住人姓名或联系人电话，供应商侧未发生任何动作");
        }

        DidaBookingConfirmRequest request = buildRequest(req, offer);
        ResponseResult<DidaBookingConfirmResponse> result = confirmAccess()
                .access(request, CallPurpose.ORDER);
        DidaBookingConfirmResponse resp = result == null ? null : result.getData();
        CreateClassification classification = DidaBookingClassifier.classifyCreate(resp);
        DidaBookingDetails details = resp == null ? null : resp.bookingDetails();
        log.info("道旅下单：分类结果,orderId={},classification={},errorCode={},bookingId={},status={}",
                req.orderId(), classification, resp == null ? null : resp.errorCode(),
                details == null ? null : details.getBookingId(), details == null ? null : details.getStatus());

        switch (classification) {
            case CONFIRMED:
                offerStore.consume(req.offerId());
                return successOf(req.orderId(), details);
            case PENDING:
                // 订单已在道旅侧存在但未到终态（5 Pending 3 分钟内、6 OnRequest 120 分钟内到终态）
                return unknown(req.orderId(), details.getBookingId(), "status:" + details.getStatus(),
                        "供应商已受理但订单未到终态（Status=" + details.getStatus() + "），请凭供应商单号查单确证");
            case FINAL_FAILED:
                return failed(req.orderId(), details.getBookingId(), "status:" + details.getStatus(),
                        "供应商回报订单终态 Failed，未成单");
            case FINAL_CANCELED:
                return failed(req.orderId(), details.getBookingId(), "status:" + details.getStatus(),
                        "供应商回报订单终态 Canceled，无有效订单");
            case REQUERY:
                return resolveByRequery(req, resp);
            case AUTH_CONFIG:
                Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.DIDA));
                log.error("[auth-config] 道旅下单：我方凭据/配置病，供应商无辜、重试无效、需人工处理。code={},message={},orderId={}",
                        resp.errorCode(), resp.errorMessage(), req.orderId());
                return failed(req.orderId(), null, resp.errorCode(), "我方凭据/配置被道旅拒绝，下单未发生：" + resp.errorMessage());
            case DETERMINISTIC_FAILURE:
                return failed(req.orderId(), null, resp.errorCode(), "供应商拒绝下单：" + resp.errorMessage());
            case INDETERMINATE:
            default:
                // 无响应≠未成单（cursor 生产读超时 30 秒的下单，道旅侧可能已成单）
                return unknown(req.orderId(), null, resp == null ? null : resp.errorCode(),
                        "下单结果不确定，请凭我方单号反查确证");
        }
    }

    /**
     * 错误码说的是"一笔已存在的订单"：按 Error.BookingID（有则用）或我方单号反查后再定。
     * 反查到终态 2 → 收敛为成功；终态 3/4 → 无有效订单，确定失败（3018 时我方单号已绑定
     * 一笔已取消订单，重订必须换新单号）；非终态 → 不确定并带单号；反查不到或反查失败 → 不确定。
     */
    private BookingResult resolveByRequery(BookingCommand req, DidaBookingConfirmResponse resp) {
        String code = resp.errorCode();
        String errorBookingId = resp.getError() == null ? null : StringUtils.trimToNull(resp.getError().getBookingId());
        DidaBookingSearchResponse search = searchQuietly(errorBookingId, req.orderId());
        DidaBookingDetails found = search == null || !search.isSucc() ? null : search.single();
        if (found == null) {
            log.warn("道旅下单：供应商报已有相关订单但反查未能确证,orderId={},code={},errorBookingId={},searchSucc={},hits={}",
                    req.orderId(), code, errorBookingId, search != null && search.isSucc(),
                    search == null ? null : search.bookings().size());
            return unknown(req.orderId(), errorBookingId, code,
                    "供应商报 " + code + "（" + resp.errorMessage() + "），反查未能确证，请稍后凭我方单号反查");
        }
        CreateClassification byStatus = DidaBookingClassifier.classifyStatus(found.getStatus());
        log.info("道旅下单：反查结果,orderId={},code={},bookingId={},status={},classification={}",
                req.orderId(), code, found.getBookingId(), found.getStatus(), byStatus);
        switch (byStatus) {
            case CONFIRMED:
                offerStore.consume(req.offerId());
                return successOf(req.orderId(), found);
            case FINAL_FAILED:
                return failed(req.orderId(), found.getBookingId(), code,
                        "供应商报 " + code + "，反查该单终态为 Failed，未成单");
            case FINAL_CANCELED:
                return failed(req.orderId(), found.getBookingId(), code,
                        "供应商报 " + code + "，我方单号名下的道旅订单已取消；如需重订请换新单号");
            default:
                return unknown(req.orderId(), found.getBookingId(), code,
                        "供应商报 " + code + "，反查该单未到终态（Status=" + found.getStatus() + "），请稍后查单确证");
        }
    }

    /** 反查一次，任何异常只记录不外抛——调用方的结论不应因补充信息失败而改变 */
    private DidaBookingSearchResponse searchQuietly(String bookingId, String clientReference) {
        try {
            ResponseResult<DidaBookingSearchResponse> result = searchAccess().access(
                    bookingId != null ? BookingSearchAccess.byBookingId(properties, bookingId)
                            : BookingSearchAccess.byClientReference(properties, clientReference),
                    CallPurpose.ORDER);
            return result == null ? null : result.getData();
        } catch (Exception e) {
            log.error("道旅下单：反查异常,orderId={},bookingId={}", clientReference, bookingId, e);
            return null;
        }
    }

    /** 通道出生地，可覆写以便编排用真实报文夹具跑通（DidaBookingFlowTest）；理由见取消实现同名方法 */
    protected BookingConfirmAccess confirmAccess() {
        return new BookingConfirmAccess(properties);
    }

    protected BookingSearchAccess searchAccess() {
        return new BookingSearchAccess(properties);
    }

    DidaBookingConfirmRequest buildRequest(BookingCommand req, Offer offer) {
        int reqRooms = req.roomNum() == null || req.roomNum() < 1 ? 1 : req.roomNum();
        // 间数以验价句柄为准（官方：与 PriceConfirm 不一致即被拒，且 PriceConfirm 的总价按它算）
        int rooms = parseIntOrDefault(offer.credential(DidaOfferCredentials.ROOM_NUM), reqRooms);
        if (rooms != reqRooms) {
            log.error("道旅下单：下单间数与验价句柄不一致，以句柄为准,orderId={},req={},offer={}", req.orderId(), reqRooms, rooms);
        }
        int adults = parseIntOrDefault(offer.credential(DidaOfferCredentials.ADULT_COUNT), 1);
        List<Integer> childAges = parseAges(offer.credential(DidaOfferCredentials.CHILD_AGES));

        DidaBookingConfirmRequest request = new DidaBookingConfirmRequest();
        request.setHeader(new DidaRequestHeader(properties.getClientId(), properties.getLicenseKey()));
        request.setReferenceNo(offer.credential(DidaOfferCredentials.REFERENCE_NO));
        request.setCheckInDate(offer.credential(DidaOfferCredentials.CHECK_IN));
        request.setCheckOutDate(offer.credential(DidaOfferCredentials.CHECK_OUT));
        request.setNumOfRooms(rooms);
        request.setGuestList(buildGuestList(
                StringUtils.defaultIfBlank(req.personName(), req.contactName()), rooms, adults, childAges));
        request.setContact(buildContact(req));
        request.setClientReference(req.orderId());
        return request;
    }

    /**
     * 入住人分配。契约的 personName 以 、 ，逗号分隔多人，而道旅要求每间的成人/儿童<b>人数</b>
     * 与验价一致（官方 GuestList 字段说明），故按句柄里的占用铺满：每间 adults 位成人 +
     * childAges 里每个年龄一位儿童；姓名不够时循环复用已有姓名——渠道单常只有一位代表入住人
     * （官方样例里成人与儿童也是同一姓名）。儿童 Age 必填（官方：「成人选填，儿童必填」）。
     */
    static List<DidaGuestRoom> buildGuestList(String personNames, int rooms, int adults, List<Integer> childAges) {
        List<String> names = new ArrayList<>();
        for (String n : StringUtils.trimToEmpty(personNames).split("[,，、]")) {
            if (StringUtils.isNotBlank(n)) {
                names.add(n.trim());
            }
        }
        if (names.isEmpty()) {
            names.add(StringUtils.trimToEmpty(personNames));
        }
        int cursor = 0;
        List<DidaGuestRoom> guestList = new ArrayList<>(rooms);
        for (int roomNum = 1; roomNum <= rooms; roomNum++) {
            List<DidaGuestInfo> guests = new ArrayList<>();
            for (int i = 0; i < Math.max(adults, 1); i++) {
                DidaGuestInfo adult = new DidaGuestInfo();
                adult.setName(splitName(names.get(cursor++ % names.size())));
                adult.setIsAdult(true);
                guests.add(adult);
            }
            for (Integer age : childAges) {
                DidaGuestInfo child = new DidaGuestInfo();
                child.setName(splitName(names.get(0)));
                child.setIsAdult(false);
                child.setAge(age);
                guests.add(child);
            }
            DidaGuestRoom room = new DidaGuestRoom();
            room.setRoomNum(roomNum);
            room.setGuestInfo(guests);
            guestList.add(room);
        }
        return guestList;
    }

    /**
     * 一个姓名 → {First, Last}。道旅两个字段都是结构性的，没有"整名"字段，故必须拆：
     * <ol>
     *   <li>含 {@code /}：{@code 姓/名}（上游既有惯例，艺龙同款）</li>
     *   <li>含空白：首段为姓、其余为名（拼音「Wang Xianen」的中文语序；cursor 道旅在产同款拆法）</li>
     *   <li>其余多字符：首字为姓、其余为名（中文姓名；cursor 在产同款拆法，复姓会拆错，
     *       无更好依据前照抄，不另造规则）</li>
     *   <li>单字符：姓名同填</li>
     * </ol>
     */
    static DidaName splitName(String raw) {
        String name = StringUtils.trimToEmpty(raw);
        int slash = name.indexOf('/');
        if (slash > 0 && slash < name.length() - 1) {
            return new DidaName(name.substring(slash + 1).trim(), name.substring(0, slash).trim());
        }
        String[] parts = name.split("\\s+", 2);
        if (parts.length == 2 && StringUtils.isNotBlank(parts[1])) {
            return new DidaName(parts[1].trim(), parts[0].trim());
        }
        if (name.length() > 1) {
            return new DidaName(name.substring(1), name.substring(0, 1));
        }
        return new DidaName(name, name);
    }

    /** 联系人：姓名与电话取上游契约；邮箱接收供应商通知，指向运营团队而非旅客，故配置化 */
    private DidaContact buildContact(BookingCommand req) {
        DidaContact contact = new DidaContact();
        contact.setName(splitName(StringUtils.defaultIfBlank(req.contactName(), req.personName())));
        contact.setPhone(StringUtils.trimToNull(req.contactPhone()));
        contact.setEmail(StringUtils.trimToNull(properties.getBookingContactEmail()));
        return contact;
    }

    static List<Integer> parseAges(String csv) {
        List<Integer> ages = new ArrayList<>();
        for (String part : StringUtils.trimToEmpty(csv).split(",")) {
            if (StringUtils.isNotBlank(part)) {
                try {
                    ages.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    log.error("道旅下单：句柄里的儿童年龄不可解析,raw={}", csv);
                }
            }
        }
        return ages;
    }

    private static int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }


    private static BookingResult successOf(String orderId, DidaBookingDetails details) {
        log.info("道旅下单：成单,orderId={},sOrderId={},confirmationCode={},totalPrice={}{}",
                orderId, details.getBookingId(), details.getConfirmationCode(), details.getTotalPrice(), details.currency());
        return BookingResult.success(orderId, details.getBookingId(),
                StringUtils.trimToNull(details.getConfirmationCode()), "下单成功");
    }

    /** 确定失败；供应商单号有则带上（那是"这笔已存在的单确实失败/已取消"这个结论的证据） */
    private static BookingResult failed(String orderId, String sOrderId, String code, String message) {
        return BookingResult.failed(orderId, code, message, message).withSupplierOrderId(sOrderId);
    }

    /** 结果不确定；供应商单号有则带上——上游正是要凭它查单确证 */
    private static BookingResult unknown(String orderId, String sOrderId, String code, String message) {
        return BookingResult.unknown(orderId, code, message, message).withSupplierOrderId(sOrderId);
    }
}
