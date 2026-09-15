package com.trip.booking.spa.gateway.domain.product;

import com.trip.booking.spa.gateway.domain.product.BedCheckInfo;
import com.trip.booking.spa.gateway.domain.product.BookingRule;
import com.trip.booking.spa.gateway.domain.product.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.Meal;
import com.trip.booking.spa.gateway.domain.product.PriceInfo;
import com.trip.booking.spa.gateway.domain.product.ProductInfo;
import com.trip.booking.spa.gateway.domain.product.Room;
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
public class Product {

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
     * <p><b>整体不出网关</b>：{@code @JsonIgnore}，也不进价格缓存
     * （{@code ProductRespCacheDTO} 无同名字段，{@code BeanUtils.copyProperties} 按名复制，
     * 自然不会带过去）。它是内部执行材料——账号与供应商酒店/房型 id 一律不出网关。
     * 上游拿到的是 {@link #productKey} 加三个等价类成分（{@link #stampEquivalence}，R-2.10）。
     *
     * <p>为什么挂在出参 DTO 上：建档的入口是
     * {@code CatalogService.upsert(List<Product>)}，而成分只在查价组装那一刻
     * 由派生器算出。不挂在这里，建档就只能拿 {@link #meal}/{@link #cancelPolicy}
     * 重判一遍——那正是 R-2.8 要消灭的东西。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.trip.booking.spa.gateway.domain.product.ProductIdentity identity;

    /**
     * 等价类成分之一：餐食规范形（如 {@code B1L0D0}），未知为 {@code UNKNOWN}（R-2.10）。
     *
     * <p>与 {@link #meal} 不是一回事：那是给人看的原始描述，各家写法不一；这个是派生器
     * 归一化后的判定结果，是 productKey 的成分。上游按它分组，<b>不得自行重判</b>（R-2.8 同理）。
     */
    private String mealSignature;

    /** 等价类成分之一：退改粗分类（{@code CancelClass} 名），未知为 {@code UNKNOWN}（R-2.10） */
    private String cancelClass;

    /** 等价类成分之一：占用规范串（如 {@code 2}、{@code 2-9,4}）（R-2.10） */
    private String occupancy;

    /**
     * 把等价类成分从 {@link #identity} 拓到出参字段上（R-2.10）。<b>实时查价这条路用它</b>；
     * 走缓存那条路的成分来自档案表（成分是稳定信息，不进 Redis，R-2.6）。
     *
     * <p>收在查价模板里而不是让各家自己填：各家自己填必然漏一家，而漏了不报错，
     * 只是上游那几个字段恒空。
     *
     * <p>只拓三个成分，<b>不拓</b>账号、供应商酒店/房型 id：那些是内部执行材料，
     * 且凑齐就能反推 productKey，等于把身份发号权交出去（R-1.5）。
     */
    public void stampEquivalence() {
        if (identity == null) {
            return;
        }
        this.mealSignature = identity.mealSignature();
        this.cancelClass = identity.cancelClass();
        this.occupancy = identity.occupancy();
    }
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
