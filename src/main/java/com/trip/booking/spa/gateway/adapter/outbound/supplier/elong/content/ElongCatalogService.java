package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.content;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.domain.supplier.SupplierIdentityProfile;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;

/**
 * 艺龙产品建档（R-2.6 按腐性分层存储 / architecture.md 接入第六步）。
 *
 * <p><b>为什么要有</b>：迁移之初艺龙只做了五个能力面（查价/验价/下单/查单/取消），
 * 建档不在当时的移植标准里，于是房型、餐食、退改这些<b>供应商换一批报价码后仍然成立</b>
 * 的稳定事实，全挤在 Redis 产品详情缓存里。2026-08-19 实测的代价：该实例 97.4% 的键、
 * 1.19G/2G 内存都是产品详情，而这只覆盖 2,615 家酒店；且因缓存被误当事实源，
 * 换票基准一度错取刷价快照的单晚价（与客人所见的区间总价差一个量级）。
 *
 * <p><b>数据源=刷价响应，零额外额度</b>。Expedia 的建档（{@code ExpediaProductMappingService}）
 * 为此另查了一遍价（+9/+10 天占位日期、零售与打包各一遍）；艺龙刷价本就在打
 * {@code hotel.detail}，产品事实已在手里，写缓存的同时顺手 upsert 即可。
 *
 * <p><b>三条写入纪律</b>：
 * <ul>
 *   <li>身份列 {@code supplier_product_id} 放 <b>productKey</b>；艺龙报价码申报为
 *       {@link SupplierIdentityProfile.QuoteCodeStability#PERISHABLE}，故
 *       {@code supplier_quote_hint} 恒为 null（R-2.3：只有申报稳定的真码才可进 hint 列）。
 *       这一条挡住的是"从库里读凭证"——即 R-3.1 明令禁止、cursor 实测成功率 8% 的死路；</li>
 *   <li><b>UNKNOWN 不进目录</b>（R-5.4）：餐食或退改解析不出的产品跳过。UNKNOWN 进了目录
 *       会污染等价类——查询时它会与真正的"已知不含早"混为一谈；</li>
 *   <li>失败只记日志、绝不打断刷价（§6.2.1）。建档是增益路径，刷价是主路径。</li>
 * </ul>
 *
 * <p><b>统一侧列的填法</b>：本仓当前对艺龙无聚合能力，故 {@code product_id}/{@code room_id}/
 * {@code hotel_id} 按 Expedia 先例以供应商侧打底（1:1）。这些列属聚合域，将来聚合接入后
 * 可被重写（R-2.4：只允许改统一侧，供应商侧是事实不改）。
 */
@Slf4j
@Service
@RefreshScope
public class ElongCatalogService {

    private static final int SUPPLIER_ID = SupplierSourceEnum.ELONG.getCode();
    private static final String OPERATOR = "elong-refresh";

    @Resource
    private ProductCatalogMapper productCatalogMapper;

    /** UNKNOWN 判定必须问 deriver(与 key 派生同源),不得在此重写——见 isCatalogEligible 注释 */
    @Resource
    private ElongProductKeyDeriver productKeyDeriver;

    /**
     * 建档开关。默认关——建档会给刷价的每一轮增加数千次 upsert，须先在生产观察
     * 写入耗时对刷价周期的影响，确认无碍再开（§3.8.1：存在正当运维场景需不发版关闭）。
     */
    @Value("${supplier.elong.catalog-enabled:false}")
    private boolean catalogEnabled;

    /**
     * 把一轮查价产出的产品写入目录/档案。
     *
     * @param products 刷价转换后的产品；{@code productKey} 为空或含 UNKNOWN 的会被跳过
     */
    public void upsert(List<ProductRespDTO> products) {
        if (!catalogEnabled) {
            return;
        }
        if (products == null || products.isEmpty()) {
            return;
        }
        String quoteHint = SupplierIdentityProfile.forCode(SUPPLIER_ID).quoteCodeStability()
                == SupplierIdentityProfile.QuoteCodeStability.STABLE ? "" : null;
        int upserted = 0;
        int skippedUnknown = 0;
        for (ProductRespDTO product : products) {
            if (StringUtils.isBlank(product.getProductKey())
                    || !productKeyDeriver.isCatalogEligible(product.getMeal(), product.getCancelPolicy())) {
                // UNKNOWN 照常参与实时链路(key 合法、可售),但不进目录(R-5.4)
                skippedUnknown++;
                continue;
            }
            try {
                HashMap<String, Object> p = new HashMap<>();
                // 统一侧以供应商侧打底（1:1），聚合接入后可重写（R-2.4）
                p.put("productId", product.getProductKey());
                p.put("roomId", product.getRoom() == null ? null : product.getRoom().getRoomId());
                p.put("hotelId", product.getHotelId());
                p.put("supplierId", SUPPLIER_ID);
                p.put("supplierHotelId", product.getHotelId());
                p.put("supplierRoomId", product.getRoom() == null ? null : product.getRoom().getRoomId());
                // 身份列=productKey；艺龙报价码易腐，hint 恒 null（R-2.3）
                p.put("supplierProductId", product.getProductKey());
                p.put("supplierQuoteHint", quoteHint == null ? null : product.getProductId());
                p.put("supplierProductName", product.getProductInfo() == null
                        ? null : product.getProductInfo().getProductName());
                // 有窗是房型层事实，产品层占位（同 Expedia）
                p.put("hasWindow", 0);
                p.put("breakfast", product.getMeal() != null && isPositive(product.getMeal().getCount()) ? 1 : 0);
                p.put("cancelType", hasFreeCancelWindow(product) ? 1 : 0);
                p.put("operator", OPERATOR);
                productCatalogMapper.upsertGlobalProductSupplier(p);
                productCatalogMapper.upsertSupplierProductBase(p);
                upserted++;
            } catch (Exception e) {
                // 建档是增益路径：写失败绝不打断刷价主路径
                log.warn("艺龙产品建档：单条写入失败,sHotelId={},productKey={},err={}",
                        product.getHotelId(), product.getProductKey(), e.toString());
            }
        }
        // 每批必打一行成果（§6.2.1）——反面是 Expedia 全量建档 9.7 万家时进度不可观测
        log.info("艺龙产品建档：sHotelId={},写入 {} 条(等价卖法在库内归并),跳过 UNKNOWN {} 条",
                products.get(0).getHotelId(), upserted, skippedUnknown);
    }

    /** 是否存在免费取消窗口——与 productKey 的 cancelClass 判据同源（R-5.1 的 FREE 判据）。 */
    private static boolean hasFreeCancelWindow(ProductRespDTO product) {
        return product.getCancelPolicy() != null && product.getCancelPolicy().stream()
                .anyMatch(c -> Integer.valueOf(1).equals(c.getCancelType()));
    }

    private static boolean isPositive(Integer count) {
        return count != null && count > 0;
    }
}
