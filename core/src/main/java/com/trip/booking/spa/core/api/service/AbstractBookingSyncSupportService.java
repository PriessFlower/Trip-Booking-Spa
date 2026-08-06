package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.BookingRespDTO;
import com.trip.booking.spa.cli.seq.BookingReq;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractBookingSyncSupportService<T> implements BookingSyncService {

    @Override
    public BookingRespDTO booking(BookingReq bookingReq) {
        try {
            T t = doBooking(bookingReq);

            log.info("BookingSyncService bookingReq : {}, bookingResp:{}", JsonUtils.writeObject2Json(bookingReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                log.error("BookingSyncService doBooking is null bookingReq : {}", JsonUtils.writeObject2Json(bookingReq));
                return null;
            }

            BookingRespDTO bookingRespDTO = bookingRespConvert(t);

            if (bookingRespDTO == null) {
                log.error("BookingSyncService bookingRespConvert is null T : {}", JsonUtils.writeObject2Json(t));
            }

            return bookingRespDTO;
        } catch (Exception e) {
            log.error("BookingSyncService is error e:", e);
            return null;
        }

    }

    public abstract T doBooking(BookingReq bookingReq);

    public abstract BookingRespDTO bookingRespConvert(T t);

}
