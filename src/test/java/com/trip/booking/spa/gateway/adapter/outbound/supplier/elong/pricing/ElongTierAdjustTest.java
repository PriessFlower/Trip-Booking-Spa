package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ElongQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService.RefreshOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 艺龙调档=模板偏移算法（与飞猪同一套）：无货=业务档+10、有货 -10 回原档、
 * 失败不动档。人工停用位 9 不进 tiers 永不触发本逻辑。
 */
class ElongTierAdjustTest {

    private ElongCPSQueryPriceServiceImpl service;
    private ElongQueryPriceTaskMapper mapper;

    @BeforeEach
    void setUp() {
        service = new ElongCPSQueryPriceServiceImpl();
        mapper = Mockito.mock(ElongQueryPriceTaskMapper.class);
        ReflectionTestUtils.setField(service, "elongQueryPriceTaskMapper", mapper);
    }

    private static ElongQueryPriceTask row(long id, int priority) {
        ElongQueryPriceTask t = new ElongQueryPriceTask();
        t.setId(id);
        t.setPriorityLevelNumber(priority);
        return t;
    }

    @Test
    @DisplayName("近档无货 → 沉到 10;远档无货位刷出有货 → 回业务档 2")
    void offsetRoundTrip() {
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(1L, 0), RefreshOutcome.EMPTY);
        verify(mapper).updatePriority(1L, 10);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(2L, 12), RefreshOutcome.ON_SALE);
        verify(mapper).updatePriority(2L, 2);
    }

    @Test
    @DisplayName("失败不动档;同档不写库")
    void failedAndSameTierDoNotWrite() {
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(3L, 1), RefreshOutcome.FAILED);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(4L, 2), RefreshOutcome.ON_SALE);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(5L, 11), RefreshOutcome.EMPTY);
        verify(mapper, never()).updatePriority(Mockito.anyLong(), Mockito.anyInt());
    }
}
