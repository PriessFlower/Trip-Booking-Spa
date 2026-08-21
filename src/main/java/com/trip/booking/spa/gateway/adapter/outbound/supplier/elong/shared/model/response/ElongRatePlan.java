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

    /**
     * <b>房量限额</b>——"最多允许订几间"，不是"还剩几间"。<b>0 表示不限，不是无房。</b>
     *
     * <p>官方定义（open.elong.com/doc/info/cn-api-search-hotel_detail，2026-08-21 浏览器直读）：
     * 「入住时间内不能超售的最小值。国内：当大于0小于5时，表示目前仅剩的房量，<b>0 表示房量充足</b>，
     * 最少有1间可以预定，多间预定可能会失败；国际：<b>0 或 999 或 9999 表示房量充足</b>，
     * 最少有1间可以预定，多间预定可能会失败」。
     *
     * <p><b>此前此处注释写「≤0 不可订」，与官方相反</b>，并据此在三个地方否决了产品：
     * {@code isOnSale}（查价出报的筛子 + resolve 候选筛选）与验价的 SOLD_OUT 判断。
     * 0 被当成无房 → 明明能订的房报售罄，方向是<b>少卖</b>。
     * 现网未踩到：2026-08-21 实测 24 家酒店 × 3 个日期共 4,239 个报价，
     * <b>0 与 null 各 0 条</b>（国际用真实数字或 9999，9999 占 2.3%），0 只出现在国内口径。
     *
     * <p><b>因此本字段不再参与"有没有房"的判定</b>：官方对每种口径都写明「最少有 1 间可以预定」，
     * 而我方一次只订 1 间（{@code NumberOfRooms=1}），只要 {@code Status} 在售，房必然有。
     * 改成"把 0 当充足"是不够的——国内 1~4 是真实房量、0 是充足；国际 0/999/9999 都是充足，
     * 两套口径不同，而我方目前没有境内外这一维（清单全为国际酒店），照着改容易再错一次。
     *
     * <p><b>多间预订（{@code NumberOfRooms > 1}）时才需要它</b>，官方两套口径都提示"多间预定可能会失败"。
     * 届时必须按境内外分别解读，并把 0/999/9999 当哨兵值而非数量。旁证：2026-08-21 实测
     * 「1 晚 1 人 2 间」的 {@code 7010 不可订} 率是单间的约 4 倍，与"多间可能失败"吻合。
     */
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

    /**
     * 预付取消规则（阶梯）；原样接住交规范化钩子解析。
     *
     * <p>此处原写「hotel.detail 常无此字段」，与生产事实相反，已更正：2026-08-21 按刷价出报
     * 口径实测（8.4h 建档日志，562 万条次），仅 <b>11.7%</b> 因餐食或退改任一为 UNKNOWN 而
     * 未入档案，即绝大多数产品在本字段即可解出完整阶梯（截止时间与罚金）。两个成因目前合成
     * 一个计数，故退改单独的缺失率只能给上界 ≤11.7%。
     *
     * <p>解析不出时 {@code CancelClass} 取 UNKNOWN：照常参与实时链路，但不进档案（R-5.4）。
     * 该口径不可外推到验价——验价另有 {@code interValidateInfo.CancelPolicyList}，两者以
     * 验价时点为准（见 {@code ElongPriceServiceImpl.buildBookableResp}）。
     */
    private JsonNode prepayResult;

    private String hotelCode;

    private String supplierId;

    private String subSupplierId;

    private String shopperProductId;

    private Integer roomMaxPax;

    private Integer adultOccupancyPerRoom;
}
