package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;

public interface BookingSyncService {
    BookingRespDTO booking(BookingReq bookingReq);
}
