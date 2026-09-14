package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 飞猪下单护栏的<b>绑定形态</b>守护：它只许由 {@code fliggy.booking-enabled}（application.yml，
 * 改它要发版）决定，<b>不许</b>被 Nacos 域的 {@code supplier.fliggy.booking-enabled} 打开，
 * 而且遗留键存在时应用必须<b>照常启动</b>。
 *
 * <p>为什么必须用真实容器而不是 new 一个对象：这三条的成败全在 Spring 的绑定顺序与
 * 绑定失败语义上，手工构造对象一条都验不出来。写这个测试时它当场逮到两个真问题：
 * <ul>
 *   <li>护栏字段若留在 {@link FliggyProperties}（前缀 {@code supplier.fliggy}）里，Nacos 的同名键
 *       会在 {@code @Value} 注入<b>之后</b>再绑一次，把护栏悄悄盖掉——看起来搬了家，其实没搬</li>
 *   <li>若只把访问器留成只读的 {@code isBookingEnabled()}，Binder 遇到遗留键不是忽略而是
 *       <b>硬失败</b>（{@code No setter found for property: booking-enabled}），整个 bean 建不出来。
 *       本仓合并即自动部署，那等于上线瞬间停服</li>
 * </ul>
 * 最终解法是把护栏拆成独立的 {@link FliggyBookingGate}，与 Nacos 前缀彻底无关。
 *
 * <p>背景：这个护栏原本就挂在 Nacos 上。2026-09-09 有人为试真单点开，注释写着"测完关回
 * false"，到 2026-09-14 被发现时已连续开了五天。飞猪没有沙箱，开着就是真扣钱。
 */
class FliggyBookingGateBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @Import(FliggyBookingGate.class)
    @EnableConfigurationProperties(FliggyProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("两个键都不给：默认关——兜底必须取安全侧（§3.3.3）")
    void defaultsClosed() {
        runner.run(ctx -> assertThat(ctx.getBean(FliggyBookingGate.class).isOpen()).isFalse());
    }

    @Test
    @DisplayName("yml 域的 fliggy.booking-enabled=true：开——这是唯一能开它的键")
    void ymlKeyOpensIt() {
        runner.withPropertyValues("fliggy.booking-enabled=true")
                .run(ctx -> assertThat(ctx.getBean(FliggyBookingGate.class).isOpen()).isTrue());
    }

    @Test
    @DisplayName("Nacos 域的遗留键=true：护栏仍关，且容器照常启动（不能因遗留键起不来）")
    void legacyNacosKeyNeitherOpensItNorBreaksStartup() {
        runner.withPropertyValues(FliggyBookingGate.LEGACY_KEY + "=true")
                .run(ctx -> {
                    assertThat(ctx).as("遗留键让容器启动失败了——上线即停服").hasNotFailed();
                    assertThat(ctx.getBean(FliggyBookingGate.class).isOpen())
                            .as("配置台上的遗留键把安全护栏打开了")
                            .isFalse();
                });
    }

    @Test
    @DisplayName("两个键打架：yml 说关就是关，Nacos 盖不过它")
    void ymlWinsOverLegacyKey() {
        runner.withPropertyValues("fliggy.booking-enabled=false", FliggyBookingGate.LEGACY_KEY + "=true")
                .run(ctx -> assertThat(ctx.getBean(FliggyBookingGate.class).isOpen()).isFalse());
    }

    @Test
    @DisplayName("同前缀下的运维项照常热配，本次拆分没有波及它们")
    void opsKeysStillBind() {
        runner.withPropertyValues("supplier.fliggy.resolve-enabled=true",
                        "supplier.fliggy.session-ttl-days=45")
                .run(ctx -> {
                    FliggyProperties p = ctx.getBean(FliggyProperties.class);
                    assertThat(p.isResolveEnabled()).isTrue();
                    assertThat(p.getSessionTtlDays()).isEqualTo(45);
                });
    }
}
