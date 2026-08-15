package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import lombok.*;

/**
 * 下单结果。本服务是供应商网关，订单归上游所有，故本对象只回报「供应商侧发生了什么」，
 * 不含订单状态、退款等归上游裁决的字段。
 *
 * <p>调用方<b>必须</b>按 {@link #outcome} 三态分支处理，见 {@link BookingOutcome}。
 * 尤其不得把 {@link BookingOutcome#UNKNOWN} 当失败处理。
 *
 * <p><b>关于 {@code sOrderId} 等字段上的 {@code @JsonProperty}</b>：字段名以单个小写字母
 * 开头、紧跟大写字母时，Jackson 默认会把前导大写串一并压平，{@code sOrderId} 于是被序列化成
 * {@code sorderId}——而入站方向因大小写不敏感仍能认 {@code sOrderId}，两个方向不对称，
 * 只看 Java 字段名发现不了。实测（2026-08-11 沙箱）下单成功的响应里确实是 {@code sorderId}，
 * 上游若按字段名取值必然拿到空。故此处显式钉住线上字段名，不交给命名推断。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRespDTO {

    /**
     * 下单结果三态，必填。调用方的分支判据只应依赖本字段。
     */
    private BookingOutcome outcome;

    /**
     * 上游订单号，原样回显，便于调用方对齐请求与响应
     */
    private String orderId;

    /**
     * 供应商订单号（Expedia 为 itinerary_id）。
     * outcome=SUCCESS 时必然有值；UNKNOWN 时可能为空，需由调用方查单补齐。
     */
    @JsonProperty("sOrderId")
    private String sOrderId;

    /**
     * 酒店确认号，供应商返回则填充。用于旅客到店核对，缺失不影响订单成立。
     */
    @JsonProperty("sConfirmationNumber")
    private String sConfirmationNumber;

    /**
     * 供应商原始错误码。留存原始值而不做归一，是为了事后能凭它复核判据是否正确
     * ——本服务把哪些码判成 FAILED、哪些判成 UNKNOWN，是需要随线上表现持续校准的。
     */
    private String supplierErrorCode;

    /**
     * 供应商原始错误信息
     */
    private String supplierErrorMessage;

    /**
     * 人可读的结果说明，仅用于日志与排障，禁止作为分支判据
     */
    private String orderDesc;
}
