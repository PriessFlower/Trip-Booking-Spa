package com.trip.booking.spa.core.api.common.offer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 一次验价所产生的「可下单授权」，由网关自持，不出网关。
 *
 * <p>{@link #credentials} 是供应商内部形态的下单凭据。<b>这类东西一律不出境到上游</b>——
 * 它一旦被上游持有，上游就必须理解它、搬运它，将来还要在多处拆解它。
 *
 * <p><b>为什么凭据是键值对而不是单个字符串</b>：各家所需的凭据不是一个值。
 * 艺龙要 {@code hotelCode}/{@code shopperProductId}/{@code littleMajiaId}/
 * {@code goodsUniqId}/{@code roomTypeId} 等七项全齐才能验价；飞猪的 {@code rateKey}
 * 与 {@code request_trace_id} 必须配对使用；clwy 要 {@code rateKey} 加 {@code rateplanId}。
 * 若这里只给一个字符串，各供应商就只能自行发明编码把多项塞进去，而编码规则一旦
 * 只存在于注释里，就会在不同代码路径上漂移——上游那个「同一字段被六家赋予六种语义、
 * 仓内四处独立拆解且其中一处规则不一致」的局面，正是这样形成的。
 * 按名取用的键值对没有位置约定，也没有分隔符约定，故无从漂移。
 *
 * <p>键名由各供应商实现自行定义，只需与自己的读取方一致；网关不解释这些键的含义。
 *
 * @see OfferStore 为什么由网关持有，而不是让上游回传
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Offer {

    /**
     * 签发该报价的供应商。下单时须与请求的供应商一致，
     * 否则说明上游把 A 家的报价拿去 B 家下单了，属确定性错误。
     */
    private Integer supplierId;

    /**
     * 供应商内部的下单凭据，按名取用。
     *
     * <p>由签发方（各供应商的验价实现）写入，由同一供应商的下单实现读出。
     * 因为写读两端在同一份代码内，不存在跨系统按各自规则拼装再期望拼出同一个值的余地。
     */
    private Map<String, String> credentials;

    /**
     * 句柄失效时刻（epoch 毫秒）。
     *
     * <p>显式给出而非让调用方自行推算：上游需要知道这个报价还能撑多久，
     * 才能决定是直接下单还是先重新验价。让各方各自约定一个「大概几分钟」，
     * 是上游那套 TTL 阶梯彼此错位的起点。
     */
    private Long expiresAt;

    /** 取某项凭据；缺失返回 null，由调用方决定这是否致命 */
    public String credential(String name) {
        return credentials == null ? null : credentials.get(name);
    }
}
