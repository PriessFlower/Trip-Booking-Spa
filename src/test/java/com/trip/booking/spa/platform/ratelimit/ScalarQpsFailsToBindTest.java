package com.trip.booking.spa.platform.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 钉住一条踩过的事实：{@code ratelimit.qps} 是 map 类型，Nacos 里给一个<b>标量</b>
 * （旧格式的一整行 JSON 字符串）会让绑定<b>硬失败</b>，服务起不来。
 *
 * <p><b>为什么值得一条测试</b>：2026-08-25 发版时我们以为"代码同时认 map 和 JSON 字符串，
 * 所以先发版还是先改 Nacos 都安全"。这是错的——绑定发生在 {@code @PostConstruct} <b>之前</b>，
 * 那个读 JSON 的 {@code @Value} 兜底字段根本执行不到：bean 造不出来，context 直接起不来。
 * 结果是新镜像上生产后崩溃重启，直到把 Nacos 转成 map 才恢复。
 *
 * <p>所以对这类 map 型配置，<b>没有"双格式兼容"这回事</b>：Nacos 的形态必须与代码的类型
 * 一致，切换是一次原子的配置改动，不能靠代码兜住。那个兜底字段已删除；本测试留下来，
 * 免得下一个人再写一个"兼容分支"并相信它有用。
 */
class ScalarQpsFailsToBindTest {

    @Test
    @DisplayName("给标量值：绑定必须失败——这就是那次生产崩溃的成因")
    void scalarValueFailsToBind() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "ratelimit.default-qps", "1",
                "ratelimit.qps", "{\"GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES\":15}"));

        assertThrows(BindException.class,
                () -> new Binder(source).bind("ratelimit", RateLimitProperties.class).get(),
                "标量竟然绑上了 map。若真如此，说明有人加了转换器——"
                        + "那就得重新评估「Nacos 形态必须与代码类型一致」这条结论，"
                        + "以及发版与改配置的先后顺序");
    }

    @Test
    @DisplayName("给 map：正常绑定，键含冒号故必须用方括号")
    void mapValueBinds() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "ratelimit.default-qps", "1",
                "ratelimit.qps[GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES]", "15"));

        RateLimitProperties bound = new Binder(source).bind("ratelimit", RateLimitProperties.class).get();

        assertEquals(15d,
                bound.getQps().get("GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES"),
                "方括号键没绑进来。裸键里的冒号会被宽松绑定吃掉，查表必然 miss 并静默回落 "
                        + "default-qps——见 RateLimitKeyBindingTest");
    }
}
