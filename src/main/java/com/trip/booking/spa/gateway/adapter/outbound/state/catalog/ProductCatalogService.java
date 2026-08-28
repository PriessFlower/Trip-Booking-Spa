package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.supplier.SupplierIdentityProfile;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 产品建档（R-2.6 写侧，供应商通用）：把一轮报价的稳定成分 upsert 进档案表。
 *
 * <p>能通用的依据（R-2.8 只照抄不判定）：七列全部原样来自 {@link ProductIdentity}；
 * UNKNOWN 判定也已由各家 deriver 做完写进成分（{@code mealSignature}/{@code cancelClass}
 * 的 UNKNOWN 取值，R-5.4 可售不进目录）——本处读判决，不重判。quoteHint 走腐性
 * 申报注册表（R-2.3，易腐报价码禁落库）。
 *
 * <p>挂在写缓存公共漏斗（{@code productToCache}）上：刷价与验价即刷两路自然全覆盖。
 * 前身是每家手抄一份、靠各家记得在刷价里调一行——艺龙的验价即刷因此漏了建档，
 * 飞猪整家没接（2026-08-28）。
 *
 * <p>开关按家：{@code supplier.{name}.catalog-enabled} 默认关——建档给刷价每轮
 * 增加数千次 upsert，须先观察写入耗时对刷价周期的影响再开（§3.8.1）。
 */
@Slf4j
@Service
public class ProductCatalogService {

    @Autowired
    private ProductCatalogMapper productCatalogMapper;

    @Autowired
    private Environment environment;

    /** 闸口日志去重（按家）：true=下次关闸时该打一条。仅为控制日志量，不参与业务判断 */
    private final Map<Integer, AtomicBoolean> gateLogged = new ConcurrentHashMap<>();

    /**
     * 建档是增益路径：任何失败不得打断报价主路径（异常在此吞掉并告警）。
     *
     * @param products        一轮报价转换出的产品；identity 缺席或含 UNKNOWN 成分的会被跳过
     * @param requestSupplier 请求方供应商——产品未带 supplierId 时的回落（口径同 productToCache）
     */
    public void upsert(List<ProductRespDTO> products, Supplier requestSupplier) {
        try {
            if (products == null || products.isEmpty()) {
                return;
            }
            Map<Integer, List<ProductRespDTO>> bySupplier = new LinkedHashMap<>();
            for (ProductRespDTO product : products) {
                Integer code = product.getSupplierId() != null ? product.getSupplierId()
                        : (requestSupplier == null ? null : requestSupplier.getSupplierId());
                if (code == null) {
                    continue;
                }
                bySupplier.computeIfAbsent(code, k -> new java.util.ArrayList<>()).add(product);
            }
            bySupplier.forEach(this::upsertForSupplier);
        } catch (Exception e) {
            log.warn("产品建档：本批写入失败,不影响报价主路径", e);
        }
    }

    private void upsertForSupplier(int supplierCode, List<ProductRespDTO> products) {
        SupplierSourceEnum supplier = SupplierSourceEnum.getEnum(supplierCode);
        if (supplier == null) {
            // 枚举外的旧代码供应商：无申报无开关键，无从建档
            return;
        }
        String name = supplier.name().toLowerCase(Locale.ROOT);
        boolean enabled = environment.getProperty(
                "supplier." + name + ".catalog-enabled", Boolean.class, false);
        AtomicBoolean shouldLogGate = gateLogged.computeIfAbsent(supplierCode, k -> new AtomicBoolean(true));
        if (!enabled) {
            // 闸口的 REJECT 分支必须可检索(§3.8.4)，但每轮被调数千次，
            // 逐次打会淹掉日志——按开关状态只在翻转后打第一次
            if (shouldLogGate.compareAndSet(true, false)) {
                log.info("[gate] supplier.{}.catalog-enabled=false，跳过建档", name);
            }
            return;
        }
        shouldLogGate.set(true);
        // 报价码腐性问申报注册表（R-4.1 未申报即抛，由外层吞掉并连带告警）
        boolean quoteCodeStable = SupplierIdentityProfile.forCode(supplierCode).quoteCodeStability()
                == SupplierIdentityProfile.QuoteCodeStability.STABLE;
        String operator = name + "-refresh";

        int upserted = 0;
        int skippedUnknown = 0;
        int skippedNoKey = 0;
        for (ProductRespDTO product : products) {
            // 两个成因必须分开计(§6.2.2)：缺 productKey 是派生失败，矛头指向该家 deriver
            // 或它的入参；UNKNOWN 是解析覆盖不足，矛头指向餐食/退改的表外取值
            ProductIdentity identity = product.getIdentity();
            if (identity == null || StringUtils.isBlank(product.getProductKey())) {
                skippedNoKey++;
                continue;
            }
            // UNKNOWN 照常参与实时链路（key 合法、可售），但不进目录（R-5.4）。
            // 判决读自 identity 成分——deriver 派生时已判过，此处不得拿 Meal/CancelPolicy
            // 重判（R-2.8，重判必然降维且与派生器分叉）
            if ("UNKNOWN".equals(identity.mealSignature())
                    || CancelClass.UNKNOWN.name().equals(identity.cancelClass())) {
                skippedUnknown++;
                continue;
            }
            try {
                // 全部照抄派生器的产物（R-2.8）：本处一个判定都不做
                HashMap<String, Object> p = new HashMap<>();
                p.put("supplierId", supplierCode);
                p.put("productKey", identity.productKey());
                p.put("supplierAccount", identity.account());
                p.put("supplierHotelId", identity.supplierHotelId());
                p.put("supplierRoomId", identity.supplierRoomId());
                p.put("mealSignature", identity.mealSignature());
                p.put("cancelClass", identity.cancelClass());
                p.put("occupancy", identity.occupancy());
                p.put("supplierProductName", product.getProductInfo() == null
                        ? null : product.getProductInfo().getProductName());
                // 易腐报价码 hint 恒 null（R-2.3）——落库即"从库里读凭证"
                p.put("supplierQuoteHint", quoteCodeStable ? product.getProductId() : null);
                p.put("operator", operator);
                productCatalogMapper.upsertSupplierProductBase(p);
                upserted++;
            } catch (Exception e) {
                // 传 e 而非 e.toString()：后者吃掉 cause 链（§6.1.1.1 异常堆栈 100% 保留）
                log.warn("产品建档：单条写入失败,supplier={},sHotelId={},productKey={}",
                        name, product.getHotelId(), product.getProductKey(), e);
            }
        }
        // 可数的业务指标不许只活在日志里(§3.9.1)：建档覆盖的增长是趋势曲线，
        // skippedNoKey 突增是 productKey 派生回归的早期警报
        Map<String, Object> tags = new HashMap<>(2);
        tags.put(MetricTags.SUPPLIER, supplier.name());
        Monitor.recordMany(MetricNames.CATALOG_UPSERTED, tags, upserted);
        Monitor.recordMany(MetricNames.CATALOG_SKIPPED_UNKNOWN, tags, skippedUnknown);
        Monitor.recordMany(MetricNames.CATALOG_SKIPPED_NO_KEY, tags, skippedNoKey);
        log.info("产品建档：supplier={},sHotelId={},upserted={},skippedUnknown={},skippedNoKey={}",
                name, products.get(0).getHotelId(), upserted, skippedUnknown, skippedNoKey);
    }
}
