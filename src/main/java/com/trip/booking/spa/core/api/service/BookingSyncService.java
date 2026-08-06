package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.dto.BookingRespDTO;
import com.trip.booking.spa.core.api.request.BookingReq;

public interface BookingSyncService {
    BookingRespDTO booking(BookingReq bookingReq);
}
