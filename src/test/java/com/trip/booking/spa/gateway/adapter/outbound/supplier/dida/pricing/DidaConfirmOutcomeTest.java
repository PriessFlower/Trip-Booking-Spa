package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaError;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaHotel;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRatePlan;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住验价响应 → 三态。<b>这是接入里最容易出资损的一段</b>（architecture.md §5 第四步）：
 * 只有确证不会因重试而改变的才许判确定态，产品级死码必须 RATE_DEAD、绝不折叠进"不确定"
 * ——cursor 把艺龙 H001083 折进"无响应"，进而被数据库价兜底成"可订"，用户下单后在建单段
 * 暴死，丢的是真单。
 *
 * <p>错误码取自官方 information-hub/api-error-code（2026-09-08 查阅）；成功分支的夹具是
 * 同日生产真打的 PreBook=true 报文（酒店 563，ReferenceNo=18643313476）。
 */
class DidaConfirmOutcomeTest {

    private final DidaPriceServiceImpl service = service();

    @Test
    @DisplayName("2005 无库存 → SOLD_OUT（劝退旅客），不是 RATE_DEAD（重新查价）")
    void soldOut() {
        assertEquals(CheckPriceOutcome.SOLD_OUT, outcomeOf("2005"));
    }

    @Test
    @DisplayName("报价失效族 → RATE_DEAD：上游重新查价即可拿到换代后的报价")
    void rateDeadFamily() {
        assertEquals(CheckPriceOutcome.RATE_DEAD, outcomeOf("2006"));
        assertEquals(CheckPriceOutcome.RATE_DEAD, outcomeOf("2020"));
        assertEquals(CheckPriceOutcome.RATE_DEAD, outcomeOf("2029"));
        assertEquals(CheckPriceOutcome.RATE_DEAD, outcomeOf("2030"));
    }

    @Test
    @DisplayName("账号/权限、频控、参数与表外码 → INDETERMINATE，不许说成票死或满房")
    void indeterminateFamily() {
        assertEquals(CheckPriceOutcome.INDETERMINATE, outcomeOf("2017"));
        assertEquals(CheckPriceOutcome.INDETERMINATE, outcomeOf("2019"));
        assertEquals(CheckPriceOutcome.INDETERMINATE, outcomeOf("2022"));
        assertEquals(CheckPriceOutcome.INDETERMINATE, outcomeOf("2034"));
        assertEquals(CheckPriceOutcome.INDETERMINATE, outcomeOf("9999"));
    }

    @Test
    @DisplayName("验价成功但没带回这条报价 → RATE_DEAD")
    void confirmedWithoutRatePlanIsRateDead() {
        DidaPriceConfirmResponse resp = new DidaPriceConfirmResponse();
        DidaPriceConfirmResponse.Success success = new DidaPriceConfirmResponse.Success();
        DidaPriceConfirmResponse.PriceDetails details = new DidaPriceConfirmResponse.PriceDetails();
        details.setReferenceNo("18643313476");
        details.setHotelList(List.of());
        success.setPriceDetails(details);
        resp.setSuccess(success);

        assertEquals(CheckPriceOutcome.RATE_DEAD, interpret(resp).getOutcome());
    }

    /**
     * PreBook=true 必回 ReferenceNo（官方 price-confirm）。没有它就下不了单——
     * 此时报"可订"等于把不确定说成确定，且上游会拿一个必死的句柄去建单。
     */
    @Test
    @DisplayName("验后价在、却没回 ReferenceNo → INDETERMINATE，不签句柄")
    void missingReferenceNoIsNotBookable() throws IOException {
        DidaPriceConfirmResponse resp = confirmFixture();
        resp.getSuccess().getPriceDetails().setReferenceNo(null);

        CheckPriceRespDTO dto = interpret(resp);
        assertEquals(CheckPriceOutcome.INDETERMINATE, dto.getOutcome());
        assertNull(dto.getOfferId());
    }

