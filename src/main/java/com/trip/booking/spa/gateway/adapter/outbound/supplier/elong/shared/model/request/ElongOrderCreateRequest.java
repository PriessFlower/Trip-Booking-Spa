package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * hotel.order.create 的 Request 节点（字段依官方文档 + cursor 生产口径，2026-08-15 核对）。
 *
 * <p>要点：
 * <ul>
 *   <li>{@code AffiliateConfirmationId} 是供应商侧幂等键：同值不建新单、返回既有订单号
 *       （官方原话）；45 秒内同号重发报 H001043</li>
 *   <li>预付（PaymentType=Prepay）授信分销商必须 {@code IsGuaranteeOrCharged=true} 且
 *       <b>不得</b>携带任何卡信息，否则 H001039（cursor 生产口径）</li>
 *   <li>{@code LatestArrivalTime} 固定入住日 23:59:59——cursor 曾把 amap 的"最早到店"
 *       误接到此字段，艺龙在客人实际到店前按 no-show 处理（移植风险⑩）</li>
 *   <li>国际/港澳台产品必带七项会话凭证（hotelCode/supplierId/subSupplierId/
 *       shopperProductId/littleMajiaId/goodsUniqId/roomTypeId），全部取自验价句柄</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElongOrderCreateRequest {

    /** 我方订单号（分销商唯一单号）；供应商侧幂等键 */
    private String affiliateConfirmationId;

    private String hotelId;

    private String roomTypeId;

    private Long ratePlanId;

    /** yyyy-MM-dd */
    private String arrivalDate;

    private String departureDate;

    /** 固定 Prepay（预付），SelfPay 为现付产品，本仓不售 */
    private String paymentType;

    private Integer numberOfRooms;

    /** 入住客人数，须 ≥ 房间数 */
    private Integer numberOfCustomers;

    /** 订单总价（元）；必须与验价口径一致，不符报 H001084 */
    private BigDecimal totalPrice;

    private String currencyCode;

    /** yyyy-MM-dd HH:mm:ss，须晚于当前时间 */
    private String earliestArrivalTime;

    private String latestArrivalTime;

    /** NoNeed：无需向客人发确认短信（渠道单由渠道自行通知） */
    private String confirmationType;

    private Contact contact;

    private List<OrderRoom> orderRooms;

    /** 客人访问 IP，必填（风控），缺失报 H001012；无终端 IP 时用我方出口兜底 */
    @com.fasterxml.jackson.annotation.JsonProperty("CustomerIPAddress")
    private String customerIPAddress;

    private String littleMajiaId;

    private String goodsUniqId;

    private String hotelCode;

    private String supplierId;

    private String subSupplierId;

    private String shopperProductId;

    /** 国际单必填：成人数 */
    private Integer numberOfAdults;

    /** 国籍，默认 CN */
    private String nat;

    /** 预付授信必传 true；true 时不得携带卡信息 */
    private Boolean isGuaranteeOrCharged;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Contact {

        private String name;

        private String mobile;

        private String email;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrderRoom {

        /** 从 1 开始 */
        private Integer roomSequence;

        private List<Customer> customers;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Customer {

        private String name;

        private Boolean isChild;

        /** 国际单要求每客人带国籍（或全体不带），缺省 CN */
        private String nationality;
    }
}
