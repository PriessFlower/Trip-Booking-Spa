package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端点的 {@code @RequestBody} 只许绑 ① 的 wire 类型，不许绑领域指令。
 *
 * <p><b>为什么要一条测试盯着</b>：领域指令（PriceQuery / CheckPriceCommand / BookingCommand…）
 * 刻意造成不可变——私有构造 + Builder，<b>没有无参构造</b>。Jackson 反序列化不了它，
 * 于是端点在运行期直接 500，而编译期一声不吭。
 *
 * <p>2026-09-11 查价解耦时 {@code /check} 就这么中招：批量改名把 {@code @RequestBody}
 * 的类型一并改成了 {@code CheckPriceCommand}，590 条单元测试全绿——因为<b>没有一条
 * 往端点 POST 过真 JSON</b>。是发布前整批实跑（§2.2.5）照出来的。
 *
 * <p>本测试按类型的<b>包</b>判定，不按名字：翻译的方向是 ① 收 JSON、往里传领域指令，
 * 故 {@code @RequestBody} 的类型必须住在 {@code adapter.inbound.rest.request}。
 */
class RequestBodyBindsWireTypeTest {

    private static final String WIRE_PACKAGE = "com.trip.booking.spa.gateway.adapter.inbound.rest.request";

    @Test
    @DisplayName("每个 @RequestBody 参数都必须是 ① 的 wire 类型（否则 Jackson 造不出来，运行期 500）")
    void everyRequestBodyIsAWireType() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> controller : List.of(SpaController.class)) {
            for (Method m : controller.getDeclaredMethods()) {
                for (Parameter p : m.getParameters()) {
                    if (!p.isAnnotationPresent(RequestBody.class)) {
                        continue;
                    }
                    Package pkg = p.getType().getPackage();
                    if (pkg == null || !WIRE_PACKAGE.equals(pkg.getName())) {
                        offenders.add(controller.getSimpleName() + "." + m.getName()
                                + " 绑了 " + p.getType().getName());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "@RequestBody 只许绑 " + WIRE_PACKAGE + " 下的类型；领域指令没有无参构造，"
                        + "Jackson 反序列化会在运行期抛 InvalidDefinitionException：" + offenders);
    }
}
