package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.FliggyQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService.RefreshOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 档位流转=最后一次结果：无货=业务档+10(原档可复原)、有货 -10 回原档、失败不动档
 * （F-5.1 同款：网络抖动不许把店打进慢车道）。同档不写库。
 */
class FliggyTierAdjustTest {

    private FliggyCPSQueryPriceServiceImpl service;
    private FliggyQueryPriceTaskMapper mapper;

    @BeforeEach
    void setUp() {
        service = new FliggyCPSQueryPriceServiceImpl();
        mapper = Mockito.mock(FliggyQueryPriceTaskMapper.class);
        ReflectionTestUtils.setField(service, "fliggyQueryPriceTaskMapper", mapper);
    }

    private static FliggyQueryPriceTask row(long id, int priority) {
        FliggyQueryPriceTask t = new FliggyQueryPriceTask();
        t.setId(id);
        t.setPriorityLevelNumber(priority);
        return t;
    }

    @Test
    @DisplayName("业务档 0 刷到无货 → 沉到 10(原档藏在数值里)")
    void emptyDemotesToSlow() {
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(1L, 0), RefreshOutcome.EMPTY);
        verify(mapper).updatePriority(1L, 10);
    }

    @Test
    @DisplayName("无货位 12 刷出有货 → 回业务档 2(成交店待遇不丢)")
    void onSalePromotesToFast() {
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(2L, 12), RefreshOutcome.ON_SALE);
        verify(mapper).updatePriority(2L, 2);
    }

    @Test
    @DisplayName("失败不动档;同档不写库")
    void failedAndSameTierDoNotWrite() {
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(3L, 0), RefreshOutcome.FAILED);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(4L, 0), RefreshOutcome.ON_SALE);
        ReflectionTestUtils.invokeMethod(service, "adjustPriority", row(5L, 11), RefreshOutcome.EMPTY);
        verify(mapper, never()).updatePriority(Mockito.anyLong(), Mockito.anyInt());
    }
}
