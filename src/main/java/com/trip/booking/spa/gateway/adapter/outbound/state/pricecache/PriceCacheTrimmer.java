package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入缓存前的裁剪（docs/price-refresh.md F-3）：按 productKey 分组，每组只保留最低价的前 N 条。
 *
 * <p><b>为什么必须裁</b>：全量存储不可行。艺龙生产实测单酒店单日期返回 102~114 条产品，
 * 按 2,056 家有货酒店 × 30 天窗口计约 9.9 GB Redis。
 *
 * <p><b>为什么分组键是 productKey 而不是"含早/不含早"</b>：这是与 tg-trip-cursor 的
 * 关键分歧。其 {@code capLowestPerBreakfastGroup} 按含早与否分两组、每组留最低价 2 条，
 * 粒度<b>比 productKey 粗</b>——同房型、同含早、但退改条款不同的两条产品会被归进同一组，
 * 于是"贵一些但可免费取消"的那条大概率被裁掉。后果是该卖法在比价中整体消失，客人看不到；
 * resolve 换票时这个等价类也没有票可换。
 *
 * <p>productKey 的等价类是（房型 + 餐食 + 退改类 + 占用）（R-1.1），按它分组能保证
 * <b>每个卖法都有代表留下来</b>。实测量级：102 条 → 35 个等价类，留 2 条约 70 条，
 * 仍是 cursor 的 12.7 条/店/日期的数倍，换来的是卖法覆盖从约 4 种升到全部等价类。
 *
 * <p><b>每组只留 1 条（2026-08-19 由 2 改为 1）</b>。原先留 2 条的理由是"为 resolve
 * 换票留备份票"，该理由不成立：resolve 依 R-3.1 <b>只查供应商实时库存</b>，明令禁止
 * 从我方缓存取候选（cursor 实测旧票复验成功率 0%、自有陈缓存 8%），因此缓存里的备份票
 * 从来没有被换票读过。
 *
 * <p>改为 1 条更是<b>正确性要求</b>：价格缓存的字段名已改为 productKey
 * （见 PriceCacheServiceImpl#cacheField），同一等价类的多条会写进<b>同一个 field</b>，
 * 后写覆盖先写。本类按价<b>升序</b>保留，于是留 2 条时第二便宜的会覆盖最便宜的——
 * 与"同一卖法给最低价"正好相反。
 *
 * <p>同一卖法确实常有多张在售票（R-1.4，生产实测平均 2.52 张/卖法、55.7% 的卖法多于
 * 一张），但对外只需报最便宜的那一张；其余票在换票时由供应商实时库存提供。
 *
 * <p><b>与异常价拦截的语义差别</b>：被 {@link AbnormalPriceGuard} 拦下的产品要
 * <b>排除</b>出下架逻辑（我们不确定新价对错，故保留旧价）；而被本类裁掉的产品
 * <b>应当</b>走下架删除——那是我方主动选择不再提供该报价，客人拿旧 productId
 * 来查应得到"无价"，而不是一份不再维护的陈价。
 *
 * <p>本类<b>供应商通用</b>（price-refresh.md §1.1）：只依赖 productKey 与总价两个字段，
 * 与协议无关。反面是 hotel-spa——刷价逻辑按供应商复制了五份。
 */
@Slf4j
@Component
@RefreshScope
public class PriceCacheTrimmer {

    /**
     * 每个 productKey 等价类保留的条数。
     *
     * <p>取值 &le; 0 表示<b>不裁剪</b>（全量入缓存）——仅供排障或小规模场景使用，
     * 常态下违反 F-3.1。默认 2 见类注释。
     */
    @Value("${cache.price.keep-per-product-key:1}")
    private int keepPerKey;

    /**
     * 按 productKey 分组裁剪。入参不被修改，返回新列表。
     *
     * <p>productKey 缺席的产品（未派生键的供应商）以 productId 自成一组，
     * 等同于不裁——<b>宁可多存也不能误裁</b>：没有键就无从判断谁与谁等价，
     * 此时裁剪等于随机丢弃卖法。
     */
    public List<ProductRespDTO> trim(List<ProductRespDTO> products) {
        if (keepPerKey <= 0 || products == null || products.size() <= 1) {
            return products;
        }
        // LinkedHashMap 保证输出顺序稳定，便于比对日志与排障
        Map<String, List<ProductRespDTO>> byKey = new LinkedHashMap<>();
        for (ProductRespDTO product : products) {
            String groupKey = StringUtils.isNotBlank(product.getProductKey())
                    ? product.getProductKey()
                    : "no-key:" + product.getProductId();
            byKey.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(product);
        }
        if (byKey.size() == products.size()) {
            // 每条各自成组，裁剪无从发生（常见于未派生 productKey 的供应商）
            return products;
        }

        List<ProductRespDTO> kept = new ArrayList<>();
        int dropped = 0;
        for (Map.Entry<String, List<ProductRespDTO>> entry : byKey.entrySet()) {
            List<ProductRespDTO> group = entry.getValue();
            if (group.size() <= keepPerKey) {
                kept.addAll(group);
                continue;
            }
            // 价格升序；缺价的排最后、不占保留名额（F-3.4）
            group.sort(Comparator.comparing(ProductRespDTO::getTotalPrice,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            kept.addAll(group.subList(0, keepPerKey));
            dropped += group.size() - keepPerKey;
        }
        // §6.2.1：裁掉多少必须可观测，否则"缓存里为什么少了产品"无从排查
        log.info("入缓存裁剪：hotelId={},入参={}条,等价类={}个,保留={}条,裁掉={}条,每类留={}",
                products.get(0).getHotelId(), products.size(), byKey.size(), kept.size(), dropped, keepPerKey);
        return kept;
    }

    public int getKeepPerKey() {
        return keepPerKey;
    }
}
