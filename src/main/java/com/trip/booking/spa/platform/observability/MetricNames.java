package com.trip.booking.spa.platform.observability;

/**
 * 埋点名的唯一出处（docs/observability.md O-2.4）。
 *
 * <p>名字里<b>不含</b>后缀：{@link Monitor} 按类型追加 {@code _count}/{@code _time}/
 * {@code _value}，Prometheus 再给 counter 追加 {@code _total}（O-2.2）。
 *
 * <p>名字里也<b>不含</b>供应商与接口这类维度，它们进标签（O-2.1）。反面是撤掉的两处：
 * {@code ChunkedFileAccess} 曾用 {@code JOINER.join(supplier, 接口, tag)} 拼名字，
 * 9 供应商 × 20 接口 × 6 后缀最多产生 1080 个独立指标名；艺龙验价曾按结果拆成
 * {@code elong_validate_day_price_mismatch} / {@code _retry_ok} / {@code _retry_failed}
 * 三个名字，于是「对齐失败率」这一个问题要查三个指标才能回答。
 *
 * <p>每个常量的注释就是该指标的解释（O-3.2 的一半，另一半是看板的 description）。
 */
public final class MetricNames {

    /** 一次供应商调用。标签 supplier/interface/status，status 出自 {@link CallStatus} */
    public static final String SUPPLIER_IO_ACCESS = "supplier_io_access";

    /** 供应商调用的重试次数。与「一次调用的结果」不是同一个度量，混在一起会把成功率算错 */
    public static final String SUPPLIER_IO_RETRY = "supplier_io_retry";

    /** 供应商原始查询（未经本仓转换的那一跳）耗时与次数 */
    public static final String SUPPLIER_IO_ORIGINAL_QUERY = "supplier_io_original_query";

    /** 供应商大文件下载。标签 supplier/interface/status，此前维度拼在名字里 */
    public static final String SUPPLIER_FILE_ACCESS = "supplier_file_access";

    /** 大文件下载的字节数（KB）。标签 supplier/interface */
    public static final String SUPPLIER_FILE_BYTES = "supplier_file_bytes";

    /** 大文件下载中重试过的分块数 */
    public static final String SUPPLIER_FILE_CHUNK_RETRY = "supplier_file_chunk_retry";

    /** 下载方式：分块并发 */
    public static final String FILE_CHUNKED = "chunked";

    /** 下载方式：单连接 */
    public static final String FILE_SINGLE = "single";

    /** 分块下载失败、回落单连接 */
    public static final String FILE_FALLBACK = "fallback";

    /** 下载失败 */
    public static final String FILE_ERROR = "error";

    /** 比价链路向某供应商发起的查价。标签 supplier/status */
    public static final String PRICING_SUPPLIER_QUERY = "pricing_supplier_query";

    /** 出价时向档案表问过属性的产品数（覆盖率的分母） */
    public static final String CATALOG_ATTRIBUTE_ASKED = "catalog_attribute_asked";

    /** 其中真拿到属性的产品数（覆盖率的分子）。分母涨、分子不涨=建档没跟上 */
    public static final String CATALOG_ATTRIBUTE_HIT = "catalog_attribute_hit";

    /** 建档写入的行数 */
    public static final String CATALOG_UPSERTED = "catalog_upserted";

    /** 建档跳过：字段解析不出确定值（餐食/退改 UNKNOWN 不进目录） */
    public static final String CATALOG_SKIPPED_UNKNOWN = "catalog_skipped_unknown";

    /** 建档跳过：算不出 productKey */
    public static final String CATALOG_SKIPPED_NO_KEY = "catalog_skipped_no_key";

    /** 刷价一轮处理的总行数（失败率的分母） */
    public static final String REFRESH_ROWS = "refresh_rows";

    /** 刷价一轮里在售出报的行数 */
    public static final String REFRESH_ONSALE = "refresh_onsale";

    /** 刷价一轮里查得通但无房的行数 */
    public static final String REFRESH_EMPTY = "refresh_empty";

    /** 刷价一轮里失败的行数（失败率的分子） */
    public static final String REFRESH_FAILED = "refresh_failed";

