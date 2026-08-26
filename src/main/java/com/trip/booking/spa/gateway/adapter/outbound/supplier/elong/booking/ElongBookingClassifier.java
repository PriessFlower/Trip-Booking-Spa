package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCreateResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * 把艺龙下单/取消响应判为确定的态。全部为纯函数，便于单测钉死。
 *
 * <p>判据来源：官方文档（hotel.order.create/cancel + 错误码表，2026-08-15 核对）
 * 与 cursor 生产实证。<b>判据向"不确定"倾斜</b>：确定失败采用白名单制——只有确证
 * "请求被拒、供应商侧未成单、重试同参数必再败"的错误码才入册，表外码一律不确定。
 * 反面教材即 cursor：下单未返回订单号一律报失败，而请求可能已在艺龙侧成单，
 * 上游据"失败"退款改单 → 幽灵单（SPA 三态契约的存在理由）。
 */
public final class ElongBookingClassifier {

    /**
     * 下单确定失败白名单：请求在参数校验/产品校验阶段被拒，供应商侧无单。
     * <ul>
     *   <li>H001012 客人访问IP必须填写（参数校验，官方文档）</li>
     *   <li>H001039 预付/强制担保订单必须已担保或已支付（参数校验，cursor 生产实证）</li>
     *   <li>H001083 获取产品信息失败——产品无效或关房（官方文档；cursor 曾把它兜成
     *       可订导致丢真单，绝不可折叠进不确定以外的兜底）</li>
     *   <li>H001084 总价计算错误——报价已换代（官方文档）</li>
     *   <li>H001097 客人姓名未通过校验（官方文档）</li>
     *   <li>H001188/H001197 请求缺马甲等必要参数（cursor 生产实证，请求被拒于校验层）</li>
     *   <li>H000033 国籍限制——产品限定客源国籍（hotel.detail 的 RatePlan.Nat），同客人
     *       重试必再拒。证据：2026-08-15 真单实测（SPA-REAL-20260815-01）报此码后
     *       按我方单号反查 5 次均 H001054，确证供应商侧无单</li>
     * </ul>
     */
    private static final Set<String> CREATE_DETERMINISTIC_FAILURES = Set.of(
            "H001012", "H001039", "H001083", "H001084", "H001097", "H001188", "H001197",
            "H000033");

    /** 疑似重复：首单可能已成立，必须反查后再定 */
    private static final Set<String> CREATE_DUPLICATE_SUSPECTS = Set.of(
            // H001043 订单重复或过快提交（同 AffiliateConfirmationId 45 秒内重发，官方文档）
            "H001043",
            // H001045 疑似重复订单（入住日期+手机号+姓名重复，官方文档）
            "H001045");

    private ElongBookingClassifier() {
    }

    /** 下单分类。DUPLICATE_SUSPECT 需调用方按我方单号反查后再定，故独立于三态之外 */
    public enum Classification {
        /** Result.OrderId 已下发，订单确证成立 */
        SUCCESS,
        /** 供应商报重复/过快提交，首单可能已成立，须反查确证 */
        DUPLICATE_SUSPECT,
        /** 白名单内的业务性拒绝，供应商侧无单，重试必再败 */
        DETERMINISTIC_FAILURE,
        /** 其余一切形态：结果不确定，可能已在供应商侧生效 */
        INDETERMINATE
    }

    public static Classification classifyCreate(ElongOrderCreateResponse response) {
        if (response == null) {
            return Classification.INDETERMINATE;
        }
        // 拿到订单号即成功，优先于一切错误判断——响应撕裂时订单已成立（移植风险⑧）
        if (response.orderId() != null) {
            return Classification.SUCCESS;
        }
        String errorCode = StringUtils.trimToEmpty(response.errorCode());
        if (CREATE_DUPLICATE_SUSPECTS.stream().anyMatch(errorCode::startsWith)) {
            return Classification.DUPLICATE_SUSPECT;
        }
        if (CREATE_DETERMINISTIC_FAILURES.stream().anyMatch(errorCode::startsWith)) {
            return Classification.DETERMINISTIC_FAILURE;
        }
        return Classification.INDETERMINATE;
    }

    /** 取消分类结果 */
    public enum CancelClassification {
        /** 供应商已受理取消（含"订单已处于取消状态"的幂等成功） */
        SUCCESS,
        /** 业务性拒绝：订单不存在/罚金不一致/状态暂不允许，供应商侧取消未发生 */
        DETERMINISTIC_FAILURE,
        /**
         * 我方凭据/配置病：请求被拒于门禁（出口 IP 不在白名单等），供应商侧未处理业务。
         * 取消确未发生（同 DETERMINISTIC_FAILURE），但成因在我方——须按
         * FailureKind.AUTH_CONFIG 三纪律处置（不归因供应商、必须告警、修复前重试无效）。
         */
        AUTH_CONFIG,
        /** 结果不确定，可能已生效 */
        INDETERMINATE
    }

    /**
     * 我方配置病白名单，与业务码同样只登记有实证的：
     * A101010012 访问IP错误——出口 IP 不在艺龙白名单，错误文案自带它看到的 IP
     * （2026-08 SPA e2e 与 cursor 生产均实证）。签名错、账号停用等码<b>无实证不入册</b>，
     * 表外一律走 INDETERMINATE 老路。classifyCreate 暂不识别本档：booking 能力尚未
     * 解耦、无承载成因档的通道，随其解耦补齐。
     */
    private static final Set<String> AUTH_CONFIG_FAILURES = Set.of("A101010012");

    /**
     * 取消确定失败白名单（官方文档）：
     * H001054 订单不存在；H001139 取消罚金不一致；H001151 订单确认中暂不允许取消
     * （非"重试必败"，但供应商明确拒绝且取消确未发生，符合 FAILED 判据——
     * message 提示可稍后重试）；H001094 取消订单失败（官方明示的业务拒绝）。
     */
    private static final Set<String> CANCEL_DETERMINISTIC_FAILURES = Set.of(
            "H001054", "H001094", "H001139", "H001151");

    /** H001056 订单已处于取消状态：目标状态已达成，幂等成功 */
    private static final String CANCEL_ALREADY_CANCELLED = "H001056";

    public static CancelClassification classifyCancel(ElongOrderCancelResponse response) {
        if (response == null) {
            return CancelClassification.INDETERMINATE;
        }
        if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getSuccesss())) {
            return CancelClassification.SUCCESS;
        }
        String errorCode = StringUtils.trimToEmpty(response.errorCode());
        if (errorCode.startsWith(CANCEL_ALREADY_CANCELLED)) {
            return CancelClassification.SUCCESS;
        }
        if (AUTH_CONFIG_FAILURES.stream().anyMatch(errorCode::startsWith)) {
            return CancelClassification.AUTH_CONFIG;
        }
        if (CANCEL_DETERMINISTIC_FAILURES.stream().anyMatch(errorCode::startsWith)) {
            return CancelClassification.DETERMINISTIC_FAILURE;
        }
        return CancelClassification.INDETERMINATE;
    }
}
