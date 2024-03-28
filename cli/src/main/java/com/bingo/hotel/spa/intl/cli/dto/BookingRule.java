package com.bingo.hotel.spa.intl.cli.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRule {
    public Integer serialCheckinMin;
    public Integer serialCheckinMax;
    public Integer roomCountMin;
    public Integer roomCountMax;
    public Integer earliestBookingDays;
    public String earliestBookingHours;
    public Integer latestBookingDays;
    public String latestBookingHours;
    public Integer isDaybreakBooking;
    public Date inStartDate;
    public Date inEndDate;
}
