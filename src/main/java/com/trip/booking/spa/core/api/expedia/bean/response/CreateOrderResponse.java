package com.trip.booking.spa.core.api.expedia.bean.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Expedia Rapid 下单响应。
 *
 * <p>Rapid 用<b>同一个 HTTP 200 通道</b>返回成功与业务失败两种形态：
 * 成功含 {@code itinerary_id}，失败含 {@code type} 与 {@code message}。
 * 故本类同时承载两者，由 {@link #isSucc()} 区分。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderResponse implements BaseResponse {

    /** 成功时的 Expedia 订单号 */
    private String itinerary_id;

    /** 我方业务单号回显 */
    private String affiliate_reference_id;

    private List<Room> rooms;

    /** 业务错误类型，非空即表示本次下单被拒 */
    private String type;

    /** 业务错误说明 */
    private String message;

    /**
     * 成功的判据是拿到订单号，而非「没有错误」——两者在响应撕裂时可能同时出现，
     * 此时以有无订单号为准，避免把已成立的订单判成失败。
     */
    @Override
    public boolean isSucc() {
        return itinerary_id != null && !itinerary_id.isBlank();
    }

    @Override
    public boolean isEmptyResult() {
        return !isSucc() && (type == null || type.isBlank());
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Room {
        private String given_name;
        private String family_name;
        private String status;
        /** 酒店确认号，旅客到店核对用 */
        private ConfirmationId confirmation_id;
        private Links links;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfirmationId {
        private String expedia;
        private String property;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Links {
        private Link cancel;
        private Link retrieve;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Link {
        private String method;
        private String href;
    }
}
