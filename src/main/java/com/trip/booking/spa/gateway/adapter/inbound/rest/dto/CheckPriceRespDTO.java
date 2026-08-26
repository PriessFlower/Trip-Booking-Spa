
package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPriceRespDTO {

    /**
     * 验价结果分态，<b>上游必须先读本字段再读其余字段</b>。
     *
     * <p>仅 {@link CheckPriceOutcome#BOOKABLE} 时价格与 {@link #offerId} 才有意义。
     * 各态的含义与处置见 {@link CheckPriceOutcome}——尤其注意
     * {@link CheckPriceOutcome#RATE_DEAD} 与 {@link CheckPriceOutcome#SOLD_OUT} 的区别，
     * 前者该重新查价，后者该告知旅客满房。
     */
    private CheckPriceOutcome outcome;
    /**
     * 售卖价格
     */
    private Integer salePrice;
    /**
     * 预定价格
     */
    private Integer subPrice;
    /**
     * 佣金
     */
    private Integer brokerage;

    /**
     * 报价币种（ISO 4217 大写三字码）：salePrice/subPrice/brokerage/priceInfos 共用它，
     * 与查价响应的同名字段同义。此前验价金额是裸分值——两家在产都报 CNY 时上游猜得对，
     * USD 供应商（美团/飞猪/喜玩）进来后"这个数是什么币种"必须由本字段回答。
     */
    private String currencyType;
    /**
     * 剩余库存
     */
    private Integer remainRoomNum;
    /**
     * 验价信息。非 {@link CheckPriceOutcome#BOOKABLE} 时说明成因
     */
    private String message;

    private Integer totalPriceAfter;

    private Integer totalPriceBefore;

    /**
     * 报价句柄，由网关签发的不透明短 ID。
     *
     * <p>上游<b>原样存、原样回传、永不解析</b>：下单时置于 {@code BookingReq.offerId}。
     * 句柄背后的供应商凭据由网关自持，上游无需知道其形态，也无需知道供应商内部存在
     * rate、bed group、令牌这些概念。
     *
     * <p>句柄有时效。过期后下单会得到确定性失败，此时重新验价即可。
     */
    private String offerId;

    /**
     * {@link #offerId} 的剩余有效秒数。
     *
     * <p>显式给出，上游据此决定是直接下单还是先重新验价，不必自行约定一个「大概几分钟」。
     * 用相对秒数而非绝对时刻，是为了免受本服务与上游之间时钟偏差的影响。
     */
    private Long offerTtlSeconds;

    /**
     * 验价时点的退改条款，仅 {@link CheckPriceOutcome#BOOKABLE} 时有值。
     *
     * <p>与查价响应 {@code ProductRespDTO.cancelPolicy} 同结构、同口径，但<b>以验价时点
     * 为准</b>——查价与验价之间条款可能已变，而下单要认的是这一份。上游据此向旅客展示
     * "几点前可免费取消"，并按 R-5.3 存入订单契约快照。
     *
     * <p>解析不出时为空而非猜测值（R-5.4：不确定不许说成确定）。
     */
    private List<CancelPolicy> cancelPolicy;

    /**
     * 验价时点的每日价明细（分），仅 {@link CheckPriceOutcome#BOOKABLE} 时有值。
     *
     * <p>{@link #salePrice} 是其合计。上游做逐日分摊、跨日促销核对时需要明细——
     * 拿总价除以晚数在阶梯价场景下是错的。
     */
    private List<PriceInfo> priceInfos;

}
