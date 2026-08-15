package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;

import java.util.Date;
import java.util.List;

/**
 * 地域静态信息反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public class HotelFileResponse implements BaseResponse {

    private String method;
    private String href;
    private Date expires;

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getHref() {
        return href;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public Date getExpires() {
        return expires;
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
