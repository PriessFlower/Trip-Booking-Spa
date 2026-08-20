package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 钉住 {@code deriveIdentity} 与 {@code deriveProductKey} <b>不得分叉</b>，
 * 以及 identity <b>不出网关</b>。
 *
 * <p>两个方法并存是过渡态：resolve 匹配只做键比对、用 key 就够；查价组装与建档要成分。
 * 一旦两者算出不同的 key，身份就分叉了——这正是 R-1.1「键分叉即身份分叉」要防的。
 */
class ElongDeriveIdentityTest {

    private ElongProductKeyDeriver deriver;

    @BeforeEach
    void setUp() {
        ElongProperties properties = new ElongProperties();
        properties.setUser("test-account");
        deriver = new ElongProductKeyDeriver();
        deriver.setProperties(properties);
    }

    private static Meal meal(int breakfast, int lunch, int dinner) {
        Meal m = new Meal();
        m.setCount(breakfast);
        m.setLunchCount(lunch);
        m.setDinnerCount(dinner);
        return m;
    }

    /** 免费取消：cancelType=1 且不扣款（与 classifyCancel 的 FREE 判据一致） */
    private static List<CancelPolicy> freeCancel() {
        return List.of(CancelPolicy.builder().cancelType(1).type(RefundType.NO_DEDUCTION).build());
    }

    @Test
    @DisplayName("两个方法必须算出同一个 key，否则身份分叉")
    void identityAndKeyMustNotDiverge() {
        Meal m = meal(2, 2, 2);
        List<CancelPolicy> cp = freeCancel();

        ProductIdentity id = deriver.deriveIdentity("26978218", "0013", m, cp, "2");
        String key = deriver.deriveProductKey("26978218", "0013", m, cp, "2");

        assertEquals(key, id.productKey());
    }

    @Test
    @DisplayName("成分与派生器的规范化结果一致")
    void componentsMatchNormalisation() {
        ProductIdentity id = deriver.deriveIdentity("26978218", "0013", meal(2, 2, 2), freeCancel(), "2");

        assertEquals("test-account", id.account(), "账号成分取自配置，不是硬编码");
        assertEquals("26978218", id.supplierHotelId());
        assertEquals("0013", id.supplierRoomId(), "艺龙的房型锚是 RatePlan.RoomTypeId");
        assertEquals("B1L1D1", id.mealSignature(), "含三餐必须带出午晚餐位");
        assertEquals(CancelClass.FREE_CANCELLABLE.name(), id.cancelClass());
        assertEquals("2", id.occupancy());
    }

    /**
     * identity 是内部执行材料，禁止随查价响应出境——上游只该看到 productKey。
     */
    @Test
    @DisplayName("identity 不进对外 JSON")
    void identityIsNotSerialised() throws Exception {
        ProductIdentity id = deriver.deriveIdentity("26978218", "0013", meal(1, 0, 0), freeCancel(), "2");
        ProductRespDTO dto = ProductRespDTO.builder()
                .hotelId("26978218").productId("易腐报价码").productKey(id.productKey()).identity(id).build();

        String json = new ObjectMapper().writeValueAsString(dto);

        assertFalse(json.contains("identity"), "identity 是内部材料，不得出网关: " + json);
        assertFalse(json.contains("B1L0D0"), "成分也不得随响应外泄: " + json);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"productKey\""), json);
    }
}
