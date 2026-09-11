package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.domain.booking.BookingResult;

/**
 * 下单能力。入参出参是领域模型，不是对外 JSON——对外形状的翻译收在 ① 的 BookingMapping。
 * 继取消、查单之后第三个矫正依赖方向的能力面。
 */
public interface BookingSyncService {

    BookingResult booking(BookingCommand command);
}
