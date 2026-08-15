package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * hotel.detail 响应里的售卖产品（RatePlan）。
 *
 * <p>两个拼写陷阱（cursor 踩过、抓包实证）：
 * <ul>
 *   <li>{@code Littlemajiaid}：JSON 键是小写混写，PascalCase 策略不会自动命中，
 *       必须显式 {@code @JsonProperty}；漏映射的下场是验价报 H001197（缺马甲）</li>
 *   <li>{@code meals}：JSON 键是全小写</li>
 * </ul>
 *
 * <p>GoodsUniqId + littleMajiaId 是<b>会话级易腐凭证</b>（SupplierIdentityProfile.ELONG）：
 * 只随本响应在内存流转或短 TTL 进 OfferStore，禁止落库（R-2.1）。
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElongRatePlan {

    private Long ratePlanId;

    private String ratePlanName;

    /** 产品总开关；false 一律不可售 */
    private Boolean status;

    /** 库存；≤0 不可订 */
    private Integer currentAlloment;

    private Boolean instantConfirmation;

    private String paymentType;

    /** 总价（元） */
    private BigDecimal totalRate;

    private BigDecimal averageRate;

    private String currencyCode;

    /** 房型锚（如 "0047"）——等价判定与 productKey 的 supplierRoomId 用它，不是外层 Room.RoomId */
    private String roomTypeId;

    /** 报价码（会话级易腐，见类 javadoc） */
    private String goodsUniqId;

    /** 促销马甲（会话级易腐）；JSON 键拼写见类 javadoc */
    @JsonProperty("Littlemajiaid")
    private String littleMajiaId;

    /** 餐食：{@code meals.dayMealTable[].breakfastShare}，逐日份数；原样接住交规范化钩子解析 */
    @JsonProperty("meals")
    private JsonNode meals;

    private List<ElongNightlyRate> nightlyRates;

    /** 预付取消规则（阶梯）；hotel.detail 常无此字段，原样接住交规范化钩子解析 */
    private JsonNode prepayResult;

    private String hotelCode;

    private String supplierId;

    private String subSupplierId;

    private String shopperProductId;

    private Integer roomMaxPax;

    private Integer adultOccupancyPerRoom;
}
