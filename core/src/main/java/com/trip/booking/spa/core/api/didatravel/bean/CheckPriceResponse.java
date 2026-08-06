package com.trip.booking.spa.core.api.didatravel.bean;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 查价接口返回.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/11
 * @since : 1.0
 **/

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CheckPriceResponse implements BaseResponse {

    private PriceSearchResponseSuccess Success;

    private ErrorType Error;

    @Override
    public boolean isSucc() {
        return null == Success ? false : true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
