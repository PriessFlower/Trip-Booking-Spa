package com.trip.booking.spa.platform.observability;

import java.util.Locale;

/**
 * 报价丢弃原因
 */
public enum DropReason {

    // ── CONVERT 阶段（供应商响应 → 内部报价对象）──

    /** 停售或无库存：供应商返回了该产品，但标记为不可售 */
    NOT_ON_SALE,

    /** 缺会话凭据：没有下单所需的短时效凭证（如艺龙 GoodsUniqId/马甲），有价也不可成交 */
    NO_SESSION_CREDENTIALS,

    /** 缺每日价：逐日价格拆不出来，无法按住期报价 */
    NO_DAY_PRICE,

    /** 缺所查占用的价：rate 在响应里，但 occupancy_pricing 没有本次查询的占用档（Expedia） */
    NO_OCCUPANCY_PRICING,

    // ── CACHE_READ 阶段（缓存读侧组装出报）──

    /** 总价为 0：该产品在缓存里的逐日价合计为 0 */
    ZERO_TOTAL_PRICE,

    /** 逐日价条数≠住期天数：某一天没有价格数据，多晚查询必然命中此分支 */
    DAY_COUNT_MISMATCH,

    /** 条数够但某一天价格为 0 */
    ZERO_DAY_PRICE,

    /** 票据详情缺席：quote 键查不到详情，只有价没有票的报价不可成交（R-1.6） */
    QUOTE_DETAIL_MISSING,

    /** 详情里没有报价码（productId 为空），下单会被供应商拒 */
    PRODUCT_ID_MISSING;

    /** 标签值一律小写，与 Prometheus 惯例一致 */
    public String tagValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
