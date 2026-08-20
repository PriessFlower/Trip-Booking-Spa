package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.content;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.domain.supplier.SupplierIdentityProfile;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.Monitor;
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

    /** 闸口日志去重:true=下次关闸时该打一条。仅为控制日志量,不参与业务判断 */
    private final java.util.concurrent.atomic.AtomicBoolean gateLogged =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    /**
     * 把一轮查价产出的产品写入目录/档案。
     *
     * @param products 刷价转换后的产品；{@code productKey} 为空或含 UNKNOWN 的会被跳过
     */
    public void upsert(List<ProductRespDTO> products) {
        if (!catalogEnabled) {
            // 闸口的 REJECT 分支必须可检索(§3.8.4:没有可检索输出的闸口视为未实现)。
            // 但本方法每轮被调数千次,逐次打会淹掉日志——故按开关状态只在翻转后打第一次。
            // 默认关的功能若完全无输出,"为什么目录表没数据"在日志里就没有答案。
            if (gateLogged.compareAndSet(true, false)) {
                log.info("[gate] supplier.elong.catalog-enabled=false，跳过建档");
            }
            return;
        }
        gateLogged.set(true);
        if (products == null || products.isEmpty()) {
            return;
        }
        String quoteHint = SupplierIdentityProfile.forCode(SUPPLIER_ID).quoteCodeStability()
                == SupplierIdentityProfile.QuoteCodeStability.STABLE ? "" : null;
        int upserted = 0;
        int skippedUnknown = 0;
        int skippedNoKey = 0;
        for (ProductRespDTO product : products) {
            // 两个成因必须分开计(§6.2.2):缺 productKey 是<b>派生失败</b>,矛头指向
            // ElongProductKeyDeriver 或它的入参;UNKNOWN 是<b>解析覆盖不足</b>,矛头指向
            // 餐食/退改的表外取值。合成一个数,派生回归会被当成"正常的 UNKNOWN 损耗"混过去。
            if (product.getIdentity() == null || StringUtils.isBlank(product.getProductKey())) {
                skippedNoKey++;
                continue;
            }
            ProductIdentity identity = product.getIdentity();
            if (!productKeyDeriver.isCatalogEligible(product.getMeal(), product.getCancelPolicy())) {
                // UNKNOWN 照常参与实时链路(key 合法、可售),但不进目录(R-5.4)
                skippedUnknown++;
                continue;
            }
            try {
                // 全部照抄派生器的产物（R-2.8）：本处<b>一个判定都不做</b>。
                // 原先这里把 Meal/CancelPolicy 重判一遍再压成 breakfast/cancelType 两个 int，
                // 既降维（B1L1D1 与 B1L0D0 同为 1、占用无处安放）又与派生器分叉。
                HashMap<String, Object> p = new HashMap<>();
                p.put("supplierId", SUPPLIER_ID);
                p.put("productKey", identity.productKey());
                p.put("supplierAccount", identity.account());
                p.put("supplierHotelId", identity.supplierHotelId());
                p.put("supplierRoomId", identity.supplierRoomId());
                p.put("mealSignature", identity.mealSignature());
                p.put("cancelClass", identity.cancelClass());
                p.put("occupancy", identity.occupancy());
                p.put("supplierProductName", product.getProductInfo() == null
                        ? null : product.getProductInfo().getProductName());
                // 艺龙报价码易腐，hint 恒 null（R-2.3）
                p.put("supplierQuoteHint", quoteHint == null ? null : product.getProductId());
                p.put("operator", OPERATOR);
                productCatalogMapper.upsertSupplierProductBase(p);
                upserted++;
            } catch (Exception e) {
                // 建档是增益路径：写失败绝不打断刷价主路径
                // 传 e 而非 e.toString():后者吃掉 cause 链,而"连接池耗尽"与"SQL 写错"
                // 在包装异常的外层消息上看起来一样(§6.1.1.1 异常堆栈 100% 保留)
                log.warn("艺龙产品建档：单条写入失败,sHotelId={},productKey={}",
                        product.getHotelId(), product.getProductKey(), e);
            }
        }
        // 可数的业务指标不许只活在日志里(§3.9.1):建档覆盖的增长本身就是一条趋势曲线,
        // 而 skippedNoKey 突增是 productKey 派生回归的早期警报。日志行照留(§6.1.1)。
        java.util.Map<String, Object> tags = new java.util.HashMap<>(2);
        tags.put("supplier", "elong");
        Monitor.recordMany("catalog_upserted", tags, upserted);
        Monitor.recordMany("catalog_skipped_unknown", tags, skippedUnknown);
        Monitor.recordMany("catalog_skipped_no_key", tags, skippedNoKey);
        // 每批必打一行成果（§6.2.1）——反面是 Expedia 全量建档 9.7 万家时进度不可观测。
        // 键值对形式便于机读(§6.3.2)
        log.info("艺龙产品建档：sHotelId={},upserted={},skippedUnknown={},skippedNoKey={}",
                products.get(0).getHotelId(), upserted, skippedUnknown, skippedNoKey);
    }


}
