package com.bingo.hotel.spa.intl.core.push.fliggy.bean;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FliggyPushResponse implements BaseResponse {
    private Boolean success;
    private Integer code;
    private String message;

    @Override
    public boolean isSucc() {
        return success;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
