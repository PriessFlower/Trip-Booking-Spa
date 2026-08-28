package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaProductKeyDeriver;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.supplier.SupplierIdentityProfile;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
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
 * Expedia 产品建档：<b>数据源=刷价响应，零额外额度</b>（R-2.6）。
 *
 * <p><b>为什么要有</b>：Expedia 原先只有 {@link ExpediaProductMappingService} 一条建档路径，
 * 它为建档<b>另查一遍价</b>（+9/+10 天占位日期、零售与打包各一遍），且只有后门能触发。
 * 结果是 2026-08-28 查生产库时 {@code supplier_product_base} 的 Expedia 行数为 <b>0</b>
 * （同表艺龙 508,076 行 / 10,525 家）——没人打过后门，而刷价那条路根本不建档。
 * 同期 Expedia 刷价<b>每 30 分钟就把任务表 2,340 家全刷一遍</b>，产品事实每半小时
 * 从眼前流过一次，只写了缓存没写目录。本类即把那份已在手里的数据顺手落库，
 * 与艺龙 {@code ElongCatalogService} 同构。
 *
 * <p><b>与 {@link ExpediaProductMappingService} 的分工</b>：后者是全量补建（按酒店清单分页、
 * 自带占位住期），适合首次铺底或按需补某几家；本类是随刷价增量维护，覆盖面等于刷价清单。
 * 两者写同一张表、同一套列语义，只是数据来源与触发方式不同。
 *
 * <p><b>三条写入纪律</b>（与艺龙一致）：
 * <ul>
 *   <li>身份列放 <b>productKey</b>，报价码另走 {@code supplier_quote_hint}（R-2.3：
 *       身份与令牌永不同字段）；是否填 hint 一律问 {@link SupplierIdentityProfile}，
 *       本处不自行判断腐性；</li>
 *   <li><b>UNKNOWN 不进目录</b>（R-5.4）：判据只能问派生器（R-2.8）——空列表之外还有
 *       「阶梯在但判不出全款」的第三种 UNKNOWN，从列表判空看不出来；</li>
 *   <li>失败只记日志、绝不打断刷价（§6.2.1）。建档是增益路径，刷价是主路径。</li>
 * </ul>
 */
@Slf4j
@Service
@RefreshScope
public class ExpediaCatalogService {

    private static final int SUPPLIER_ID = SupplierSourceEnum.EXPEDIA.getCode();
    private static final String OPERATOR = "expedia-refresh";

    @Resource
    private ProductCatalogMapper productCatalogMapper;

    /** UNKNOWN 判定必须问 deriver(与 key 派生同源),不得在此重写 */
    @Resource
    private ExpediaProductKeyDeriver productKeyDeriver;

    /**
     * 建档开关。默认关——建档会给刷价的每一轮增加数千次 upsert，须先在生产观察
     * 写入耗时对刷价周期的影响，确认无碍再开（§3.8.1：存在正当运维场景需不发版关闭）。
     */
    @Value("${supplier.expedia.catalog-enabled:false}")
    private boolean catalogEnabled;

    /** 闸口日志去重:true=下次关闸时该打一条。仅为控制日志量,不参与业务判断 */
    private final java.util.concurrent.atomic.AtomicBoolean gateLogged =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    /**
     * 把一轮刷价产出的产品写入目录。
     *
     * @param products 刷价转换后的产品；{@code productKey} 为空或含 UNKNOWN 的会被跳过
     */
    public void upsert(List<ProductRespDTO> products) {
        if (!catalogEnabled) {
            // 闸口的 REJECT 分支必须可检索(§3.8.4)。但本方法每轮被调数千次,逐次打会淹掉
            // 日志——故按开关状态只在翻转后打第一次。默认关的功能若完全无输出,
            // "为什么目录表没数据"在日志里就没有答案(这正是 Expedia 目录空了这么久的教训)。
            if (gateLogged.compareAndSet(true, false)) {
                log.info("[gate] supplier.expedia.catalog-enabled=false，跳过建档");
            }
            return;
        }
        gateLogged.set(true);
        if (products == null || products.isEmpty()) {
            return;
        }
        boolean quoteCodeStable = SupplierIdentityProfile.forCode(SUPPLIER_ID).quoteCodeStability()
                == SupplierIdentityProfile.QuoteCodeStability.STABLE;
        int upserted = 0;
        int skippedUnknown = 0;
        int skippedNoKey = 0;
        for (ProductRespDTO product : products) {
            // 两个成因必须分开计(§6.2.2):缺 productKey 是<b>派生失败</b>,矛头指向派生器或它的
            // 入参;UNKNOWN 是<b>解析覆盖不足</b>,矛头指向餐食/退改的表外取值。
            if (product.getIdentity() == null || StringUtils.isBlank(product.getProductKey())) {
                skippedNoKey++;
                continue;
            }
            if (!productKeyDeriver.isCatalogEligible(product.getMeal(), product.getCancelPolicy())) {
                // UNKNOWN 照常参与实时链路(key 合法、可售),但不进目录(R-5.4)
                skippedUnknown++;
                continue;
            }
            ProductIdentity identity = product.getIdentity();
            try {
                // 全部照抄派生器的产物（R-2.8）：本处<b>一个判定都不做</b>
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
                // rate.id 是报价标识，只当快速通道（R-2.3）；申报稳定才可填
                p.put("supplierQuoteHint", quoteCodeStable ? product.getProductId() : null);
                p.put("operator", OPERATOR);
                productCatalogMapper.upsertSupplierProductBase(p);
                upserted++;
            } catch (Exception e) {
                // 建档是增益路径：写失败绝不打断刷价主路径。
                // 传 e 而非 e.toString():后者吃掉 cause 链(§6.1.1.1 异常堆栈 100% 保留)
                log.warn("Expedia产品建档：单条写入失败,sHotelId={},productKey={}",
                        product.getHotelId(), product.getProductKey(), e);
            }
        }
        // 可数的业务指标不许只活在日志里(§3.9.1):建档覆盖的增长本身是一条趋势曲线,
        // 而 skippedNoKey 突增是 productKey 派生回归的早期警报。日志行照留(§6.1.1)。
        java.util.Map<String, Object> tags = new java.util.HashMap<>(2);
        tags.put(MetricTags.SUPPLIER, SupplierSourceEnum.EXPEDIA.name());
        Monitor.recordMany(MetricNames.CATALOG_UPSERTED, tags, upserted);
        Monitor.recordMany(MetricNames.CATALOG_SKIPPED_UNKNOWN, tags, skippedUnknown);
        Monitor.recordMany(MetricNames.CATALOG_SKIPPED_NO_KEY, tags, skippedNoKey);
        log.info("Expedia产品建档：sHotelId={},upserted={},skippedUnknown={},skippedNoKey={}",
                products.get(0).getHotelId(), upserted, skippedUnknown, skippedNoKey);
    }
}
