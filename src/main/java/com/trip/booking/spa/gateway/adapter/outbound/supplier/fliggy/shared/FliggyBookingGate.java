package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 飞猪下单安全护栏（PROJECT.md §3.2.3、§3.8）：false 即拒绝下单，供应商侧不发生任何动作。
 *
 * <p><b>为什么单独一个类</b>：护栏的键是 {@code fliggy.booking-enabled}，属 application.yml 域，
 * 改它必须发版并过评审；而 {@link FliggyProperties} 整体绑在 {@code supplier.fliggy.*}（Nacos 域，
 * 配置台上点一下就改）。两者放在同一个类里，护栏就会跟着那个前缀跑到配置台上去——这正是
 * 本次要修的病。拆开之后，"改它要不要发版"这件事由类的归属直接决定，不靠人记得写对前缀。
 *
 * <p>顺带避开两个具体的坑：其一，若把 {@code @Value} 字段留在 {@code @ConfigurationProperties}
 * 类里，Nacos 的同名键会在 {@code @Value} 注入<b>之后</b>再绑一次，把护栏悄悄盖掉；其二，
 * 若只留一个只读的 {@code isBookingEnabled()}，Binder 遇到遗留键会<b>硬失败</b>
 * （{@code No setter found for property}），整个 bean 建不出来、应用起不来——而本仓合并即
 * 自动部署、无人工审批，那就是一次自伤式停服。
 *
 * <p><b>飞猪没有沙箱</b>——唯一可用端点就是生产 TOP 网关（eco.taobao.com），故本开关不存在
 * "先在沙箱验一遍"的中间态：开即真单真扣款。默认关。
 *
 * <p><b>§3.8.5 三项声明</b>
 * <ul>
 *   <li><b>误开的后果</b>：向飞猪提交真实订单并真实扣款，无沙箱可退。2026-09-09 有人为试真单
 *       在配置台点开，注释写着"测完关回 false"，到 2026-09-14 被发现时已连续开了五天</li>
 *   <li><b>误关的后果</b>：飞猪下单一律确定失败（FAILED 而非 UNKNOWN，供应商侧无任何动作），
 *       上游可直接终结订单退款；查价／验价／查单／取消均不受影响</li>
 *   <li><b>生效执行面</b>：全部承载 /booking 流量的节点，与 profile 无关，仅飞猪链路。
 *       取消刻意不设闸：已存在的真单必须永远可撤</li>
 * </ul>
 */
@Slf4j
@Component
public class FliggyBookingGate {

    /** 已失效的遗留键，见 {@link #warnOnLegacyKey} */
    static final String LEGACY_KEY = "supplier.fliggy.booking-enabled";

    @Value("${fliggy.booking-enabled:false}")
    private boolean open;

    /** 只用来查遗留键在不在，不参与取值 */
    @Resource
    private Environment environment;

    /** 护栏是否放行。唯一取值入口（§3.8.2 一事一闸） */
    public boolean isOpen() {
        return open;
    }

    /**
     * 遗留键的清理提示。{@link #LEGACY_KEY} 自本次改动起不再有任何作用，但它还留在生产 Nacos 里
     * （删键的时机见附录 A：必须等删掉读取方的版本真正上了生产之后）。在那之前，配置台上看到的值
     * 与真实护栏状态可以不一致，这本身就是事故温床：有人可能以为把它改回 true 就开了闸。
     *
     * <p><b>只告警不阻止启动</b>——阻止启动等于把一次配置清理变成一次停服，而合并即自动部署，
     * 没有人工闸口来挡。
     */
    private void warnOnLegacyKey() {
        if (environment == null || !environment.containsProperty(LEGACY_KEY)) {
            return;
        }
        log.error("[gate] Nacos 里仍存在已失效的遗留键 {}（当前值={}），它不再控制任何东西。"
                        + "飞猪下单护栏现由 application.yml 的 fliggy.booking-enabled 决定，当前为 {}。"
                        + "请在本版本确认上生产后从 Nacos 删除该键，并同步删掉 "
                        + "config/nacos/pending-removal.txt 里的对应行（PROJECT.md 附录 A）。",
                LEGACY_KEY, environment.getProperty(LEGACY_KEY), open);
    }

    @PostConstruct
    void logStartupState() {
        warnOnLegacyKey();
        // 飞猪无沙箱：open=true 即真单真扣款，启动日志必须可查（与另三家同形）
        log.info("飞猪下单护栏: fliggy.booking-enabled={}", open);
    }
}
