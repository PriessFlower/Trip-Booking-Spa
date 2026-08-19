package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
public class CheckPriceReq {
    @NonNull
    private Integer supplierId;//供应商ID

    private String sHotelId;//供应商酒店ID

    private String sProductId;//供应商产品Id

    /**
     * 网关派生的稳定产品身份（查价响应透出的 productKey，docs/product-identity.md R-1.1）。
     * 可选：携带时，若 sProductId 所指报价已不在（令牌死），网关可按它在现货中
     * 自动换等价新票（resolve ②）；不带则维持旧行为（RATE_DEAD）。
     */
    private String productKey;
    @NonNull
    private String checkIn;//入住日期
    @NonNull
    private String checkOut;//离店日期
    @NonNull
    private Integer roomNum;//房间数量
    /**
     * <b>客人所见价</b>（分）：上游实际展示给客人的该报价总价。<b>可选</b>。
     *
     * <p>唯一用途是 resolve 换票时的<b>尺子</b>——新票比客人所见贵多少算太贵
     * （容差双门 min(比例, 绝对帽)，issue #59）。取值优先级：
     * <ol>
     *   <li>调用方携带 → 用它（最准，这就是客人看到的数）；</li>
     *   <li>未携带 → 网关按<b>出价同一条路径</b>反查（见
     *       {@code ElongPriceServiceImpl#lookupTotalPriceFromCache}）；</li>
     *   <li>都拿不到 → 不换票（没有尺子就不量，R-1.6 宁可少卖不可卖错）。</li>
     * </ol>
     *
     * <p><b>为什么叫 seenPrice 而不是 totalPrice</b>：旧名字暗示"随便哪个总价都行"，
     * 2026-08-19 我据此错拿了产品详情快照里的总价（刷价那次的 1 晚价）当基准，而客人
     * 看的是按其查询区间累加的多晚价——基准小一个量级，多晚订单的换票全被误判超容差。
     * 名字必须说清"必须是客人看到的那个数"。旧名经 {@code @JsonAlias} 仍可接收，
     * 存量调用方不受影响。
     *
     * <p><b>为什么可选</b>：接入方未必持有价格（cursor 的验价 DTO 就没有价格字段）。
     * 它曾是必填（Lombok {@code @NonNull} → 反序列化即抛 → HTTP 400），把整条
     * spa# 票据验价打死。它承担的是"减少无谓变价"的体验优化，不是资损防线
     * （最后防线在渠道侧的变价上报），不该为它卡死链路。
     */
    @com.fasterxml.jackson.annotation.JsonAlias("totalPrice")
    private Integer seenPrice;

    private String planSession;

    private String sCityCode;

    private Integer adultCount;

    private Integer childNum; //儿童数

    private List<Integer> childAges; //儿童年龄

    private String priceFlag;//hotel_package-打包价 hotel_only-零售价

    private String language;//语言

    private String bedId;

    private String currency;//分销商币种
}