    @Test
    @DisplayName("真实 PreBook 报文 → BOOKABLE：句柄签出、价格取验后价、退改取验价时点那份")
    void realPrebookIsBookable() throws IOException {
        CheckPriceRespDTO dto = interpret(confirmFixture());

        assertEquals(CheckPriceOutcome.BOOKABLE, dto.getOutcome());
        assertEquals("offer-test", dto.getOfferId());
        assertEquals(600L, dto.getOfferTtlSeconds());
        assertEquals(75800, dto.getSalePrice());
        assertEquals("CNY", dto.getCurrencyType());
        assertEquals(1, dto.getPriceInfos().size());
        // 09-25 起罚全额 → 免费窗 + 罚金段
        assertEquals(2, dto.getCancelPolicy().size());
        // 官方明说 InventoryCount 不准，故不报剩余房量——不把猜测说成事实
        assertNull(dto.getRemainRoomNum());
    }

    @Test
    @DisplayName("句柄里必须带齐下单要用的凭据：ReferenceNo 是道旅下单的唯一入口")
    void offerCarriesReferenceNo() throws IOException {
        RecordingOfferStore store = new RecordingOfferStore();
        DidaPriceServiceImpl impl = service();
        ReflectionTestUtils.setField(impl, "offerStore", store);
        impl.interpretConfirmResponse(request(), hotel(), searchedPlan(), confirmFixture());

        assertNotNull(store.credentials);
        assertEquals("18643313476", store.credentials.get("referenceNo"));
        assertEquals("563", store.credentials.get("hotelId"));
        assertEquals("190452504804273758", store.credentials.get("ratePlanId"));
        assertEquals("2026-09-29", store.credentials.get("checkIn"));
        assertEquals("2026-09-30", store.credentials.get("checkOut"));
        assertEquals("1", store.credentials.get("roomNum"));
        assertEquals("2", store.credentials.get("adultCount"));
        assertEquals("758", store.credentials.get("totalPrice"));
        assertEquals("CNY", store.credentials.get("currency"));
    }

    private CheckPriceOutcome outcomeOf(String code) {
        DidaPriceConfirmResponse resp = new DidaPriceConfirmResponse();
        DidaError error = new DidaError();
        error.setCode(code);
        error.setMessage("test");
        resp.setError(error);
        return interpret(resp).getOutcome();
    }

    private CheckPriceRespDTO interpret(DidaPriceConfirmResponse resp) {
        return service.interpretConfirmResponse(request(), hotel(), searchedPlan(), resp);
    }

    private static CheckPriceReq request() {
        return CheckPriceReq.builder().supplierId(10020).sHotelId("563")
                .sProductId("190452504804273758").checkIn("2026-09-29").checkOut("2026-09-30")
                .roomNum(1).adultCount(2).childNum(0).build();
    }

    private static DidaHotel hotel() {
        DidaHotel hotel = new DidaHotel();
        hotel.setHotelId(563L);
        return hotel;
    }

    /** 查价时点的那条报价：验价失败时的退改回落用它 */
    private static DidaRatePlan searchedPlan() {
        try {
            return fixture("/dida/price-search-563-1night.json").firstHotel().getRatePlanList().get(0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DidaPriceConfirmResponse confirmFixture() throws IOException {
        return read("/dida/price-confirm-563-prebook.json", DidaPriceConfirmResponse.class);
    }

    private static DidaPriceSearchResponse fixture(String resource) throws IOException {
        return read(resource, DidaPriceSearchResponse.class);
    }

    private static <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream in = DidaConfirmOutcomeTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "夹具缺失: " + resource);
            return JsonUtils.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }

    private static DidaPriceServiceImpl service() {
        DidaProperties properties = new DidaProperties();
        properties.setClientId("BJNSW");
        properties.setLicenseKey("BJNSW");
        DidaProductKeyDeriver deriver = new DidaProductKeyDeriver();
        deriver.setProperties(properties);
        DidaPriceServiceImpl impl = new DidaPriceServiceImpl();
        ReflectionTestUtils.setField(impl, "properties", properties);
        ReflectionTestUtils.setField(impl, "productKeyDeriver", deriver);
        ReflectionTestUtils.setField(impl, "offerStore", new RecordingOfferStore());
        return impl;
    }

    /** 只记不写的 OfferStore 替身：句柄签发本身有 Redis 依赖，这里只关心凭据装了什么 */
    private static final class RecordingOfferStore extends OfferStore {

        private Map<String, String> credentials;

        @Override
        public String issue(Integer supplierId, Map<String, String> credentials) {
            this.credentials = credentials;
            return "offer-test";
        }

        @Override
        public long ttlSecondsOf(int supplierId) {
            return 600L;
        }
    }
}
