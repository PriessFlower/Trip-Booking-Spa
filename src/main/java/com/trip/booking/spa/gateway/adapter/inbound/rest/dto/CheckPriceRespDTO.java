
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

    private List<String> bedTypeCode;

    private String plansId;

}
