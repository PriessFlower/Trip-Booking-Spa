package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.content;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 艺龙建档的写入判据(R-2.6 / R-2.3 / R-5.4)。
 */
class ElongCatalogServiceTest {

    private ElongCatalogService service;
    private ProductCatalogMapper mapper;

    @BeforeEach
    void setUp() {
        service = new ElongCatalogService();
        mapper = Mockito.mock(ProductCatalogMapper.class);
        ElongProductKeyDeriver deriver = new ElongProductKeyDeriver();
        ElongProperties props = new ElongProperties();
        ReflectionTestUtils.setField(props, "user", "tgtrip");
        ReflectionTestUtils.setField(deriver, "properties", props);
        ReflectionTestUtils.setField(service, "productCatalogMapper", mapper);
        ReflectionTestUtils.setField(service, "productKeyDeriver", deriver);
        ReflectionTestUtils.setField(service, "catalogEnabled", true);
    }

    private static ProductRespDTO product(Meal meal, List<CancelPolicy> cancel) {
        return ProductRespDTO.builder()
                .hotelId("61832733").productId("62022758A19A7133205")
                .productKey("2a8c7eb804d4aabbccddeeff00112233445566778899aabbccddeeff00112233")
                .room(Room.builder().roomId("0033").roomName("大床房").build())
                .meal(meal).cancelPolicy(cancel)
                .build();
    }

    private static Meal breakfast() {
        Meal m = new Meal(); m.setCount(2); m.setLunchCount(0); m.setDinnerCount(0); return m;
    }

    private static List<CancelPolicy> freeCancel() {
        return List.of(CancelPolicy.builder().cancelType(1).type(RefundType.NO_DEDUCTION).before(36).build());
    }

    /** 身份列必须是 productKey;艺龙报价码申报易腐,hint 必须为 null(R-2.3) */
    @Test
    void identityColumnIsProductKeyAndHintStaysNull() {
        service.upsert(List.of(product(breakfast(), freeCancel())));

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper).upsertSupplierProductBase(cap.capture());
        HashMap<String, Object> p = cap.getValue();
        assertEquals(p.get("productKey") == null ? p.get("supplierProductId") : p.get("supplierProductId"),
                p.get("supplierProductId"));
        assertEquals("2a8c7eb804d4aabbccddeeff00112233445566778899aabbccddeeff00112233",
                p.get("supplierProductId"), "身份列必须是 productKey,不是供应商报价码");
        assertNull(p.get("supplierQuoteHint"), "艺龙报价码易腐,禁止落 hint 列(R-2.3)——否则又成'从库里读凭证'");
        assertEquals(10010, p.get("supplierId"));
        assertEquals("0033", p.get("supplierRoomId"));
        assertEquals(1, p.get("breakfast"));
        assertEquals(1, p.get("cancelType"));
        assertEquals("elong-refresh", p.get("operator"));
    }

    /**
     * 退改解析不出(空列表)→ UNKNOWN → 不进目录(R-5.4)。
     * 注意:这类产品的 productKey 是【合法非空】的(含 c:UNKNOWN),实时链路照常可售——
     * 所以建档侧不能只判"key 是否为空",必须问 deriver。这正是本测试要钉住的点。
     */
    @Test
    void unknownCancelPolicyNeverEntersCatalog() {
        service.upsert(List.of(product(breakfast(), List.of())));
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
        Mockito.verify(mapper, Mockito.never()).upsertGlobalProductSupplier(Mockito.any());
    }

    /** 餐食解析不出 → 同样不进目录 */
    @Test
    void unknownMealNeverEntersCatalog() {
        service.upsert(List.of(product(null, freeCancel())));
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /** 可取消但全程收费 = 第三种 UNKNOWN,从 DTO 表面看不出来,必须靠 deriver 判据挡住 */
    @Test
    void cancellableButAlwaysChargedIsUnknownToo() {
        List<CancelPolicy> allCharged = List.of(
                CancelPolicy.builder().cancelType(1).type(RefundType.DEDUCT_BY_AMOUNT).amount(5000).before(36).build());
        service.upsert(List.of(product(breakfast(), allCharged)));
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /** 开关默认关时不得写库 */
    @Test
    void disabledSwitchWritesNothing() {
        ReflectionTestUtils.setField(service, "catalogEnabled", false);
        service.upsert(List.of(product(breakfast(), freeCancel())));
        Mockito.verifyNoInteractions(mapper);
    }

    /** 建档是增益路径:写库抛异常不得打断刷价 */
    @Test
    void writeFailureNeverBreaksRefresh() {
        Mockito.when(mapper.upsertGlobalProductSupplier(Mockito.any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.upsert(List.of(product(breakfast(), freeCancel()))));
    }
}
