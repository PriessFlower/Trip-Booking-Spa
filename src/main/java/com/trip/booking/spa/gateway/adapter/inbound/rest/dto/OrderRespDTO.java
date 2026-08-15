
package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
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
     * 查单结果三态，<b>上游必须先读本字段再读其余字段</b>。
     * 仅 {@link OrderPresence#FOUND} 时订单信息字段才有意义。
     */
    public OrderPresence presence;

    /**
     * 查单未成功时的说明（{@code NOT_FOUND} / {@code INDETERMINATE} 场景）。
     */
    public String message;

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
     *
     * <p><b>无法映射时留空，不得取默认值。</b>供应商新增或改写状态取值时，
     * 猜一个默认值会把未知状态说成已知——上游据此做的每一步都是错的。
     * 留空并保留 {@link #supplierOrderStatus} 原文，让未知就表现为未知。
     */
    public Integer orderStatus;
    /**
     * 供应商返回的状态原文，未做映射。
     *
     * <p>存在的意义是在 {@link #orderStatus} 映射不上时仍能保留事实，
     * 供人工判读与补映射，避免"未知状态被默认值吞掉"。
     */
    public String supplierOrderStatus;
    /**
     * 酒店确认号
     */
    public String confirmationNumber;

}
