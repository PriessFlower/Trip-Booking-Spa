
package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
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

    /**
     * 取消违约金，单位<b>分</b>，与契约内其余金额同单位；币种见 {@link #cancelFeeCurrency}。
     *
     * <p>仅当 {@link #penaltySource} 非 NONE 时有值。此前艺龙把罚金拼进中文 message
     * （"取消已受理，违约金 X 元"，单位还是元），上游要拿只能正则中文串——本字段是替代。
     */
    private Long cancelFee;

    /** 违约金币种，ISO 4217 大写三字码。与 {@link #cancelFee} 同生同灭 */
    private String cancelFeeCurrency;

    /**
     * 罚金来源：FIELD（供应商字段直接给出）/ POLICY_DERIVED（按验价时点政策推算）/
     * NONE（无从得知）。<b>NONE 不是 0，更不是免费取消</b>——各家给不给罚金离散度极大
     * （cursor 九家里五家的取消响应不带罚金），上游必须按来源分流处置。
     */
    private String penaltySource;

    /** 供应商原生错误码，判 FAILED 时供辨识（与 BookingRespDTO.supplierErrorCode 同义） */
    private String supplierErrorCode;

    /**
     * 失败成因档，可空；目前唯一取值 {@code AUTH_CONFIG}——我方凭据/配置病
     * （session 过期、签名错、出口 IP 不在白名单、必填配置缺失）。
     * 上游据此<b>不归因供应商、不拉黑、不判无货</b>，等我方修复后再试；
     * 网关侧已同步告警（[auth-config] 日志 + supplier_auth_config 指标）。
     */
    private String failureKind;

}
