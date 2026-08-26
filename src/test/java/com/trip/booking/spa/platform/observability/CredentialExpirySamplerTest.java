package com.trip.booking.spa.platform.observability;

import com.trip.booking.spa.gateway.domain.supplier.CredentialExpiry;
import com.trip.booking.spa.gateway.domain.supplier.CredentialRenewal;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 凭据到期监控的两条命脉：
 *
 * <ol>
 *   <li>申报 HUMAN_ONLY 却不供 {@link CredentialExpiry} → 启动即拒——承诺了监控
 *       却不给数据来源，与「配置写了却绑不上」同病，等到期就是又一次
 *       「飞猪 session 过期被当集成死查两个月」</li>
 *   <li>剩余天数 gauge 按 supplier/renewal 出数，可为负（已过期）——告警规则
 *       （spa.yml SupplierCredentialExpiringSoon）读的就是它</li>
 * </ol>
 */
class CredentialExpirySamplerTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    private static CredentialExpiry expiry(SupplierSourceEnum supplier, Instant at) {
        return new CredentialExpiry() {
            @Override
            public SupplierSourceEnum supplier() {
                return supplier;
            }

            @Override
            public Instant expiresAt() {
                return at;
            }
        };
    }

    /** 只实现测试所需的两个方法；生产代码只用 stream()/forEach */
    private static ObjectProvider<CredentialExpiry> providerOf(List<CredentialExpiry> list) {
        return new ObjectProvider<>() {
            @Override
            public CredentialExpiry getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CredentialExpiry getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CredentialExpiry getIfAvailable() {
                return list.isEmpty() ? null : list.get(0);
            }

            @Override
            public CredentialExpiry getIfUnique() {
                return list.size() == 1 ? list.get(0) : null;
            }

            @Override
            public Stream<CredentialExpiry> stream() {
                return list.stream();
            }
        };
    }

    @Test
    @DisplayName("剩余天数按 supplier/renewal 出 gauge；已过期为负数")
    void daysLeftIsPublished() {
        CredentialExpirySampler sampler = new CredentialExpirySampler(
                providerOf(List.of(expiry(SupplierSourceEnum.ELONG, NOW.plus(Duration.ofDays(30))))));
        ReflectionTestUtils.setField(sampler, "clock", Clock.fixed(NOW, ZoneOffset.UTC));

        sampler.sample();

        assertEquals(30.0, registry.get("supplier_credential_days_left_value")
                .tags("supplier", "ELONG", "renewal", "stateless").gauge().value());
    }

    @Test
    @DisplayName("已过期 → 负数,不是消失——消失和健康长得一样")
    void expiredIsNegativeNotAbsent() {
        CredentialExpirySampler sampler = new CredentialExpirySampler(
                providerOf(List.of(expiry(SupplierSourceEnum.ELONG, NOW.minus(Duration.ofDays(2))))));
        ReflectionTestUtils.setField(sampler, "clock", Clock.fixed(NOW, ZoneOffset.UTC));

        sampler.sample();

        assertEquals(-2.0, registry.get("supplier_credential_days_left_value")
                .tags("supplier", "ELONG").gauge().value());
    }

    @Test
    @DisplayName("申报 HUMAN_ONLY 却没有 CredentialExpiry → 启动即拒")
    void humanOnlyWithoutExpirySourceRefusesBoot() {
        // 纯函数直测:现网还没有 HUMAN_ONLY 的家,借 EXPEDIA 编码造申报输入
        Map<SupplierSourceEnum, CredentialRenewal> declared =
                Map.of(SupplierSourceEnum.EXPEDIA, CredentialRenewal.HUMAN_ONLY);

        assertThrows(IllegalStateException.class,
                () -> CredentialExpirySampler.requireExpirySources(declared, Set.of()));
        assertDoesNotThrow(
                () -> CredentialExpirySampler.requireExpirySources(declared, Set.of(SupplierSourceEnum.EXPEDIA)));
    }

    @Test
    @DisplayName("STATELESS 不要求供给——无会话即无到期,缺席是正确状态")
    void statelessNeedsNoSource() {
        assertDoesNotThrow(() -> CredentialExpirySampler.requireExpirySources(
                Map.of(SupplierSourceEnum.ELONG, CredentialRenewal.STATELESS), Set.of()));
    }
}
