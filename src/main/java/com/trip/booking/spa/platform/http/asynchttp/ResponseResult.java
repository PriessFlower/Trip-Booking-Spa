package com.trip.booking.spa.platform.http.asynchttp;

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
     * 接口是否请求成功（包括网络和业务）。
     *
     * <p><b>判据是 2xx 而非 200。</b>此前写死 {@code == 200}，凡返回 201/202/204 的接口
     * 一律被判失败：带重试的会白发一次请求，不带重试的会把成功上报成失败或「不确定」。
     *
     * <p>该缺陷已实际发生：Expedia 取消房间成功返回 <b>204 无响应体</b>，被判失败后触发
     * 重试，第二次 DELETE 得到 400「Room is already cancelled」，最终把一次成功的取消
     * 上报成 UNKNOWN——而查单显示房间确已取消。当时以「取消不重试」止血，此处方为根治。
     *
     * <p>放宽只会让判定更准，不会放过失败：业务层的 {@link BaseResponse#isSucc()} 仍在
     * 其后把关，供应商在 2xx 响应里塞业务错误时依旧判失败。
     */
    public boolean isSucc() {
        return isHttpSucc() && data != null && data.isSucc();
    }

    /** HTTP 层是否成功。2xx 全部视为成功，含 201 Created 与 204 No Content */
    private boolean isHttpSucc() {
        return httpStatus >= HttpStatus.SC_OK && httpStatus < HttpStatus.SC_MULTIPLE_CHOICES;
    }

}
