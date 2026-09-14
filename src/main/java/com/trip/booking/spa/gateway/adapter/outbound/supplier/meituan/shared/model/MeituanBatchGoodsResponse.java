package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量查价响应（官方 hotel.oversea.batch.goods.rp）。
 *
 * <p>{@link #isEmptyResult()} 为真即"这批酒店这住期没有可售产品"，是<b>常规形态</b>不是异常。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeituanBatchGoodsResponse implements BaseResponse {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("partnerId")
    private Integer partnerId;

    @JsonProperty("result")
    private List<MeituanHotelGoods> result;

    @Override
    public boolean isSucc() {
        return Integer.valueOf(MeituanCodes.SUCCESS).equals(code);
    }

    @Override
    public boolean isEmptyResult() {
        return result == null || result.isEmpty();
    }

    /** 按酒店 id 取该家的产品袋；没有即 null（合批时一次问多家，回的顺序不保证） */
    public MeituanHotelGoods hotelOf(String hotelId) {
        if (isEmptyResult() || hotelId == null) {
            return null;
        }
        for (MeituanHotelGoods goods : result) {
            if (goods != null && goods.getHotelId() != null && hotelId.equals(String.valueOf(goods.getHotelId()))) {
                return goods;
            }
        }
        return null;
    }
}
