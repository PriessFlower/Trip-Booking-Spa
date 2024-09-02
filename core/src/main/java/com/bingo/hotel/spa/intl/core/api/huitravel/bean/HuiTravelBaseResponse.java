package com.bingo.hotel.spa.intl.core.api.huitravel.bean;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;

public class HuiTravelBaseResponse implements BaseResponse {
    private String msg;

    private String code;

    @Override
    public boolean isSucc() {
        return false;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
