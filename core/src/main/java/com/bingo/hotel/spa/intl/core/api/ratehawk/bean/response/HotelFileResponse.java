package com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 请求文件返回.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/06
 * @since : 1.0
 **/

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class HotelFileResponse implements BaseResponse {

    private String last_update;
    private String url;

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
