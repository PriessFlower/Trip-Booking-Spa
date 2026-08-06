package com.trip.booking.spa.core.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @Description 从缓存中查询价格
 * @Author lihao
 * @Date 2024/1/11 10:20
 **/
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PriceCacheReq {

    private String checkIn;//入住日期

    private String checkout;//离店日期

    private Integer supplierId;//供应商ID

    private String sHotelId;//供应商酒店列表

    private String sProductId;//供应商产品Id

}
