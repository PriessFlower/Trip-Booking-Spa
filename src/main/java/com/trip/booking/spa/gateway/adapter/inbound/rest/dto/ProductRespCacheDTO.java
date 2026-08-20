package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import lombok.Data;

/**
 * 票据缓存（{@code quote:{hotelId}:{productKey}}）的载荷（R-2.6 按腐性分层存储）。
 *
 * <p><b>只放两类</b>：易腐码、以及读侧无法自行重算的少数标量。稳定属性（房型、餐食、
 * 退改、占用、产品名）由目录表承载，出价时按 {@link #productKey} 回查（见
 * {@code ProductAttributeReader}）；<b>金额一律不放</b>——见下。
 *
 * <p>此前本 DTO 承载全部 24 个字段，导致 Redis 里 17.5 万条详情占该实例 97.4% 的键、
 * 1.19G/2G 内存，而这只覆盖 2,615 家酒店；且因缓存成为稳定信息的唯一事实源，
 * 换票基准一度错取刷价快照的单晚价（2026-08-19）。
 *
 * <p><b>金额字段为什么全部删除</b>（2026-08-20）：出价读侧在
 * {@code BeanUtils.copyProperties} 之后<b>逐项重算并覆盖</b>了 totalPrice、totalTaxes、
 * roomTotalPrice、stayPrice、storePayPrice、brokerage —— 它们按客人查询区间逐日累加，
 * 而缓存里存的是<b>刷价那一次</b>区间的快照（通常 1 晚）。也就是说这六个字段写进 Redis
 * 每轮一遍、读出来立刻被丢弃，纯占地方。
 *
 * <p>更要紧的是 {@code totalPrice} 不只是浪费：它正是 2026-08-19 那次换票基准取错
 * 量级的来源（客人查 3 晚、基准取 1 晚）。当时的修法是让调用方改走出价同一条路径，
 * 但字段留着就仍是个陷阱——下一个人看到"缓存里有总价"仍会去读它。删掉，陷阱才消失。
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

    /**
     * 线下支付金额的币种。
     *
     * <p>金额本身（{@code storePayPrice}）由读侧按日累加，但币种无处可算——它不在每日
     * 价里，也不是产品的稳定属性（同一卖法换个渠道可能换币种），故留在票据里。
     */
    private String storePayCurrency;

    /** 报价币种。同上，读侧无法自行重算 */
    private String currencyType;

    /** 报价币种（对外字段名）。与 {@link #currencyType} 并存是历史契约，不在本次收敛范围 */
    public String currency;
}
