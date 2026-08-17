package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 异常价拦截（docs/price-refresh.md F-7）：新价相对缓存中的上一轮价异常暴跌时，
 * 拒绝写入，保留旧价并落可检索记录。
 *
 * <p><b>为什么必须有</b>：供应商返回的价格此前被原样写入缓存，没有任何异常值防线。
 * 供应商侧一旦出数据错误（或我方解析出错），错价会一路走到客人面前——刷价写进缓存、
 * 列表页展示、验价若返回同一份错价则按错价成交，全链路无一处会质疑它。这是直接资损。
 *
 * <p>三系统对比（2026-08-17 核实，issue #70）：hotel-spa（本仓前身）有此防线；
 * tg-trip-cursor <b>没有</b>——其 {@code PriceSnapshotService} 只做变价记录，
 * 艺龙调用点注释原文"不用返回值"，算出的差异被直接丢弃。两套在跑的生产系统里
 * 只有一套有防护，cursor 至今未出事是"供应商数据没出过大错"，不是设计上防住了。
 *
 * <p><b>本类是供应商通用的</b>（price-refresh.md §1.1）：拦截判据只依赖新旧价两个数，
 * 与供应商协议无关。放在通用缓存写入路径上，接一家新供应商即自动受保护——
 * 反面是 hotel-spa 把刷价逻辑按供应商复制了五份。
 *
 * <p><b>只防跌不防涨</b>：涨价写入缓存的后果是"报价偏高、少卖"，而暴跌的后果是
 * "按错价成交、资损"。按 R-1.6 的元规则（赌错只许少卖，不许卖错），只在跌的方向设闸。
 */
@Slf4j
@Component
@RefreshScope
public class AbnormalPriceGuard implements InitializingBean {

    /**
     * 触发拦截的跌幅比例。0.5 = 新价低于旧价的一半才拦。
     *
     * <p>兜底取 0.5 而非更严的值：艺龙促销常见 20~30% 折扣（生产在售价分布实测），
     * 阈值定低会把真实促销大量误拦，客人看不到优惠。<b>宽松起步，据拦截记录再收紧</b>——
     * 反过来先严后松会在上线初期误杀真促销，且事后难以解释优惠为何消失。
     *
     * <p>取值域 [0, 1]；0 表示任何跌价都拦（等同停止更新价格，仅供排障用），
     * 1 表示不拦。
     */
    @Value("${cache.price.abnormal-drop-ratio:0.5}")
    private double dropRatio;

    /**
     * 参与拦截的最低基准价（分）。旧价低于此值时不拦——低价房本身波动大，
     * 且绝对损失有限，拦下的误判成本高于放行。
     *
     * <p>兜底 10000（100 元）取自艺龙生产在售价分布实测：&lt;100 元仅占 1.5%，
     * 即该下限覆盖 98.5% 的在售价。刻意不照搬 hotel-spa 的 200 元——那会把
     * 100~200 元这一大段（艺龙 100~300 元段占 29.7%）漏在保护之外。
     */
    @Value("${cache.price.abnormal-floor-cents:10000}")
    private int floorCents;

    @Override
    public void afterPropertiesSet() {
        if (dropRatio < 0 || dropRatio > 1) {
            throw new IllegalStateException(
                    "cache.price.abnormal-drop-ratio must be between 0 and 1, but was " + dropRatio);
        }
        if (floorCents < 0) {
            throw new IllegalStateException(
                    "cache.price.abnormal-floor-cents must not be negative, but was " + floorCents);
        }
        log.info("异常价拦截配置: dropRatio={}, floorCents={}分", dropRatio, floorCents);
    }

    /**
     * 判定新价是否异常暴跌、应当拒绝写入。
     *
     * <p>放行的三种情形（都不是"确证正常"，而是"没有依据拦截"）：
     * <ul>
     *   <li>无旧价基准——首次刷该产品，没有可比对象</li>
     *   <li>旧价低于下限——低价房波动大，绝对损失有限</li>
     *   <li>新价不低于旧价——涨价不在本闸管辖范围</li>
     * </ul>
     *
     * @param oldCents 缓存中的上一轮价（分）；无旧价传 null 或非正值
     * @param newCents 本次刷到的新价（分）
     */
    public boolean isAbnormalDrop(Integer oldCents, Integer newCents) {
        if (oldCents == null || oldCents <= 0 || newCents == null || newCents <= 0) {
            return false;
        }
        if (oldCents < floorCents) {
            return false;
        }
        if (newCents >= oldCents) {
            return false;
        }
        double drop = (double) (oldCents - newCents) / oldCents;
        return drop > dropRatio;
    }

    /**
     * 落拦截记录（F-7.2）。键值对形态、带业务主键与关键数值，供事后判定
     * 是供应商数据异常还是真实促销（§6.1.2）。
     *
     * <p>级别取 warn 而非 info：它表示"我方主动拒绝了供应商数据、缓存维持旧值"，
     * 属降级运行，须写明降成了什么（§6.4）。
     */
    public void logIntercepted(String hotelId, String productId, String date,
                               int oldCents, int newCents) {
        log.warn("异常价拦截：新价暴跌，拒绝写入缓存并保留旧价,hotelId={},productId={},date={},旧价={}分,新价={}分,跌幅={}%,阈值={}%",
                hotelId, productId, date, oldCents, newCents,
                Math.round((double) (oldCents - newCents) * 100 / oldCents),
                Math.round(dropRatio * 100));
    }

    /** 供调用方在轮次汇总里输出；也是判断阈值是否需要调整的直接依据 */
    public double getDropRatio() {
        return dropRatio;
    }

    public int getFloorCents() {
        return floorCents;
    }
}
