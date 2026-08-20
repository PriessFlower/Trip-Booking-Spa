package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.content;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
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

    /**
     * 产品必须携带 identity——建档只照抄它，不再自行判定（R-2.8）。
     * 这里用与生产同一条路径（deriver）算出 identity，而不是手工拼，
     * 否则测试就成了"自己发明一套成分"，守不住真实链路。
     */
    private ProductRespDTO product(Meal meal, List<CancelPolicy> cancel) {
        ElongProductKeyDeriver deriver =
                (ElongProductKeyDeriver) ReflectionTestUtils.getField(service, "productKeyDeriver");
        ProductIdentity identity = deriver.deriveIdentity("61832733", "0033", meal, cancel, "2");
        return ProductRespDTO.builder()
                .hotelId("61832733").productId("62022758A19A7133205")
                .productKey(identity.productKey()).identity(identity)
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

    /**
     * 落库的每一列都必须<b>原样</b>来自 identity（R-2.8），且成分不得降维（R-2.7）。
     *
     * <p>反面即改造前：建档拿 {@code Meal}/{@code CancelPolicy} 重判一遍再压成
     * {@code breakfast}/{@code cancelType} 两个 int——{@code B1L1D1} 与 {@code B1L0D0}
     * 同为 1、占用连列都没有，表因此无法自证。
     */
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
        assertEquals(10010, p.get("supplierId"));
        assertEquals("elong-refresh", p.get("operator"));
        assertNull(p.get("supplierQuoteHint"), "艺龙报价码易腐,禁止落 hint 列(R-2.3)——否则又成'从库里读凭证'");

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

    /** 含三餐与只含早必须落成不同的成分——这正是旧的 breakfast 0/1 分不出来的那一对 */
    @Test
    void fullBoardAndBreakfastOnlyLandDifferently() {
        Meal fullBoard = new Meal();
        fullBoard.setCount(2); fullBoard.setLunchCount(2); fullBoard.setDinnerCount(2);

        service.upsert(List.of(product(fullBoard, freeCancel())));
        service.upsert(List.of(product(breakfast(), freeCancel())));

        ArgumentCaptor<HashMap<String, Object>> cap = ArgumentCaptor.forClass(HashMap.class);
        Mockito.verify(mapper, Mockito.times(2)).upsertSupplierProductBase(cap.capture());
        assertEquals("B1L1D1", cap.getAllValues().get(0).get("mealSignature"));
        assertEquals("B1L0D0", cap.getAllValues().get(1).get("mealSignature"));
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

    /**
     * 建档是增益路径:写库抛异常不得打断刷价。
     *
     * <p>桩必须打在<b>实际被调用</b>的那个方法上。原先打在
     * {@code upsertGlobalProductSupplier}，2026-08-20 停写它之后异常再也不会抛出，
     * 本测试会静默退化成"什么都没验"——绿着，但守不住任何东西。
     */
    @Test
    void writeFailureNeverBreaksRefresh() {
        Mockito.when(mapper.upsertSupplierProductBase(Mockito.any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.upsert(List.of(product(breakfast(), freeCancel()))));
    }

}
