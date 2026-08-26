package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRespDTO {

    public String hotelId;
    public String productId;
    /**
     * 网关派生的稳定产品身份，标识"卖法"（等价类），跨查价不变。
     * 与 productId（供应商报价标识，各家腐性不一）互不替代：
     * 身份与令牌永不同字段。派生规则见 docs/product-identity.md R-1.1。
     */
    private String productKey;
    /**
     * productKey 的<b>全部成分</b>，供建档原样落库（R-2.7 / R-2.8）。
     *
     * <p><b>不出网关</b>：{@code @JsonIgnore}，也不进价格缓存
     * （{@code ProductRespCacheDTO} 无同名字段，{@code BeanUtils.copyProperties} 按名复制，
     * 自然不会带过去）。它是内部执行材料，对上游只暴露 {@link #productKey}。
     *
     * <p>为什么挂在出参 DTO 上：建档的入口是
     * {@code CatalogService.upsert(List<ProductRespDTO>)}，而成分只在查价组装那一刻
     * 由派生器算出。不挂在这里，建档就只能拿 {@link #meal}/{@link #cancelPolicy}
     * 重判一遍——那正是 R-2.8 要消灭的东西。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.trip.booking.spa.gateway.domain.product.ProductIdentity identity;
    public Integer supplierId;
    /**
     * 总价
     */
    private Integer totalPrice;
    /**
     * 总税费 expedia专用
     */
    private Integer totalTaxes;
    /**
     * 总房价 expedia专用
     */
    private Integer roomTotalPrice;
    /**
     * 酒店一次性收取费用 每日总价+酒店一次性收取费用=线上支付总价 expedia专用
     */
    private Integer stayPrice;
    /**
     * 线下支付金额 expedia专用
     */
    private Integer storePayPrice;
    /**
     * 线下支付金额币种
     */
    private String storePayCurrency;
    /**
     * 佣金
     */
    private Integer brokerage;
    /**
     * 报价币种（ISO 4217 大写三字码）：totalPrice/roomTotalPrice/totalTaxes/priceInfos
     * 等全部分值金额共用它。<b>本仓的唯一报价币种字段</b>——曾与恒空的 currency 并存
     * （写方为零、线上恒 null、cursor 的 SpaProductResp 也未声明），2026-08-26 收敛删除。
     * 到店付另有 {@link #storePayCurrency}（可与报价币种不同，非冗余）。
     */
    private String currencyType;
    /**
     * 产品基本信息
     */
    public ProductInfo productInfo;
    /**
     * 总价
     */
    public Room room;
    /**
     * 规则
     */
    public List<BookingRule> bookingRule;
    /**
     * 餐食
     */
    public Meal meal;
    /**
     * 取消政策
     */
    public List<CancelPolicy> cancelPolicy;
    /**
     * 价格
     */
    public List<PriceInfo> priceInfos;
    /**
     * 最大入住人数
     */
    private Integer maxOccupancy;
    /**
     * hotel_package-打包价 hotel_only-零售价
     */
    private String priceFlag;
    /**
     * 专属分销标识，可能是高佣金 true 是   false 否
     */
    private boolean distribution;
    /**
     * 床型选择信息
     */
    private List<BedCheckInfo> bedCheckInfos;

}
