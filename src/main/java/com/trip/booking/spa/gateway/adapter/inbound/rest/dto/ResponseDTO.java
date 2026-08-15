package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User: zhe.hao
 * Date:
 * Time:
 */
@Getter
@Setter
@NoArgsConstructor
public class ResponseDTO<T> {

    public static final int STATUS_OK = 0;

    public static final int STATUS_FAIL = -1;

    private T result;
    private Boolean success;
    private String errorMsg;
    private Integer code;

    public ResponseDTO(int code, boolean success, String errorMsg) {
        this.code = code;
        this.success = success;
        this.errorMsg = errorMsg;
    }

    public ResponseDTO(int code, boolean success, T result) {
        this.code = code;
        this.success = success;
        this.result = result;
    }

    public ResponseDTO(int code, boolean success, String errorMsg, T result) {
        this.code = code;
        this.success = success;
        this.errorMsg = errorMsg;
        this.result = result;
    }


    public static <T> ResponseDTO success(T result) {
        return new ResponseDTO(STATUS_OK, true, result);
    }


    public static ResponseDTO error(int code, String errorMsg) {
        return new ResponseDTO(code, false, errorMsg);
    }

    /**
     * 未知原因错误
     *
     * @param errorMsg
     * @return
     */
    public static ResponseDTO error(String errorMsg) {
        return new ResponseDTO(STATUS_FAIL, false, errorMsg);
    }


}
