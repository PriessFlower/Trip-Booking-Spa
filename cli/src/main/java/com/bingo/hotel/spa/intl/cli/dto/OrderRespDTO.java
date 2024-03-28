
package com.bingo.hotel.spa.intl.cli.dto;

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
public class OrderRespDTO {

    /**
     * 代理商订单ID
     */
    public String supplierOrderId;
    /**
     * 产品ID
     */
    public String supplierProductId;
    /**
     * 订单总价
     */
    public Integer totalPrice;
    /**
     * 结算价
     */
    public Integer settlePrice;
    /**
     * 预约下单时间
     */
    public String createTime;
    /**
     * 订单状态
     * 10 创建订单 create
     * 20 预定中 booking
     * 21 预定成功 book_suc
     * 22 预定失败 book_fail
     * 30 取消中 canceling
     * 31 取消成功 cancel_suc
     * 32 取消失败 cancel_fail
     */
    public Integer orderStatus;
    /**
     * 酒店确认号
     */
    public String confirmationNumber;

}
