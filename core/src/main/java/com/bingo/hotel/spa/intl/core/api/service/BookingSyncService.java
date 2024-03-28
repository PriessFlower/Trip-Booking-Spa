package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.BookingRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.BookingReq;

public interface BookingSyncService {
    BookingRespDTO booking(BookingReq bookingReq);
}
