package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import lombok.Data;

/**
 * 产品详情缓存的载荷（R-2.6 按腐性分层存储）。
 *
 * <p><b>只放三类</b>：易腐码、通往目录的桥、当轮价格。稳定属性（房型、餐食、退改、
 * 占用、产品名）由目录表承载，出价时按 {@link #productKey} 回查（见
 * {@code ProductAttributeReader}）。
 *
 * <p>此前本 DTO 承载全部 24 个字段，导致 Redis 里 17.5 万条详情占该实例 97.4% 的键、
 * 1.19G/2G 内存，而这只覆盖 2,615 家酒店；且因缓存成为稳定信息的唯一事实源，
 * 换票基准一度错取刷价快照的单晚价（2026-08-19）。瘦身后单条约 1,225 → 366 字节。
 *
 * <p><b>{@code productKey} 是那条桥，删它等于把库里的属性永久锁死</b>——出价拿到的是
 * 易腐的 productId，而 productId 依 R-2.1 不落库，只能靠本字段转一道。
 */
@Data
public class ProductRespCacheDTO {

    /** 供应商报价码：易腐（艺龙会话级轮换），故只存缓存、禁止落库（R-2.1） */

    public String productId;
    /**
     * 卖法等价类键（R-1.1，跨次稳定）。缓存必须原样保存——它是对上游的不透明句柄
     * （gateway-boundary B1）与 resolve 换票的检索键。2026-08-18 发现本 DTO 缺此字段，
     * 刷价写缓存时 productKey 被静默丢弃、缓存读出的产品 productKey=null，直接阻塞
     * cursor 走缓存比价的对接。写读两侧均为 BeanUtils.copyProperties 按名复制,
     * 补上字段即自动透传；旧缓存条目反序列化为 null，随刷价周期自然覆盖。
     */
    private String productKey;
    public String planSession;
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
     * 外币币种
     */
    private String currencyType;
    /**
     * 外币币种
     */
    public String currency;
}
