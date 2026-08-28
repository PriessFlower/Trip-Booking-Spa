package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaContractProfile;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaProductKeyDeriver;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Expedia 建档的写入判据（R-2.6 / R-2.3 / R-5.4）。
 *
 * <p>与 {@code ElongCatalogServiceTest} 同构，两处差异是真差异、不是抄漏：
 * Expedia 报价码申报为 {@code STABLE}，故 {@code supplier_quote_hint} <b>要填</b>
 * （艺龙恒 null）；{@code supplierId}/{@code operator} 各按各家。
 */
class ExpediaCatalogServiceTest {

    private ExpediaCatalogService service;
    private ProductCatalogMapper mapper;

    @BeforeEach
    void setUp() {
        service = new ExpediaCatalogService();
        mapper = Mockito.mock(ProductCatalogMapper.class);
        ExpediaProductKeyDeriver deriver = new ExpediaProductKeyDeriver();
        ExpediaContractProfile profile = Mockito.mock(ExpediaContractProfile.class);
        Mockito.when(profile.getPartnerPointOfSale()).thenReturn("TEST_PPOS");
        deriver.setContractProfile(profile);
        ReflectionTestUtils.setField(service, "productCatalogMapper", mapper);
        ReflectionTestUtils.setField(service, "productKeyDeriver", deriver);
        ReflectionTestUtils.setField(service, "catalogEnabled", true);
    }

    /**
     * 用与生产同一条路径（deriver）算 identity，而不是手工拼成分——手工拼的话
     * 测试就成了"自己发明一套成分"，守不住真实链路。
     */
    private ProductRespDTO product(Meal meal, List<CancelPolicy> cancel) {
        ExpediaProductKeyDeriver deriver =
                (ExpediaProductKeyDeriver) ReflectionTestUtils.getField(service, "productKeyDeriver");
        ProductIdentity identity = deriver.deriveIdentity("15714685", "200414414", meal, cancel, "2");
        return ProductRespDTO.builder()
                .hotelId("15714685").productId("rate-abc-123")
                .productKey(identity.productKey()).identity(identity)
                .room(Room.builder().roomId("200414414").roomName("Standard Room").build())
                .productInfo(ProductInfo.builder().productName("Standard Room").build())
                .meal(meal).cancelPolicy(cancel)
                .build();
    }

    private static Meal breakfast() {
        Meal m = new Meal();
        m.setCount(2);
        m.setLunchCount(0);
        m.setDinnerCount(0);
        return m;
    }

    private static List<CancelPolicy> freeCancel() {
        return List.of(CancelPolicy.builder().cancelType(1).type(RefundType.NO_DEDUCTION).before(36).build());
    }

    /** 罚全款段（本次修复后 percent=100% 的落法），确定不可退——可进目录 */
    private static List<CancelPolicy> fullPenalty() {
        return List.of(CancelPolicy.builder()
                .cancelType(1).type(RefundType.DEDUCT_BY_PERCENT).value(100d).before(0).build());
    }

    /** 落库的每一列都必须<b>原样</b>来自 identity（R-2.8），成分不得降维（R-2.7） */
    @Test
    void everyColumnIsCopiedFromIdentityVerbatim() {
        ProductRespDTO p0 = product(breakfast(), freeCancel());
        service.upsert(List.of(p0));

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper).upsertSupplierProductBase(cap.capture());
        HashMap<String, Object> p = cap.getValue();
        ProductIdentity id = p0.getIdentity();

        assertEquals(id.productKey(), p.get("productKey"), "身份列必须是 productKey，不是供应商报价码");
        assertEquals(id.account(), p.get("supplierAccount"));
        assertEquals(id.supplierHotelId(), p.get("supplierHotelId"));
        assertEquals(id.supplierRoomId(), p.get("supplierRoomId"));
        assertEquals(id.mealSignature(), p.get("mealSignature"));
        assertEquals(id.cancelClass(), p.get("cancelClass"));
        assertEquals(id.occupancy(), p.get("occupancy"));
        assertEquals(10005, p.get("supplierId"));
        assertEquals("expedia-refresh", p.get("operator"));
        // 与艺龙的真差异：Expedia rate_id 申报 STABLE，hint 要填（R-2.3）
        assertEquals("rate-abc-123", p.get("supplierQuoteHint"),
                "Expedia 报价码申报稳定，hint 必须落——它是 resolve 的快速通道");

