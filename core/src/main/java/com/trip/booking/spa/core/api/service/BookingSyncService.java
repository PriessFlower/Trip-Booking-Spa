package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.BookingRespDTO;
import com.trip.booking.spa.cli.seq.BookingReq;

public interface BookingSyncService {
    BookingRespDTO booking(BookingReq bookingReq);
}
