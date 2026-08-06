package com.trip.booking.spa.core.api.common.asynchttp;

import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpStatus;

/**
 * 响应结果
 * @author zhe.hao
 *
 * @param <T>
 */
@Getter
@Setter
public class ResponseResult<T extends BaseResponse> {

    private int httpStatus;//http状态码
    private String origData;//原始数据
    private T data;//解析结果

    public ResponseResult(int httpStatus, String origData) {
        this.httpStatus = httpStatus;
        this.origData = origData;
    }

    public ResponseResult(int httpStatus, String origData, T data) {
        this.httpStatus = httpStatus;
        this.origData = origData;
        this.data = data;
    }

    public ResponseResult(String origData, T data) {
        this.httpStatus = HttpStatus.SC_OK;
        this.origData = origData;
        this.data = data;
    }

    public ResponseResult(T data) {
        this.httpStatus = HttpStatus.SC_OK;
        this.data = data;
    }

    /**
     * 接口是否请求成功（包括网络和业务）
     * @return
     */
    public boolean isSucc() {
        return this.httpStatus == HttpStatus.SC_OK && data != null && data.isSucc();
    }

}
