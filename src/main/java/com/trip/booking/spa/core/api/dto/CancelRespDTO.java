
package com.trip.booking.spa.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.core.api.common.enums.CancelOutcome;
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
     * 取消结果三态。上游必须据此分流：仅 {@link CancelOutcome#SUCCESS} 可视为已取消，
     * {@link CancelOutcome#UNKNOWN} 必须查单确证后再作处置。
     *
     * <p>该字段先于 sOrderStatus 判读；sOrderStatus 为兼容旧上游保留，语义较粗。
     */
    private CancelOutcome outcome;

    /**
     * 供上游展示或记录的原因说明。判 FAILED 或 UNKNOWN 时必填，说明为何如此判定。
     */
    private String message;

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
     *
     * <p>与 {@link #sOrderId} 同理需显式钉住线上字段名：Jackson 的命名推断会把
     * {@code sOrderStatus} 压成 {@code sorderStatus}。此前取消未实现，该字段从未被真正
     * 序列化，故一直未暴露。
     */
    @JsonProperty("sOrderStatus")
    private Integer sOrderStatus;
    /**
     * 订单详情
     */
    private String orderDesc;

}
