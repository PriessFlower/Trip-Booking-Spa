package com.trip.booking.spa.core.api.fastpay.bean.request;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报价入参.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/22
 * @since : 1.0
 **/

@Builder
public class SearchRequest {
    private String messageID;

    private String currency;

    private String checkIn;

    private String checkOut;

    private List<Occupancy> occupancies;

    private List<String> hotelCodes;

    private List<String> areas;

    private List<String> zones;

    private List<String> tags;

    private AvailLocation location;

    private SearchParameters parameters;

    private HotelProduct product;

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

    public List<Occupancy> getOccupancies() {
        return occupancies;
    }

    public void setOccupancies(List<Occupancy> occupancies) {
        this.occupancies = occupancies;
    }

    public List<String> getHotelCodes() {
        return hotelCodes;
    }

    public void setHotelCodes(List<String> hotelCodes) {
        this.hotelCodes = hotelCodes;
    }

    public List<String> getAreas() {
        return areas;
    }

    public void setAreas(List<String> areas) {
        this.areas = areas;
    }

    public List<String> getZones() {
        return zones;
    }

    public void setZones(List<String> zones) {
        this.zones = zones;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public AvailLocation getLocation() {
        return location;
    }

    public void setLocation(AvailLocation location) {
        this.location = location;
    }

    public SearchParameters getParameters() {
        return parameters;
    }

    public void setParameters(SearchParameters parameters) {
        this.parameters = parameters;
    }

    public HotelProduct getProduct() {
        return product;
    }

    public void setProduct(HotelProduct product) {
        this.product = product;
    }

    @Builder
    public static class Occupancy {

        private BigDecimal adults;

        private BigDecimal children;

        private List<BigDecimal> childrenAges;

        public BigDecimal getAdults() {
            return adults;
        }

        public void setAdults(BigDecimal adults) {
            this.adults = adults;
        }

        public BigDecimal getChildren() {
            return children;
        }

        public void setChildren(BigDecimal children) {
            this.children = children;
        }

        public List<BigDecimal> getChildrenAges() {
            return childrenAges;
        }

        public void setChildrenAges(List<BigDecimal> childrenAges) {
            this.childrenAges = childrenAges;
        }
    }

    public static class AvailLocation {

        private String country;
        
        private String city;

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }

    public static class SearchParameters {
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

    public static class HotelProduct {
        private String roomTypeCode;

        private String ratePlanCode;

        private String mealPlanCode;

        public String getRoomTypeCode() {
            return roomTypeCode;
        }

        public void setRoomTypeCode(String roomTypeCode) {
            this.roomTypeCode = roomTypeCode;
        }

        public String getRatePlanCode() {
            return ratePlanCode;
        }

        public void setRatePlanCode(String ratePlanCode) {
            this.ratePlanCode = ratePlanCode;
        }

        public String getMealPlanCode() {
            return mealPlanCode;
        }

        public void setMealPlanCode(String mealPlanCode) {
            this.mealPlanCode = mealPlanCode;
        }
    }
}
