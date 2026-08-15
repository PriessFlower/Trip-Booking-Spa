package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

/**
 * 取消单个房间的响应。
 *
 * <p><b>成功时 Expedia 返回 204 且响应体为空</b>，故本模型的常态就是「什么都没有」。
 * 失败时才返回错误对象（{@code type} + {@code message}）。
 *
 * <p>因此判定成功不能看响应体是否有内容——那样会把成功判成失败。真正的判据是状态码，
 * 而状态码由 {@code ResponseResult.code} 承载，本模型只负责在失败时把原因解析出来。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CancelRoomResponse implements BaseResponse {

    /** Expedia 的错误分类，如 order_not_found、post_stay_cancellation_not_allowed */
    private String type;

    /** 错误说明原文 */
    private String message;

    /**
     * 由响应体构造。空体视为成功（204 的常态），非空体按错误对象解析。
     *
     * <p>解析失败不抛异常：此时 type 与 message 均为空，调用方据状态码判读，
     * 并因无法确知原因而落入 UNKNOWN——这比抛异常更贴合三态契约。
     */
    public static CancelRoomResponse of(String body) {
        CancelRoomResponse resp = new CancelRoomResponse();
        if (!StringUtils.hasText(body)) {
            return resp;
        }
        try {
            CancelRoomResponse parsed = JsonUtils.readValue(body.trim(), CancelRoomResponse.class);
            if (parsed != null) {
                resp.setType(parsed.getType());
                resp.setMessage(parsed.getMessage());
            }
        } catch (Exception ignored) {
            // 解析不出就留空，由调用方按状态码判读
        }
        return resp;
    }

    /**
     * 本模型不承担成功判定——成功与否以 HTTP 状态码为准（204 无体即成功）。
     * 此处恒返回 true，避免 BaseHttpAccess 因「响应体为空」而误判失败。
     */
    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return !StringUtils.hasText(type) && !StringUtils.hasText(message);
    }
}
