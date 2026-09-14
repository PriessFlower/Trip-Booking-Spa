package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmResponse;
import com.trip.booking.spa.gateway.domain.booking.OrderState;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingDetails;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * 把道旅订单族响应判为确定的态。全部为纯函数，便于单测钉死。
 *
 * <p>判据来源：官方 information-hub/api-error-code「订单相关错误代码」与 booking-search 的
 * 状态表（2026-09-13 查阅），以及 cursor 生产账号在 supplier_request_log 里留下的真实报文
 * （2026-09-07~09-14 一周：下单 19 次、预取消 8 次、确认取消 7 次、查单 18,424 次）。
 * <b>判据向"不确定"倾斜</b>：确定失败采用白名单制——只有官方文案能证明"请求被拒于校验、
 * 供应商侧无单、重试同参数必再败"的码才入册，表外码一律不确定。
 *
 * <p>为什么不能把"没成单"一律判失败：cursor 一周内下单 19 次里 2 次读超时（30 秒），而
 * 同期确认取消 5 次全部读超时、随后预取消却回 3018「已取消」——<b>超时的请求在道旅侧已经生效</b>。
 * 下单若照 cursor 那样把超时报成失败，上游退款改单，酒店那边却有一间房订着。
 */
public final class DidaBookingClassifier {

    /** 我方订单状态码，取值含义见 {@code OrderQueryResult#orderStatus} */
    static final int ORDER_STATUS_BOOKING = 20;
    static final int ORDER_STATUS_BOOK_SUCCESS = 21;
    static final int ORDER_STATUS_BOOK_FAIL = 22;
    static final int ORDER_STATUS_CANCEL_SUCCESS = 31;

    /**
     * 我方凭据/配置病：2017「机构信息验证失败」（错 LicenseKey 与出口 IP 不在白名单都报它，
     * 2026-09-08 实测文案分别为 Invalid LicenseKey / Invalid ip/signature）、2019「请求被禁止」。
     * 请求被拒于门禁，供应商侧未处理业务；按 FailureKind.AUTH_CONFIG 三纪律处置。
     */
    private static final Set<String> AUTH_CONFIG_CODES = Set.of("2017", "2019");

    /**
     * 下单确定失败白名单（官方码表文案即证据）：请求在参数/产品/额度/风控校验阶段被拒，
     * 供应商侧无单，同参数重试必再败。
     * <ul>
     *   <li>-2 请求形态不合法（gzip 缺失、ReferenceNo 缺失等「Invalid Request Parameter」；
     *       2026-09-08 实测与 cursor 2026-01 留存报文）</li>
     *   <li>3001 订单信息不正确（住期/间数变了）、3002 参考号不正确、3006 参考号过期、
     *       3008 房间数量不正确、3022 到店日期不正确</li>
     *   <li>3005 入住人信息不正确（cursor 2025-11 真实报文「Incorrect occupancy information
     *       specified in one room」）、3011 联系人信息为空、3035/3038 客人名字验证不通过</li>
     *   <li>3014 信用额度不够、3015 无房或变价、3021 酒店已经停售、3040 变价超过能承受的范围、
     *       3041 餐型验证不通过、3050 报价没有提供</li>
     *   <li>3025/3026 ClientReference 超长/未提供、3027 ValueAdd 不匹配、3034 优惠券不匹配</li>
     *   <li>4010 高风险行为、4030 疑似 OFAC 制裁客人、4031 机构不允许下不可取消订单</li>
     * </ul>
     */
    private static final Set<String> CREATE_DETERMINISTIC_FAILURES = Set.of(
            "-2", "3001", "3002", "3005", "3006", "3008", "3011", "3014", "3015", "3021", "3022",
            "3025", "3026", "3027", "3034", "3035", "3038", "3040", "3041", "3050",
            "4010", "4030", "4031");

