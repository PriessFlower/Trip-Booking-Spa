package com.trip.booking.spa.core.api.fastpay.bean.request;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 验价入参.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/25
 * @since : 1.0
 **/
@Builder
public class CheckPriceRequest {

    private String messageID;

    private String currency;

    private String checkIn;

    private String checkOut;

    private SearchRequest.Occupancy occupancy;

    private String hotelCode;

    private String productCode;

    private BigDecimal quantity;

    private LivecheckRequestParameters parameters;

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(String checkIn) {
        this.checkIn = checkIn;
    }

    public String getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(String checkOut) {
        this.checkOut = checkOut;
    }

    public SearchRequest.Occupancy getOccupancy() {
        return occupancy;
    }

    public void setOccupancy(SearchRequest.Occupancy occupancy) {
        this.occupancy = occupancy;
    }

    public String getHotelCode() {
        return hotelCode;
    }

    public void setHotelCode(String hotelCode) {
        this.hotelCode = hotelCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public LivecheckRequestParameters getParameters() {
        return parameters;
    }

    public void setParameters(LivecheckRequestParameters parameters) {
        this.parameters = parameters;
    }

    public static class LivecheckRequestParameters {
        private String countryOfResidence;

        private String nationality;

        public String getCountryOfResidence() {
            return countryOfResidence;
        }

        public void setCountryOfResidence(String countryOfResidence) {
            this.countryOfResidence = countryOfResidence;
        }

        public String getNationality() {
            return nationality;
        }

        public void setNationality(String nationality) {
            this.nationality = nationality;
        }
    }
}
