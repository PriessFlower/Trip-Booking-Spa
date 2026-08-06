package com.trip.booking.spa.core.api.didatravel.bean;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 道旅返回静态信息下载地址.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/10
 * @since : 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class UrlDTO implements BaseResponse {
    private String url;

    @Override
    public String toString() {
        return "UrlDTO{" +
                "url='" + url + '\'' +
                '}';
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