    /**
     * 道旅侧可能已有一笔与本单相关的订单，必须反查后再定：
     * 3018 订单已经取消不可重新下单、3019 客户订单号重复、3020 订单正在处理中（可能需等 5 分钟）、
     * 3033 订单是 Pre-Confirm 状态、3036 超时未支付、3039 订单重复、3042 dida 订单号已存在、
     * 3043 dida 订单号与客户订单号不匹配、3060/3070 OnRequest 订单确认超时/失败、3090 支付失败。
     * 共同点：文案说的是<b>一笔已存在订单的状态</b>，不是本请求被拒于校验。
     */
    private static final Set<String> CREATE_REQUERY_CODES = Set.of(
            "3018", "3019", "3020", "3033", "3036", "3039", "3042", "3043", "3060", "3070", "3090");

    /**
     * 确认取消的确定失败白名单：3003 BookingID 不正确、3004 CancelConfirmID 不正确、
     * 3007 取消确认号过期——确认号/单号本身被拒，取消确未发生。
     * 4000「取消失败」<b>不入册</b>：文案没说是拒于校验还是执行中途失败，而确认取消已实证
     * 会"超时却生效"，此类码只能不确定。
     */
    private static final Set<String> CANCEL_CONFIRM_DETERMINISTIC_FAILURES = Set.of("3003", "3004", "3007");

    /** 3018：目标状态已达成。cursor 生产 2026-09-10 预取消实测文案「Booking is already canceled.」 */
    private static final String ALREADY_CANCELED = "3018";

    private DidaBookingClassifier() {
    }

    /** 下单分类 */
    public enum CreateClassification {
        /** Status=2 Confirmed：官方唯一的成单确认方式 */
        CONFIRMED,
        /** 已有单号但未到终态（0/1/5/6 或缺席）：订单存在、结果未定，回 UNKNOWN 并带单号 */
        PENDING,
        /** Status=4 Failed：官方终态，供应商侧无有效订单 */
        FINAL_FAILED,
        /** Status=3 Canceled：官方终态，订单曾成立又已取消，供应商侧无有效订单 */
        FINAL_CANCELED,
        /** 错误码说的是一笔已存在订单，须按我方单号（或 Error.BookingID）反查后再定 */
        REQUERY,
        /** 白名单内的业务性拒绝，供应商侧无单，重试必再败 */
        DETERMINISTIC_FAILURE,
        /** 我方凭据/配置病，请求未达业务 */
        AUTH_CONFIG,
        /** 其余一切形态：结果不确定，可能已在供应商侧生效 */
        INDETERMINATE
    }

    public static CreateClassification classifyCreate(DidaBookingConfirmResponse response) {
        if (response == null) {
            return CreateClassification.INDETERMINATE;
        }
        if (response.isSucc()) {
            DidaBookingDetails details = response.bookingDetails();
            if (details == null || StringUtils.isBlank(details.getBookingId())) {
                // 报成功却没有单号：契约撕裂，无从确证有没有单
                return CreateClassification.INDETERMINATE;
            }
            return classifyStatus(details.getStatus());
        }
        // 错误响应里带单号 = 道旅侧已有相关订单，优先于错误码
        if (response.getError() != null && StringUtils.isNotBlank(response.getError().getBookingId())) {
            return CreateClassification.REQUERY;
        }
        String code = StringUtils.trimToEmpty(response.errorCode());
        if (AUTH_CONFIG_CODES.contains(code)) {
            return CreateClassification.AUTH_CONFIG;
        }
        if (CREATE_REQUERY_CODES.contains(code)) {
            return CreateClassification.REQUERY;
        }
        if (CREATE_DETERMINISTIC_FAILURES.contains(code)) {
            return CreateClassification.DETERMINISTIC_FAILURE;
        }
        return CreateClassification.INDETERMINATE;
    }

    /**
     * 订单状态 → 分类（官方状态表）。只认终态 2/3/4；其余含缺席一律 PENDING——
     * 「其他任何返回结果均不能视为订单的最终处理结果」。
     */
    public static CreateClassification classifyStatus(Integer status) {
        if (status == null) {
            return CreateClassification.PENDING;
        }
        switch (status) {
            case DidaBookingDetails.STATUS_CONFIRMED:
                return CreateClassification.CONFIRMED;
            case DidaBookingDetails.STATUS_CANCELED:
                return CreateClassification.FINAL_CANCELED;
            case DidaBookingDetails.STATUS_FAILED:
                return CreateClassification.FINAL_FAILED;
            default:
                return CreateClassification.PENDING;
        }
    }

