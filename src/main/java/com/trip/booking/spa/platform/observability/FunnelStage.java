package com.trip.booking.spa.platform.observability;

import java.util.Locale;

/**
 * 漏斗阶段：一条报价从供应商响应到对外出报，途中被丢弃时丢在了哪一环
 * （docs/observability.md O-4.6：stage 取值保持小集合且稳定，新增须评审）。
 *
 * <p>与 {@link DropReason} 配对使用：stage 答「丢在哪一层」，reason 答「为什么丢」。
 */
public enum FunnelStage {

    /** 供应商响应 → 内部报价对象的转换环节（如艺龙查价的三类跳过） */
    CONVERT,

    /** 缓存写侧：报价已转换出来，落价格 Hash 时被丢弃（如无逐日价则无处落价） */
    CACHE_WRITE,

    /** 缓存读侧：价格 Hash 已取到，组装对外报价时被丢弃 */
    CACHE_READ;

    /** 标签值一律小写，与 Prometheus 惯例一致 */
    public String tagValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
