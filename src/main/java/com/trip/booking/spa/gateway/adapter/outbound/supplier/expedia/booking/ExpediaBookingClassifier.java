package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.booking;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CreateOrderResponse;
import org.apache.commons.lang3.StringUtils;

/**
 * 把 Expedia 下单响应判为三态之一。
 *
 * <p>全部为纯函数，便于单测钉死。判据有两个来源：EPS 技术研讨会材料的错误处理规定
 * （见 {@code docs/expedia-booking-contract.md} §6），以及 2026-08-10 沙箱实测。
 *
 * <p><b>判据向"不确定"倾斜是有意为之</b>：误判不确定的代价是多查一次单，
 * 误判失败的代价是上游退款而供应商侧订单仍在（资损）。故只有确证不会因重试改变的
 * 业务性拒绝才判失败，其余一律不确定。
 */
public final class ExpediaBookingClassifier {

    /** 重复下单：Expedia 侧已有同一业务单号的订单，含义是"首次已成功" */
    public static final String DUPLICATE_ITINERARY = "duplicate_itinerary";

    private ExpediaBookingClassifier() {
    }

    /** 分类结果。DUPLICATE 需调用方转为反查后再定，故独立于三态之外 */
    public enum Classification {
        /** 已确认成单 */
        SUCCESS,
        /**
         * Expedia 侧已存在同一业务单号的订单。
         * 调用方必须转为反查取回既有订单号，最终判 SUCCESS——
         * <b>不可</b>因其 HTTP 状态码为 400 而判失败。
         */
        DUPLICATE,
        /** 业务性拒绝，重试必再败 */
        DETERMINISTIC_FAILURE,
        /** 结果不确定，可能已在供应商侧生效 */
        INDETERMINATE
    }

    /**
     * @param httpStatus HTTP 状态码；无响应时传 0
     * @param response   已解析的响应体，可为 null
     * @param rawBody    原始响应体，用于识别嵌套错误码（解析后的对象不含 errors 明细）
     */
    public static Classification classify(int httpStatus, CreateOrderResponse response, String rawBody) {
        // 拿到订单号即成功。优先于一切错误判断——响应撕裂时二者可能同时出现，
        // 此时订单已成立，按订单号判可避免把已成立的订单误判为失败
        if (response != null && response.isSucc()) {
            return Classification.SUCCESS;
        }

        // 重复下单：真实含义是首次已成功。必须在 4XX 判定之前拦下
        if (containsDuplicateItinerary(rawBody, response)) {
            return Classification.DUPLICATE;
        }

        // 无响应：请求可能已送达而响应丢失
        if (httpStatus == 0) {
            return Classification.INDETERMINATE;
        }

        // 499 与 5xx：EPS 明确规定「双方都不知道最终状态」，须等 90 秒后反查
        if (httpStatus == 499 || httpStatus >= 500) {
            return Classification.INDETERMINATE;
        }

        // 409 / 410：EPS 规定须反查是否已产生重复订单
        if (httpStatus == 409 || httpStatus == 410) {
            return Classification.INDETERMINATE;
        }

        // 其余 4XX 为业务性拒绝（参数非法、满房、售罄等），重试必再败
        if (httpStatus >= 400) {
            return Classification.DETERMINISTIC_FAILURE;
        }

        // 2xx/3xx 却没拿到订单号：形态不可判读，不敢当成功也不敢当失败
        return Classification.INDETERMINATE;
    }

    /**
     * 识别 duplicate_itinerary。Expedia 把它放在 {@code errors[].type} 嵌套结构里，
     * 顶层 {@code type} 只是笼统的 {@code invalid_input}，故必须扫原始响应体。
     */
    private static boolean containsDuplicateItinerary(String rawBody, CreateOrderResponse response) {
        if (StringUtils.contains(rawBody, DUPLICATE_ITINERARY)) {
            return true;
        }
        return response != null && StringUtils.contains(response.getType(), DUPLICATE_ITINERARY);
    }
}
