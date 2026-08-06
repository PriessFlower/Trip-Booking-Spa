package com.trip.booking.spa.core.api.huitravel.bean;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;


public class HuiTravelBaseResponse implements BaseResponse {
    private String msg;

    private String code;

    public String getMsg() {
        return msg;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean isSucc() {
        return code.equals("0");
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }




}