    /** 刷价一轮里沿用上一轮价格的行数 */
    public static final String REFRESH_BORROWED = "refresh_borrowed";

    /** 刷价一轮里被降级处理的行数 */
    public static final String REFRESH_DEMOTED = "refresh_demoted";

    /** 刷价一轮的耗时（毫秒，O-2.6） */
    public static final String REFRESH_ROUND = "refresh_round";

    /**
     * 本轮已处理的调用数（gauge，轮内实时更新）。
     *
     * <p>为什么需要它：上面那些 {@code refresh_*} 计数器都在<b>轮末一次上报</b>，于是一轮 9 分钟里
     * 面板上是一条平线——用 5 分钟窗口去看 rate 大概率落在两个轮次之间，画出来是 0，看着像没在跑
     * （2026-08-25 实测踩到）。本 gauge 与 {@link #REFRESH_INFLIGHT_SIZE} 配对，给出「已处理/共」
     * 的轮内进度，调速时不必等一轮结束就能看出效果。
     */
    public static final String REFRESH_INFLIGHT_DONE = "refresh_inflight_done";

    /**
     * 本轮共需处理的调用数（gauge，= 取到的行数 × 占用数）。与 {@link #REFRESH_INFLIGHT_DONE} 配对。
     *
     * <p>叫 size 而不是 total：埋点名不得自带 {@code _total} 后缀（O-2.2）——Prometheus 会给
     * counter 再追加一次，得到 {@code xxx_total_total}。这条有架构测试守着。
     */
    public static final String REFRESH_INFLIGHT_SIZE = "refresh_inflight_size";

    /** 验价时逐日价与总价的对齐检查。标签 supplier/outcome，取值见 {@link #ALIGN_MISMATCH} 等 */
    public static final String VALIDATE_DAY_PRICE_ALIGN = "validate_day_price_align";

    /** 对齐检查：首次即不一致 */
    public static final String ALIGN_MISMATCH = "mismatch";

    /** 对齐检查：重试后一致 */
    public static final String ALIGN_RETRY_OK = "retry_ok";

    /** 对齐检查：重试后仍不一致 */
    public static final String ALIGN_RETRY_FAILED = "retry_failed";

    /** 对外查价接口的耗时（毫秒），每个 HTTP 请求记一次 */
    public static final String QUERY_PRICE_FOR_SPA = "query_price_for_spa";

    /**
     * 对外查价的一条「请求×供应商」腿。一个 HTTP 请求带 N 家供应商就是 N 条腿，每腿记一次。
     * 标签 supplier / source（cache|live）/ outcome（available|no_inventory|indeterminate
     * 即 PricingOutcome 小写，外加 {@link #LEG_ERROR}=处理中抛异常）。
     * 出报率 = available 腿 / 全部腿（O-4.2 的请求数与出报率）。
     */
    public static final String SPA_PRICE_LEG = "spa_price_leg";

    /** 腿的第四个 outcome：处理中抛异常（HTTP 报错出去）。不补上它，sum(腿) 就不等于腿总数（O-3.3） */
    public static final String LEG_ERROR = "error";

    /** 对外查价实际出报的产品条数。标签 supplier/source。与 spa_price_leg 相除得每腿平均条数 */
    public static final String SPA_PRICE_QUOTED = "spa_price_quoted";

    /**
     * 漏斗：一条报价在出报前被丢弃。标签 supplier / stage（{@link FunnelStage}）/
     * reason（{@link DropReason}）。此前这些分支要么只有日志（艺龙三类跳过）、要么
     * 什么都没有（缓存读侧五个 return），「丢在哪」只能靠 grep 和猜（O-4.5）。
     */
    public static final String QUOTE_DROPPED = "quote_dropped";

    /**
     * 检出一次我方凭据/配置病（FailureKind.AUTH_CONFIG）。标签 supplier。
     * <b>任何非零都该有人看</b>：这一档病供应商无辜、重试无效，只有人能修——
     * cursor 的飞猪 session 病因为无处表达，被当"集成死"晾了两个月（2026-06~08-10）。
     */
    public static final String SUPPLIER_AUTH_CONFIG = "supplier_auth_config";

    private MetricNames() {
    }
}
