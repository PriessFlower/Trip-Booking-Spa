package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 下单模板。
 *
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
