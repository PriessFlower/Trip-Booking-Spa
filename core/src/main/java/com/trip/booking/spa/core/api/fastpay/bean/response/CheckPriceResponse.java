package com.trip.booking.spa.core.api.fastpay.bean.response;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;

import java.util.List;

/**
 * 验价反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/11/25
 * @since : 1.0
 **/
public class CheckPriceResponse implements BaseResponse {

    private String messageID = null;

    private List<SearchResponse.HotelAvail> hotelAvails = null;

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    public List<SearchResponse.HotelAvail> getHotelAvails() {
        return hotelAvails;
    }

    public void setHotelAvails(List<SearchResponse.HotelAvail> hotelAvails) {
        this.hotelAvails = hotelAvails;
    }

    @Override
    public boolean isSucc() {
        return false;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
