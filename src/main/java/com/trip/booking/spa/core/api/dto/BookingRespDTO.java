
package com.trip.booking.spa.core.api.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRespDTO {

    /**
     * 代理商订单号
     */
    private String sOrderId;
    /**
     * BG订单号
     */
    private String orderId;
    /**
     * 代理商订单状态
     */
    private boolean sOrderStatus;
    /**
     * 订单详情
     */
    private String orderDesc;

}
