package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.order;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.mapping.OrderQueryMapping;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaBookingContact;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.order.OrderQueryCommand;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 查单在真链路上的端到端：Expedia 沙箱 → 通道层 → 适配层 → 领域结果 → 对外 JSON。
 *
 * <p><b>为什么需要它</b>：查单能力刚从 REST DTO 切到领域模型（{@code OrderQueryCommand} /
 * {@code OrderQueryResult}），单测用的是夹具报文，证明不了真实响应经过新的转换链后
 * 三态与字段仍然对。本测试打的是真实沙箱，走完整条链。
 *
 * <p><b>只读、不产生真单与费用</b>：只调 {@code GET /v3/itineraries}，从不调下单接口。
 * 用一个随机的、必然不存在的我方单号反查——Expedia 对此返回空数组，正是 NOT_FOUND 那一态。
 * 这一态恰恰是最要紧的：它是唯一允许上游重新下单的答复，判错即重复下单。
 *
 * <p>凭据取自环境变量（见 .env.example），缺任何一项即跳过。开关 {@code EXPEDIA_E2E=1}。
 */
@EnabledIfEnvironmentVariable(named = "EXPEDIA_E2E", matches = "1")
class ExpediaOrderQueryE2ETest {

    /** 通道层取过的限流键，按取用顺序；由本类装的假限流中枢填 */
    private static final List<String> TAKEN = new ArrayList<>();

    private static ExpediaOrderQuerySyncServiceImpl service;

    @BeforeAll
    static void wireRealService() throws Exception {
        String apiKey = System.getenv("EXPEDIA_API_KEY");
        String secret = System.getenv("EXPEDIA_SHARED_SECRET");
        String host = System.getenv("EXPEDIA_API_HOST");
        String email = System.getenv("EXPEDIA_E2E_EMAIL");
        assumeTrue(notBlank(apiKey) && notBlank(secret) && notBlank(host),
                "缺 EXPEDIA_API_KEY / EXPEDIA_SHARED_SECRET / EXPEDIA_API_HOST，跳过");
        assumeTrue(notBlank(email),
                "缺 EXPEDIA_E2E_EMAIL：查单邮箱必须与下单时一致，否则 Expedia 不返回结果");

        ExpediaRapidProperties props = new ExpediaRapidProperties();
        set(props, "apiKey", apiKey);
        set(props, "sharedSecret", secret);

        // 限流中枢平时由 Spring 启动时抄进静态桥；这里没起容器，手动装一个全放行的实现，
        // 否则通道层拿到 null 直接 NPE。放行而不真限流：本测试只打一次。
        // 但它记录取过哪些键——真链路上通道层按什么键扣格，只有这里验得到。
        TAKEN.clear();
        RateLimitHolder holder = new RateLimitHolder();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(new RateLimitManager() {
            @Override
            public void acquire(String key) {
                TAKEN.add(key);
            }

            @Override
            public boolean tryAcquire(String key) {
                TAKEN.add(key);
                return true;
            }

            @Override
            public boolean isRegistered(String key) {
                return true;
            }
        });
        holder.setApplicationContext(ctx);

        ExpediaBookingContact contact = new ExpediaBookingContact();
        set(contact, "bookingContactJson", "{\"email\":\"" + email + "\"}");
        invokeInit(contact);

        service = new ExpediaOrderQuerySyncServiceImpl();
        set(service, "host", host);
        set(service, "sessionId", orBlank(System.getenv("EXPEDIA_CUSTOMER_SESSION_ID")));
        set(service, "ownIp", orBlank(System.getenv("EXPEDIA_CUSTOMER_IP")));
        set(service, "expediaUtils", new ExpediaUtils(props));
        set(service, "bookingContact", contact);
        // rateLimiter 只是 QueryOrderAccess 的构造入参，其构造函数并未持有它，故可留空
    }

    /**
     * 必然不存在的单号 → 供应商回空数组 → NOT_FOUND，且经翻译后的对外 JSON 无状态码。
     *
     * <p>走的是模板入口 {@code orderQuery(OrderQueryCommand)}，即生产的真实路径：
     * 模板兜底 → 适配层转换 → 领域结果 → {@code OrderQueryMapping} 出 DTO。
     */
    @Test
    @DisplayName("真沙箱：查一个不存在的单，三态为 NOT_FOUND 且对外形状正确")
    void absentOrderIsReportedAsNotFoundThroughTheWholeChain() {
        // 28 字符是 affiliate_reference_id 的硬上限（2026-09-11 沙箱实测：超限回 400
        // invalid_input / affiliate_reference_id.invalid_exceeds_char_limit）。
        // 造超长单号会把本测试变成"验错误分支"，验不到 NOT_FOUND 那一态
        String absentOrderId = ("E2E-" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);

        OrderQueryResult result = service.orderQuery(
                OrderQueryCommand.of(10004, absentOrderId, null));

        assertThat(result).isNotNull();
        assertThat(result.presence())
                .as("空数组是「确实没有这笔订单」；判成 INDETERMINATE 会让上游一直重试，"
                        + "判成 FOUND 更糟。这一态是唯一允许重新下单的答复")
                .isEqualTo(OrderPresence.NOT_FOUND);

        OrderRespDTO dto = OrderQueryMapping.toDto(result);
        assertThat(dto.getPresence()).isEqualTo(OrderPresence.NOT_FOUND);
        assertThat(dto.getOrderStatus()).as("没查到就没有状态可报，不得取默认值").isNull();
        assertThat(dto.getSupplierOrderId()).isNull();

        assertThat(TAKEN)
                .as("通道层必须按 QUERY_ORDER 的接口桶扣格——两级桶拼键是否正确，只有真链路验得到")
                .anyMatch(k -> k.contains("EXPEDIA") && k.contains("QUERY_ORDER"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String orBlank(String s) {
        return s == null ? "" : s;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = findField(target.getClass(), field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** ExpediaBookingContact 的 JSON 在 @PostConstruct 里解析，容器外要手动触发 */
    private static void invokeInit(ExpediaBookingContact contact) throws Exception {
        for (java.lang.reflect.Method m : ExpediaBookingContact.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(javax.annotation.PostConstruct.class)) {
                m.setAccessible(true);
                m.invoke(contact);
                return;
            }
        }
    }
}
