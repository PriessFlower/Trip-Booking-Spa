package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 一条报价（美团称"产品"）。
 *
 * <p>字段名保留 wire 原名（§4.2.3），包括官方参数表未列、实测报文里确有的那几个
 * （{@code immediateConfirm}、{@code endDateLocal}、{@code priceStd}/{@code penaltyStd}、
 * {@code ohMealTypeEnum}、{@code accessCode}）。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeituanGoods {

    /**
     * 产品 ID，即本家的报价码。<b>按易腐处理</b>：短期会跨住期复用，但拿今天的 id 去 cursor
     * 生产库的六月快照里查一个都不在，长期有效没有证据（R-4.2）。只进 OfferStore，禁止落库。
     */
    @JsonProperty("goodsId")
    private Long goodsId;

    @JsonProperty("goodsName")
    private String goodsName;

    @JsonProperty("goodsNameEn")
    private String goodsNameEn;

    @JsonProperty("hotelId")
    private Long hotelId;

    /** 物理房型 ID */
    @JsonProperty("realRoomId")
    private Long realRoomId;

    /** 产品来源：1 上单产品，2 直连产品 */
    @JsonProperty("goodsSource")
    private Integer goodsSource;

    @JsonProperty("mealType")
    private MeituanMealType mealType;

    /** 逐日价，单位<b>分</b>，口径为「每间每晚」 */
    @JsonProperty("priceModelList")
    private List<MeituanPriceModel> priceModelList;

    /** 均价（分），仅参考；本仓总价一律按逐日价求和，不用它 */
    @JsonProperty("averagePrice")
    private Long averagePrice;

    /** 1 不可取消；2 限时取消 */
    @JsonProperty("refundable")
    private Integer refundable;

    /** 阶梯退改。注意报价档的金额是<b>单间</b>口径，order.check 的是全部间数口径 */
    @JsonProperty("cpApply")
    private List<MeituanCpApply> cpApply;

    /** 1 即时确认；2 非即时确认（需酒店二次确认），本网关不卖 */
    @JsonProperty("confirmType")
    private Integer confirmType;

    /** 与 confirmType 同义的布尔位，实测两者一致；判据以 confirmType 为准，本字段仅作交叉核对 */
    @JsonProperty("immediateConfirm")
    private Boolean immediateConfirm;

    /** 报价入住人数 */
    @JsonProperty("quotedOccupancy")
    private Integer quotedOccupancy;

    /** 价格入住人数 */
    @JsonProperty("rateOccupancy")
    private Integer rateOccupancy;

    /** 最小入住年龄，-1 表示无限制 */
    @JsonProperty("minGuestAge")
    private Integer minGuestAge;

    /** 入住须知 */
    @JsonProperty("checkPolicy")
    private String checkPolicy;

    /** 是否即时确认（只卖这一档） */
    public boolean isInstantConfirm() {
        return Integer.valueOf(1).equals(confirmType);
    }

    /** 是否明确不可退 */
    public boolean isNonRefundable() {
        return Integer.valueOf(1).equals(refundable);
    }
}
