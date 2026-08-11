
package com.trip.booking.spa.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRespDTO {

    /**
     * 代理商订单号
     */
    /** 见 BookingRespDTO 类注释：显式钉住线上字段名，避免被 Jackson 命名推断压成 sorderId */
    @JsonProperty("sOrderId")
    private String sOrderId;
    /**
     * BG订单号
     */
    private String orderId;
    /**
     * 代理商订单状态
     * 0 取消成功
     * 1 取消中
     * 2 取消失败
     */
    private Integer sOrderStatus;
    /**
     * 订单详情
     */
    private String orderDesc;

}
