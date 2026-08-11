package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.common.enums.BookingOutcome;
import com.trip.booking.spa.core.api.dto.BookingRespDTO;
import com.trip.booking.spa.core.api.request.BookingReq;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 下单模板。
 *
 * <p><b>本类的核心职责是「绝不把不确定说成失败」</b>。原实现把 doBooking 抛出的一切异常与
 * 空响应统一吞成 null，控制层再转为一条错误响应——上游只能理解为下单失败，据此退款；
 * 而超时、连接中断这类情形下供应商可能已真实成单，于是「已退款却仍占房」。
 *
 * <p>故本类的兜底一律回报 {@link BookingOutcome#UNKNOWN}：无法证明请求未在供应商侧生效时，
 * 就不能替上游作出「失败」的判断。判 {@link BookingOutcome#FAILED} 的权力只交给各供应商
 * 实现——只有它能读懂供应商的业务错误码，也只有确证不会因重试而改变的结果才配判失败。
 *
 * <p><b>关于泛型 T</b>：查价、验价等模板里 T 是供应商原始响应类型，本模板允许 T 为
 * 实现方自定的<b>编排中间态</b>。因为下单往往不是一次调用就能定论——例如 Expedia 实现
 * 需要「下单 → 遇重复则反查 → 成功后再反查补确认号」，多次调用的结果装不进单一响应对象。
 * 此时 {@code doBooking} 承担编排、{@code bookingRespConvert} 退化为字段搬运，是预期用法。
 *
 * <p>另注：本类捕获一切异常并判 UNKNOWN，其中包含本地限流拒绝
 * （{@code RedisLimitException} 在请求发出前抛出，此时可确知未下单）。严格说这类情形
 * 「重试即可成功」，与 UNKNOWN 的语义不完全吻合，但判 UNKNOWN 是安全的——上游至多白查
 * 一次单，绝不会误退款。故不为此另立一态。
 */
@Slf4j
public abstract class AbstractBookingSyncSupportService<T> implements BookingSyncService {

    @Override
    public BookingRespDTO booking(BookingReq bookingReq) {
        try {
            T t = doBooking(bookingReq);

            log.info("BookingSyncService bookingReq : {}, bookingResp:{}", JsonUtils.writeObject2Json(bookingReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                // 无响应不等于未下单：请求可能已送达供应商而响应丢失，须交上游查单确证
                log.error("BookingSyncService doBooking 无响应，回报 UNKNOWN, orderId={}", bookingReq.getOrderId());
                return unknown(bookingReq, "供应商无响应，结果不确定，请查单确证");
            }

            BookingRespDTO bookingRespDTO = bookingRespConvert(t);

            if (bookingRespDTO == null) {
                log.error("BookingSyncService bookingRespConvert 返回空，回报 UNKNOWN, orderId={}, 原始响应={}",
                        bookingReq.getOrderId(), JsonUtils.writeObject2Json(t));
                return unknown(bookingReq, "供应商响应无法解析，结果不确定，请查单确证");
            }
            if (bookingRespDTO.getOutcome() == null) {
                // 实现方漏填三态即视为不确定，避免默认值悄悄退化成某一态
                log.error("BookingSyncService 实现未填 outcome，按 UNKNOWN 处理, orderId={}", bookingReq.getOrderId());
                bookingRespDTO.setOutcome(BookingOutcome.UNKNOWN);
            }
            if (bookingRespDTO.getOrderId() == null) {
                bookingRespDTO.setOrderId(bookingReq.getOrderId());
            }
            return bookingRespDTO;
        } catch (Exception e) {
            // 异常同样不足以断定未下单：连接在请求发出后中断，与请求根本没发出，在本地无从区分
            log.error("BookingSyncService 异常，回报 UNKNOWN, orderId={}", bookingReq.getOrderId(), e);
            return unknown(bookingReq, "下单过程异常，结果不确定，请查单确证：" + e.getClass().getSimpleName());
        }
    }

    private BookingRespDTO unknown(BookingReq bookingReq, String desc) {
        return BookingRespDTO.builder()
                .outcome(BookingOutcome.UNKNOWN)
                .orderId(bookingReq.getOrderId())
                .orderDesc(desc)
                .build();
    }

    public abstract T doBooking(BookingReq bookingReq);

    public abstract BookingRespDTO bookingRespConvert(T t);
}
