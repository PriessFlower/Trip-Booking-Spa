package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ElongQueryPriceTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;

/** 批次4 反馈环判据:升档是增益路径,任何异常不得影响验价主流程(F-6)。 */
class HotFeedbackTest {

    @Test
    void upgradeFailureNeverBreaksCheckPrice() {
        ElongPriceServiceImpl service = new ElongPriceServiceImpl();
        ElongQueryPriceTaskMapper mapper = Mockito.mock(ElongQueryPriceTaskMapper.class);
        Mockito.when(mapper.upgradeByShId(anyString())).thenThrow(new RuntimeException("DB down"));
        ReflectionTestUtils.setField(service, "elongQueryPriceTaskMapper", mapper);
        assertDoesNotThrow(() -> service.markHotelHot("12168956"), "升档失败必须被吞掉");
    }

    @Test
    void upgradeCallsMapperWithHotelId() {
        ElongPriceServiceImpl service = new ElongPriceServiceImpl();
        ElongQueryPriceTaskMapper mapper = Mockito.mock(ElongQueryPriceTaskMapper.class);
        Mockito.when(mapper.upgradeByShId("12168956")).thenReturn(7);
        ReflectionTestUtils.setField(service, "elongQueryPriceTaskMapper", mapper);
        service.markHotelHot("12168956");
        Mockito.verify(mapper).upgradeByShId("12168956");
    }
}
