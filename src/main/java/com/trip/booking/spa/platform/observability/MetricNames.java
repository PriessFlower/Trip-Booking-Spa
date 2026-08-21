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

    /** 验价时逐日价与总价的对齐检查。标签 supplier/outcome，取值见 {@link #ALIGN_MISMATCH} 等 */
    public static final String VALIDATE_DAY_PRICE_ALIGN = "validate_day_price_align";

    /** 对齐检查：首次即不一致 */
    public static final String ALIGN_MISMATCH = "mismatch";

    /** 对齐检查：重试后一致 */
    public static final String ALIGN_RETRY_OK = "retry_ok";

    /** 对齐检查：重试后仍不一致 */
    public static final String ALIGN_RETRY_FAILED = "retry_failed";

    /** 对外查价接口的耗时（毫秒）。请求数与出报数尚未埋，见 O-4.2 与 §10 第 9 项 */
    public static final String QUERY_PRICE_FOR_SPA = "query_price_for_spa";

    private MetricNames() {
    }
}
