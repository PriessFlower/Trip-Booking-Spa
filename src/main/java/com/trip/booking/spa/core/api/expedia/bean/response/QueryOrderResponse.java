package com.trip.booking.spa.core.api.expedia.bean.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按我方业务单号反查订单的响应。
 *
 * <p><b>本类的唯一职责是把「查到了什么」收敛为三种确定语义</b>，见 {@link #getPresence()}。
 * 下单结果不确定时，上游据此判断能否安全地退款或补下单——把「查单失败」误当成
 * 「订单不存在」会导致重复下单，反之会让订单永久悬空。二者都是资损，故这三态不可合并。
 *
 * <p>Expedia 成功响应是 JSON 数组：非空表示订单存在，空数组表示确实没有这笔订单。
 * 失败响应是含 {@code type}/{@code message} 的对象。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryOrderResponse implements BaseResponse {

    /** 查单结果的确定性 */
    public enum Presence {
        /** 确证订单存在，{@link #itineraries} 非空 */
        FOUND,
        /** 确证订单不存在（Expedia 返回空数组）。仅此态可安全地重新下单 */
        NOT_FOUND,
        /** 查单本身失败或响应无法判读。<b>不可</b>据此重新下单，也不可据此退款 */
        INDETERMINATE
    }

    private Presence presence = Presence.INDETERMINATE;

    private List<Itinerary> itineraries;

    /** 查单失败时的错误类型 */
    private String type;

    /** 查单失败时的错误说明 */
    private String message;

    /**
     * 解析 Expedia 响应体。无法判读时一律落 {@link Presence#INDETERMINATE}，
     * 不猜测为「不存在」——猜错的代价是重复下单。
     */
    public static QueryOrderResponse of(String body) {
        QueryOrderResponse resp = new QueryOrderResponse();
        if (body == null || body.isBlank()) {
            resp.setMessage("查单响应为空");
            return resp;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            List<Itinerary> list = JsonUtils.decodeJson(trimmed,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Itinerary>>() {
                    });
            if (list == null) {
                resp.setMessage("查单响应数组无法解析");
                return resp;
            }
            resp.setItineraries(list);
            resp.setPresence(list.isEmpty() ? Presence.NOT_FOUND : Presence.FOUND);
            return resp;
        }
        // 对象形态即错误响应
        QueryOrderResponse error = JsonUtils.readValue(trimmed, QueryOrderResponse.class);
        if (error != null) {
            resp.setType(error.getType());
            resp.setMessage(error.getMessage());
        }
        return resp;
    }

    /** 查单调用本身是否可判读；注意「订单不存在」也算成功查到了结论 */
    @Override
    public boolean isSucc() {
        return presence != Presence.INDETERMINATE;
    }

    @Override
    public boolean isEmptyResult() {
        return presence == Presence.NOT_FOUND;
    }

    /** 首个行程；仅在 {@link Presence#FOUND} 时有意义 */
    public Itinerary firstItinerary() {
        return itineraries == null || itineraries.isEmpty() ? null : itineraries.get(0);
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Itinerary {
        private String itinerary_id;
        private String affiliate_reference_id;
        private String email;
        private List<CreateOrderResponse.Room> rooms;
    }
}
