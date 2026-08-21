package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * <b>申报总价</b>（元）；必须与验价口径一致，不符报 H001084。
     *
     * <p><b>结算按本字段走</b>——2026-07 月结对账单实证：艺龙订单 101000106416 的
     * 「艺龙卖价 / 分销商卖价 / 结算金额」三列均等于我方申报值。所以本字段不是
     * 「订单总价」这种中性叫法能概括的，它就是<b>我方应付金额</b>，故改名为申报价。
     *
     * <p>官方备注原文（cn-api-search-hotel_order_create，2026-08-21 核对）：
     * 「原币种价格 RatePlan的TotalRate * 房间数, 开通了结算价的分销商，此处应该传入
     * 结算价。<b>如果是国际分销商需要传入 sum(Rate) * 房间数</b>。」
     * 我方是国际分销商，故正确取值为 {@code sum(Rate) * 房间数}。
     *
     * <p><b>当前实现填的是 sum(Member)（会员价口径），比 sum(Rate) 高 1.4%~10%</b>
     * ——本次改名未动取值，修正待商务确认合约口径后单独进行。方向是我方多付。
     *
     * <p>另：本请求<b>缺 CustomerPrice</b>（销售给客人的最终总价格）。官方该字段必填列
     * 为 Y，且预付酒店按它开发票（FAQ 220）；对账单里「代理差额佣金」= 分销商卖价 −
     * 结算金额，正是我方毛利那一列。我方从未传，故 10 笔历史单该列恒为 0。
     */
    @JsonProperty("TotalPrice")
    private BigDecimal declaredTotal;

    /**
     * 逐日价，原样replay验价当次<b>被艺龙接受的</b>那一份。
     *
     * <p>官方说明：「每日价透传：用于每日金额校验，<b>避免出现订单部分退艺龙与合作方退款金额
     * 不一致现象发生</b>。DayPriceList 节点里每个 DayPrice 里的 Price 之和 * NumberOfRooms
     * = TotalPrice」。此前我方<b>完全没传</b>——句柄里存了却无人读，本请求类连字段都没有，
     * 于是部分退时两边金额本就可能对不上。
     *
     * <p><b>为什么必须用验价接受过的那一份、而不是 hotel.detail 的原值</b>：detail 与
     * hotel.data.validate 对同一产品的 {@code MinRate} 会给出不同值（2026-08-21 实测 detail
     * 偏高 0.01~0.33 元），拿 detail 原值下单等于把验价环节刚绕过的 {@code H001189} 重新引入
     * 建单环节——而建单是写操作、不可重试、失败态含"不确定"，代价比验价失败高一个量级。
     */
    @JsonProperty("DayPriceList")
    private List<DayPrice> dayPriceList;

    /** 逐日价子项。字段拼写与 hotel.data.validate 的 DayPrice 一致（Date / Price / MinRate） */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DayPrice {

        /** yyyy-MM-dd */
        private String date;

        /** 元 */
        private BigDecimal price;

        /** 元；国际必传、国内不允许传 */
        private BigDecimal minRate;
    }

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

    /** 儿童年龄（订单维度）；无儿童也传空数组——生产被接受的报文 3/3 均显式携带 [] */
    private List<Integer> childAges;

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

        /** 名（拼音/英文）；国际单生产被接受的报文 3/3 均带 First/LastName */
        private String firstName;

        /** 姓（拼音/英文） */
        private String lastName;

        private Boolean isChild;

        /** 国际单要求每客人带国籍（或全体不带），缺省 CN */
        private String nationality;
    }
}
