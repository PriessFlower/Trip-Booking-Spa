package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService.RefreshOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Expedia 刷价的分档与调档，与艺龙/飞猪同一套模板偏移算法。
 *
 * <p>此前 Expedia 是三家里唯一没对齐的：{@code tiers()} 只有 {@code List.of(0)}、
 * 批量与并发不分档、{@code adjustPriority} 根本没实现——满房的店不会沉入慢车道，
 * 一直按同频消耗额度；而生产任务表 2,340 行也全趴在 0 档。
 */
class ExpediaTierAdjustTest {

    private ExpediaCPSQueryPriceServiceImpl service;
    private ExpediaQueryPriceTaskMapper mapper;

    @BeforeEach
    void setUp() {
        service = new ExpediaCPSQueryPriceServiceImpl();
        mapper = Mockito.mock(ExpediaQueryPriceTaskMapper.class);
        ReflectionTestUtils.setField(service, "expediaQueryPriceTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "environment", new MockEnvironment());
    }

    private static ExpediaQueryPriceTask row(long id, int priority) {
        ExpediaQueryPriceTask t = new ExpediaQueryPriceTask();
        t.setId(id);
        t.setPriorityLevelNumber(priority);
        return t;
    }

    /** 档位集必须与艺龙/飞猪一致：三个业务档 + 三个无货位 */
    @Test
    @DisplayName("tiers 覆盖 0/1/2 与各自无货位 10/11/12")
    void tiersCoverBusinessAndSoldOutSlots() {
        @SuppressWarnings("unchecked")
        List<Integer> tiers = (List<Integer>) ReflectionTestUtils.invokeMethod(service, "tiers");
        assertEquals(List.of(0, 1, 2, 10, 11, 12), tiers,
                "只有 0 档＝住期不分档、无货位无人消费，沉进去的行再也刷不到");
    }

    @Test
    @DisplayName("近档无货 → 沉到 10;远档无货位刷出有货 → 回业务档 2")
    void offsetRoundTrip() {
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(1L, 0), RefreshOutcome.EMPTY);
        verify(mapper).updatePriority(1L, 10);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(2L, 12), RefreshOutcome.ON_SALE);
        verify(mapper).updatePriority(2L, 2);
    }

    /** F-5.1：一次网络抖动不该把店打进慢车道 */
    @Test
    @DisplayName("失败不动档;同档不写库")
    void failedAndSameTierDoNotWrite() {
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(3L, 1), RefreshOutcome.FAILED);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(4L, 2), RefreshOutcome.ON_SALE);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(5L, 11), RefreshOutcome.EMPTY);
        verify(mapper, never()).updatePriority(Mockito.anyLong(), Mockito.anyInt());
    }

    /** 批量与并发必须按档取键，否则远近住期一个待遇 */
    @Test
    @DisplayName("批量/并发按档取不同的 Nacos 键")
    void sizingIsPerTier() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("task.expedia-cps.batch-size", "11");
        env.setProperty("task.expedia-cps.mid-batch-size", "22");
        env.setProperty("task.expedia-cps.far-batch-size", "33");
        env.setProperty("task.expedia-cps.slow-batch-size", "44");
        env.setProperty("task.expedia-cps.concurrency", "1");
        env.setProperty("task.expedia-cps.mid-concurrency", "2");
        env.setProperty("task.expedia-cps.far-concurrency", "3");
        env.setProperty("task.expedia-cps.slow-concurrency", "4");
        ReflectionTestUtils.setField(service, "environment", env);

        assertEquals(11, call("batchSize", 0));
        assertEquals(22, call("batchSize", 1));
        assertEquals(33, call("batchSize", 2));
        assertEquals(44, call("batchSize", 11), "无货位共用 slow-*——沉进去的店该少刷");

        assertEquals(1, call("concurrency", 0));
        assertEquals(2, call("concurrency", 1));
        assertEquals(3, call("concurrency", 2));
        assertEquals(4, call("concurrency", 12));
    }

    /** invokeMethod 返回泛型，直接喂 assertEquals 会撞上重载歧义 */
    private int call(String method, int priority) {
        Integer v = ReflectionTestUtils.invokeMethod(service, method, priority);
        return v == null ? Integer.MIN_VALUE : v;
    }
}