        // R-2.9：房型层与聚合域的列已随表重设计移除，不得再出现在写入载荷里
        assertNull(p.get("hasWindow"));
        assertNull(p.get("supplierBedDesc"));
        assertNull(p.get("productId"));
        assertNull(p.get("roomId"));
        assertNull(p.get("hotelId"));
        // 降维过的旧列必须彻底消失，否则新旧口径并存又会分叉
        assertNull(p.get("breakfast"));
        assertNull(p.get("cancelType"));
    }

    /**
     * 罚全款=确定不可退，<b>要进目录</b>。
     *
     * <p>这条钉住的是本次退改修复与建档的接缝：修复前 percent=100% 会被丢成
     * 「只剩免费头段」，落库就成了 FREE_CANCELLABLE——把不能退的卖成能退。
     */
    @Test
    void fullPenaltyLandsAsNonRefundable() {
        service.upsert(List.of(product(breakfast(), fullPenalty())));

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper).upsertSupplierProductBase(cap.capture());
        assertEquals("NON_REFUNDABLE", cap.getValue().get("cancelClass"));
    }

    /** 真有免费窗的才落 FREE_CANCELLABLE——与上一条成对，防止判据整体漂移 */
    @Test
    void freeWindowLandsAsFreeCancellable() {
        service.upsert(List.of(product(breakfast(), freeCancel())));

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper).upsertSupplierProductBase(cap.capture());
        assertEquals("FREE_CANCELLABLE", cap.getValue().get("cancelClass"));
    }

    /**
     * 退改解析不出（空列表）→ UNKNOWN → 不进目录（R-5.4）。
     *
     * <p>这类产品的 productKey 是<b>合法非空</b>的（含 UNKNOWN 成分），实时链路照常可售——
     * 所以建档侧不能只判"key 是否为空"，必须问 deriver。这正是本测试要钉住的点。
     */
    @Test
    void unknownCancelPolicyNeverEntersCatalog() {
        service.upsert(List.of(product(breakfast(), List.of())));
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /** 餐食解析不出 → 同样不进目录 */
    @Test
    void unknownMealNeverEntersCatalog() {
        service.upsert(List.of(product(null, freeCancel())));
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /** 按晚扣=判不出是否等于全款，第三种 UNKNOWN，从 DTO 表面看不出来，必须靠 deriver 挡住 */
    @Test
    void perNightPenaltyIsUnknownToo() {
        List<CancelPolicy> perNight = List.of(CancelPolicy.builder()
                .cancelType(1).type(RefundType.DEDUCT_DAY_NIGHT).value(1d).before(0).build());
        service.upsert(List.of(product(breakfast(), perNight)));
        Mockito.verify(mapper, Mockito.never()).upsertSupplierProductBase(Mockito.any());
    }

    /** 开关默认关时不得写库 */
    @Test
    void disabledSwitchWritesNothing() {
        ReflectionTestUtils.setField(service, "catalogEnabled", false);
        service.upsert(List.of(product(breakfast(), freeCancel())));
        Mockito.verifyNoInteractions(mapper);
    }

    /**
     * 建档是增益路径：写库抛异常不得打断刷价。
     *
     * <p>桩必须打在<b>实际被调用</b>的那个方法上，否则本测试会静默退化成"什么都没验"。
     */
    @Test
    void writeFailureNeverBreaksRefresh() {
        Mockito.when(mapper.upsertSupplierProductBase(Mockito.any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.upsert(List.of(product(breakfast(), freeCancel()))));
    }

    /** 缺 identity 的产品跳过，且不得把整批带崩 */
    @Test
    void productWithoutIdentityIsSkipped() {
        ProductRespDTO broken = ProductRespDTO.builder().hotelId("15714685").build();
        service.upsert(List.of(broken, product(breakfast(), freeCancel())));
        Mockito.verify(mapper, Mockito.times(1)).upsertSupplierProductBase(Mockito.any());
    }
}
