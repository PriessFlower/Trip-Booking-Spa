package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品档案的<b>供应商通用</b>写入口（R-2.6 按腐性分层存储）。
 *
 * <p>为什么抽出来：这条 upsert 除 {@code operator} 外与供应商无关，而
 * {@code ExpediaCatalogMapper} 里还混着 Expedia 专属的快照/地理表操作。艺龙接建档时
 * 若去调那个 mapper，等于让艺龙依赖 Expedia 的适配层——违反 architecture.md §2
 * （供应商语义只允许出现在各自适配层）。
 *
 * <p>写入纪律：
 * <ul>
 *   <li><b>一行=一个卖法</b>，身份列 {@code product_key} 放 productKey；供应商真码只可进
 *       {@code supplier_quote_hint} 且仅当申报为稳定（R-2.3）——艺龙申报 PERISHABLE，
 *       故其 hint 恒为 null；</li>
 *   <li><b>只落派生产物，不做判定</b>（R-2.8）：{@code meal_signature} / {@code cancel_class} /
 *       {@code occupancy} / {@code supplier_account} 一律取自
 *       {@code ProductIdentity}，调用方不得从原始响应或出参 DTO 重新判一遍；</li>
 *   <li><b>列必须能重算出 product_key</b>（R-2.7）：七个成分各有一列，且按派生时的原形存；</li>
 *   <li><b>UNKNOWN 不进目录</b>（R-5.4）：餐食或退改解析不出的产品由调用方跳过，
 *       否则污染等价类匹配；</li>
 *   <li>{@code operator} 由调用方传入（如 {@code elong-refresh}），用于区分数据来源。</li>
 * </ul>
 *
 * <p><b>没有 global_product_supplier</b>：那是聚合域的桥，按 R-6.1 不放在供应商网关，
 * 2026-08-20 已停写并撤表。谁做聚合谁自建，SPA 只负责产出 productKey。
 */
public interface ProductCatalogMapper {

    /** 档案域：供应商产品事实。 */
    int upsertSupplierProductBase(@Param("p") HashMap<String, Object> params);

    /**
     * 出价读侧：按 productKey 批量取稳定属性（R-2.6）。
     *
     * <p>返回行的列名即 map 的 key，调用方是 {@code ProductAttributeReader}。
     * 只取出价响应真正要用的几列，不做 {@code SELECT *}——档案表会长，
     * 而多取的列会顺着进程内缓存长期驻留在内存里。
     */
    List<Map<String, Object>> selectAttributesByProductKeys(@Param("supplierId") int supplierId,
                                                            @Param("productKeys") List<String> productKeys);
}
