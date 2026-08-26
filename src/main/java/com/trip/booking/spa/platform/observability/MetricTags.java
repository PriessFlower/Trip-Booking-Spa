package com.trip.booking.spa.platform.observability;

import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;

import java.util.HashMap;
import java.util.Map;

/**
 * 指标标签键的唯一出处，以及标签表的构造入口（docs/observability.md O-2.4）。
 *
 * <p>此前 {@code "supplier"}、{@code "status"}、{@code "interface"} 这几个键以裸字面量
 * 散在 7 个文件里各写一遍，改名时无从知道改全了没有；{@code supplier} 的值更有三种方言
 * 并存——大写枚举名、小写字面量、数字编码（{@code "10010"}），于是
 * {@code catalog_attribute_hit{supplier="10010"}} 与
 * {@code supplier_io_access{supplier="ELONG"}} 在 PromQL 里拼不起来，
 * 「无房型映射丢多少」这个问题在指标通道上因此无解（O-2.3）。
 *
 * <p>两个键各管一个概念，各有一套取值，不得混用（O-2.7）：
 * <ul>
 *   <li>{@link #STATUS}——一次供应商调用的终态，取值只出自 {@link CallStatus}</li>
 *   <li>{@link #OUTCOME}——校验类检查的结果（如逐日价对齐），取值由该指标自行定义</li>
 * </ul>
 */
public final class MetricTags {

    /** 供应商。值一律取 {@link SupplierSourceEnum#name()}，禁止字面量与数字编码（O-2.3） */
    public static final String SUPPLIER = "supplier";

    /** 供应商接口。值取 {@link MonitorNameEnum#name()} */
    public static final String INTERFACE = "interface";

    /** 调用终态，取值只出自 {@link CallStatus} */
    public static final String STATUS = "status";

    /** 校验结果，与 {@link #STATUS} 不是同一个概念 */
    public static final String OUTCOME = "outcome";

    /** 漏斗阶段，取值只出自 {@link FunnelStage}（O-4.6：小集合且稳定） */
    public static final String STAGE = "stage";

    /** 丢弃原因，取值只出自 {@link DropReason}（O-4.4：必须枚举化） */
    public static final String REASON = "reason";

    /** 查价入口这条腿走了缓存还是实时。取值只有 {@link #SOURCE_CACHE} 与 {@link #SOURCE_LIVE} */
    public static final String SOURCE = "source";

    /** source 取值：走缓存 */
    public static final String SOURCE_CACHE = "cache";

    /** source 取值：实时问供应商 */
    public static final String SOURCE_LIVE = "live";

    /** 线程池注册名（{@code ThreadPools} 的池名） */
    public static final String POOL = "pool";

    /** 连接池目标 host（按 host 分池后每 host 一池） */
    public static final String HOST = "host";

    /** 限流桶键（{@code GLOBAL_LIMIT:<供应商>:<接口>[:<用途>]} 全键） */
    public static final String BUCKET = "bucket";

    /** 凭据续期档位，取值只出自 {@code CredentialRenewal.tagValue()} */
    public static final String RENEWAL = "renewal";

    private MetricTags() {
    }

    public static Map<String, Object> of(SupplierSourceEnum supplier) {
        Map<String, Object> tags = new HashMap<>(2);
        tags.put(SUPPLIER, supplier.name());
        return tags;
    }

    /** 不带终态的场景（如重试次数：它计的是尝试，不是一次调用的结果） */
    public static Map<String, Object> of(SupplierSourceEnum supplier, MonitorNameEnum api) {
        Map<String, Object> tags = of(supplier);
        tags.put(INTERFACE, api.name());
        return tags;
    }

    public static Map<String, Object> of(SupplierSourceEnum supplier, CallStatus status) {
        Map<String, Object> tags = of(supplier);
        tags.put(STATUS, status.tagValue());
        return tags;
    }

    public static Map<String, Object> of(SupplierSourceEnum supplier, MonitorNameEnum api, CallStatus status) {
        Map<String, Object> tags = of(supplier, status);
        tags.put(INTERFACE, api.name());
        return tags;
    }

    /** 文件下载这类非「调用终态」的结果，也走 {@link #OUTCOME} 键 */
    public static Map<String, Object> outcomeOf(SupplierSourceEnum supplier, MonitorNameEnum api, String outcome) {
        Map<String, Object> tags = of(supplier, api);
        tags.put(OUTCOME, outcome);
        return tags;
    }

    /** 校验类指标：{@code outcome} 的取值由调用方给出，不套用 {@link CallStatus} */
    public static Map<String, Object> outcomeOf(SupplierSourceEnum supplier, String outcome) {
        Map<String, Object> tags = of(supplier);
        tags.put(OUTCOME, outcome);
        return tags;
    }

    /** 漏斗丢弃（{@code quote_dropped}）：stage 答丢在哪一层，reason 答为什么丢 */
    public static Map<String, Object> dropped(SupplierSourceEnum supplier, FunnelStage stage, DropReason reason) {
        Map<String, Object> tags = of(supplier);
        tags.put(STAGE, stage.tagValue());
        tags.put(REASON, reason.tagValue());
        return tags;
    }

    /** 查价入口的一条「请求×供应商」腿：source 答走了缓存还是实时，outcome 答分态结论 */
    public static Map<String, Object> leg(SupplierSourceEnum supplier, String source, String outcome) {
        Map<String, Object> tags = of(supplier);
        tags.put(SOURCE, source);
        tags.put(OUTCOME, outcome);
        return tags;
    }

    /** 出报条数（{@code spa_price_quoted}）：只带 supplier 与 source 两维 */
    public static Map<String, Object> quoted(SupplierSourceEnum supplier, String source) {
        Map<String, Object> tags = of(supplier);
        tags.put(SOURCE, source);
        return tags;
    }

    /** 线程池水位与拒绝（{@code thread_pool_*}） */
    public static Map<String, Object> pool(String poolName) {
        Map<String, Object> tags = new HashMap<>(2);
        tags.put(POOL, poolName);
        return tags;
    }

    /** 连接池水位（{@code http_pool_*}） */
    public static Map<String, Object> host(String host) {
        Map<String, Object> tags = new HashMap<>(2);
        tags.put(HOST, host);
        return tags;
    }

    /** 限流等待与兜底命中（{@code ratelimit_*}）。bucket 集合有界：键随代码发布，不随流量 */
    public static Map<String, Object> bucket(String key) {
        Map<String, Object> tags = new HashMap<>(2);
        tags.put(BUCKET, key);
        return tags;
    }

}
