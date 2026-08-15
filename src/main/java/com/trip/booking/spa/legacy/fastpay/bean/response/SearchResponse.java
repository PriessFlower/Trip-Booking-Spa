package com.trip.booking.spa.legacy.fastpay.bean.response;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 验价反参
 */

public class SearchResponse implements BaseResponse {

    private String messageID;

    private List<HotelAvail> hotelAvails;

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    public List<HotelAvail> getHotelAvails() {
        return hotelAvails;
    }

    public void setHotelAvails(List<HotelAvail> hotelAvails) {
        this.hotelAvails = hotelAvails;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class HotelAvail {

        private HotelInfo hotelInfo;

        private List<AvailRoomRate> availRoomRates;

        public HotelInfo getHotelInfo() {
            return hotelInfo;
        }

        public void setHotelInfo(HotelInfo hotelInfo) {
            this.hotelInfo = hotelInfo;
        }

        public List<AvailRoomRate> getAvailRoomRates() {
            return availRoomRates;
        }

        public void setAvailRoomRates(List<AvailRoomRate> availRoomRates) {
            this.availRoomRates = availRoomRates;
        }
    }

    public static class HotelInfo {

        private String name;

        private String code;

        private DataObject category;

        private DataObject type;

        private String specialNotes;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public DataObject getCategory() {
            return category;
        }

        public void setCategory(DataObject category) {
            this.category = category;
        }

        public DataObject getType() {
            return type;
        }

        public void setType(DataObject type) {
            this.type = type;
        }

        public String getSpecialNotes() {
            return specialNotes;
        }

        public void setSpecialNotes(String specialNotes) {
            this.specialNotes = specialNotes;
        }
    }

    public static class DataObject {

        private String name;

        private String code;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class AvailRoomRate {

        private String reservationToken;

        private String productCode;

        private Integer inventory;

        private String roomCode;

        private String roomName;

        private String ratePlanCode;

        private String ratePlanName;

        private String mealPlanCode;

        private String mealPlanName;

        private BigDecimal totalPrice;

        private BigDecimal publicPrice;

        private Boolean priceBinding;

        private BigDecimal commission;

        private String currency;

        private Boolean promo;

        private CancellationPolicy cancellationPolicy;

        public String getReservationToken() {
            return reservationToken;
        }

        public void setReservationToken(String reservationToken) {
            this.reservationToken = reservationToken;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public Integer getInventory() {
            return inventory;
        }

        public void setInventory(Integer inventory) {
            this.inventory = inventory;
        }

        public String getRoomCode() {
            return roomCode;
        }

        public void setRoomCode(String roomCode) {
            this.roomCode = roomCode;
        }

        public String getRoomName() {
            return roomName;
        }

        public void setRoomName(String roomName) {
            this.roomName = roomName;
        }

        public String getRatePlanCode() {
            return ratePlanCode;
        }

        public void setRatePlanCode(String ratePlanCode) {
            this.ratePlanCode = ratePlanCode;
        }

        public String getRatePlanName() {
            return ratePlanName;
        }

        public void setRatePlanName(String ratePlanName) {
            this.ratePlanName = ratePlanName;
        }

        public String getMealPlanCode() {
            return mealPlanCode;
        }

        public void setMealPlanCode(String mealPlanCode) {
            this.mealPlanCode = mealPlanCode;
        }

        public String getMealPlanName() {
            return mealPlanName;
        }

        public void setMealPlanName(String mealPlanName) {
            this.mealPlanName = mealPlanName;
        }

        public BigDecimal getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
        }

        public BigDecimal getPublicPrice() {
            return publicPrice;
        }

        public void setPublicPrice(BigDecimal publicPrice) {
            this.publicPrice = publicPrice;
        }

        public Boolean getPriceBinding() {
            return priceBinding;
        }

        public void setPriceBinding(Boolean priceBinding) {
            this.priceBinding = priceBinding;
        }

        public BigDecimal getCommission() {
            return commission;
        }

        public void setCommission(BigDecimal commission) {
            this.commission = commission;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Boolean getPromo() {
            return promo;
        }

        public void setPromo(Boolean promo) {
            this.promo = promo;
        }

        public CancellationPolicy getCancellationPolicy() {
            return cancellationPolicy;
        }

        public void setCancellationPolicy(CancellationPolicy cancellationPolicy) {
            this.cancellationPolicy = cancellationPolicy;
        }
    }

    public static class CancellationPolicy {

        private String code;

        private String description;
        
        private Boolean cancellable;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getCancellable() {
            return cancellable;
        }

        public void setCancellable(Boolean cancellable) {
            this.cancellable = cancellable;
        }
    }
}