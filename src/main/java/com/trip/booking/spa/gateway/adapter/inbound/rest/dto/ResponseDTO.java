package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /**
     * 分态。目前只有查价端点填充
     * （{@link com.trip.booking.spa.gateway.domain.booking.PricingOutcome} 的名字）——
     * 查价的 result 是一个列表，分态无处安放，只能落在信封上；
     * 验价等端点的分态在各自的 result 对象里，不用这个字段。
     *
     * <p>为空时不序列化，其余端点的响应形状不变。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String outcome;

    /** 链式填分态，便于在 return 处一行写完 */
    public ResponseDTO<T> withOutcome(Enum<?> outcome) {
        this.outcome = outcome == null ? null : outcome.name();
        return this;
    }

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