    /**
     * 道旅订单状态 → 我方订单状态（官方 information-hub/api-booking-status）。
     * 映射纪律：只映射语义铁定的取值，<b>识别不出的返回 null</b> 并由调用方保留原文
     * （{@code OrderQueryResult#supplierOrderStatus}）——猜默认值会把未知状态说成已知。
     * <ul>
     *   <li>2 Confirmed → {@link OrderState#BOOKED}</li>
     *   <li>3 Canceled → {@link OrderState#CANCELED}</li>
     *   <li>4 Failed → {@link OrderState#BOOK_FAILED}</li>
     *   <li>0 PreBook / 1 Booked / 5 Pending / 6 OnRequest → {@link OrderState#BOOKING}
     *       （官方：非终态，会在 3 分钟／120 分钟内到终态）</li>
     * </ul>
     */
    public static OrderState toOrderState(Integer status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case DidaBookingDetails.STATUS_CONFIRMED:
                return OrderState.BOOKED;
            case DidaBookingDetails.STATUS_CANCELED:
                return OrderState.CANCELED;
            case DidaBookingDetails.STATUS_FAILED:
                return OrderState.BOOK_FAILED;
            case DidaBookingDetails.STATUS_PRE_BOOK:
            case DidaBookingDetails.STATUS_BOOKED:
            case DidaBookingDetails.STATUS_PENDING:
            case DidaBookingDetails.STATUS_ON_REQUEST:
                return OrderState.BOOKING;
            default:
                return null;
        }
    }

    /** 取消两步各自的分类 */
    public enum CancelStepClassification {
        /** 该步成功：预取消回了确认号 / 确认取消回了空 Success */
        ACCEPTED,
        /** 3018：订单已处于取消状态，目标状态已达成 */
        ALREADY_CANCELED,
        /** 我方凭据/配置病 */
        AUTH_CONFIG,
        /** 供应商明确拒绝且该步确未发生 */
        DETERMINISTIC_FAILURE,
        /** 无响应或码义未核实：该步可能已生效 */
        INDETERMINATE
    }

    /**
     * 预取消分类。预取消只签发确认号、不改订单状态（官方：「只调用这个 api 是不足以真正取消
     * 一个订单的」），故<b>任何解析得出的错误都是确定失败</b>——取消尚未发出；只有无响应才不确定。
     */
    public static CancelStepClassification classifyPreCancel(DidaBookingCancelResponse response) {
        if (response == null) {
            return CancelStepClassification.INDETERMINATE;
        }
        if (response.isSucc()) {
            return CancelStepClassification.ACCEPTED;
        }
        String code = StringUtils.trimToEmpty(response.errorCode());
        if (ALREADY_CANCELED.equals(code)) {
            return CancelStepClassification.ALREADY_CANCELED;
        }
        if (AUTH_CONFIG_CODES.contains(code)) {
            return CancelStepClassification.AUTH_CONFIG;
        }
        return CancelStepClassification.DETERMINISTIC_FAILURE;
    }

    /**
     * 确认取消分类。这一步会真改状态，且已实证"超时却生效"，故只有白名单内的码判确定失败，
     * 无响应与表外码一律不确定，交后续查单确证。
     */
    public static CancelStepClassification classifyCancelConfirm(DidaBookingCancelConfirmResponse response) {
        if (response == null) {
            return CancelStepClassification.INDETERMINATE;
        }
        if (response.isSucc()) {
            return CancelStepClassification.ACCEPTED;
        }
        String code = StringUtils.trimToEmpty(response.errorCode());
        if (ALREADY_CANCELED.equals(code)) {
            return CancelStepClassification.ALREADY_CANCELED;
        }
        if (AUTH_CONFIG_CODES.contains(code)) {
            return CancelStepClassification.AUTH_CONFIG;
        }
        if (CANCEL_CONFIRM_DETERMINISTIC_FAILURES.contains(code)) {
            return CancelStepClassification.DETERMINISTIC_FAILURE;
        }
        return CancelStepClassification.INDETERMINATE;
    }

    public static boolean isAuthConfig(String code) {
        return AUTH_CONFIG_CODES.contains(StringUtils.trimToEmpty(code));
    }
}
