package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * HotelBookingSearch（查单）请求体（官方 booking-api/booking-search，2026-09-13 查阅）。
 * 官方标「推荐」的两种查法本仓都用：{@code SearchBy.BookingID}（道旅单号）与
 * {@code SearchBy.BookingInfo.ClientReference}（我方单号）——后者让 B5「我方单号足以定位」成立。
 * 日期范围等其余条件不接：本仓只按单号查。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidaBookingSearchRequest {

    @JsonProperty("Header")
    private DidaRequestHeader header;

    @JsonProperty("SearchBy")
    private SearchBy searchBy;

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SearchBy {

        @JsonProperty("BookingID")
        private String bookingId;

        @JsonProperty("BookingInfo")
        private BookingInfo bookingInfo;
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BookingInfo {

        @JsonProperty("ClientReference")
        private String clientReference;
    }
}
