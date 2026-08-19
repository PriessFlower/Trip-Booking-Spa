package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import org.apache.ibatis.annotations.Param;

import java.util.HashMap;

/**
 * 产品目录/档案的<b>供应商通用</b>写入口（R-2.6 按腐性分层存储）。
 *
 * <p>为什么抽出来：两条 upsert 语句除 {@code operator} 外与供应商无关，而
 * {@code ExpediaCatalogMapper} 里还混着 Expedia 专属的快照/地理表操作。艺龙接建档时
 * 若去调那个 mapper，等于让艺龙依赖 Expedia 的适配层——违反 architecture.md §2
 * （供应商语义只允许出现在各自适配层）。
 *
 * <p>写入纪律：
 * <ul>
 *   <li><b>身份列放 productKey</b>（{@code supplier_product_id}），供应商真码只可进
 *       {@code supplier_quote_hint} 且仅当申报为稳定（R-2.3）——艺龙申报 PERISHABLE，
 *       故其 hint 恒为 null；</li>
 *   <li><b>UNKNOWN 不进目录</b>（R-5.4）：餐食或退改解析不出的产品由调用方跳过，
 *       否则污染等价类匹配；</li>
 *   <li>{@code operator} 由调用方传入（如 {@code elong-refresh}），用于区分数据来源。</li>
 * </ul>
 */
public interface ProductCatalogMapper {

    /** 目录域：统一侧 + 供应商侧的桥（R-2.4：供应商侧列是事实，不改）。 */
    int upsertGlobalProductSupplier(@Param("p") HashMap<String, Object> params);

    /** 档案域：供应商产品事实。 */
    int upsertSupplierProductBase(@Param("p") HashMap<String, Object> params);
}
