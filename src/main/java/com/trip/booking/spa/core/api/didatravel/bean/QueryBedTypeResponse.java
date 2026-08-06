package com.trip.booking.spa.core.api.didatravel.bean;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 查询床型类型返参.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/15
 * @since : 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class QueryBedTypeResponse implements BaseResponse {

    private GetBedTypeListRSSuccess Success;

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
