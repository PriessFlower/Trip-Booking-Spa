package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 查价转换：从原始 JSON 一路到 ProductRespDTO（读原始报文，不 new 中间对象）。
 * 钉三件事：价格单位分原样透传+币种自带；身份与票据分列两字段（productKey ≠ rate_key）；
 * 丢弃分支必计 quote_dropped（O-4.5，O45 守护要求本包引用它）。
 */
class FliggyConvertTest {

    private FliggyPriceServiceImpl service;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        service = new FliggyPriceServiceImpl();
        FliggyProperties properties = new FliggyProperties();
        properties.setAppKey("app-1");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "productKeyDeriver", new FliggyProductKeyDeriver(properties));

        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
    }

    private static PriceReq req() {
        PriceReq req = PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-02")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        req.setOccupancies(List.of("2"));
        return req;
    }

    @Test
    @DisplayName("好报价出报:分价原样、币种自带、身份与 rate_key 分列;缺 rate_key 弃之且计数")
    void convertsGoodRateAndCountsDropped() {
        String raw = "{\"xhotel_distribution_ari_availability_response\":{\"data\":{"
                + "\"request_trace_id\":\"t1\",\"properties\":[{\"hotel_id\":\"H1\",\"rates\":["
                + "{\"rate_key\":\"rk-1\",\"room_id\":\"R1\",\"room_name\":\"大床房\","
                + "\"total_rate\":{\"inclusive\":\"25800\",\"exclusive\":\"23000\",\"currency\":\"USD\"},"
                + "\"meals\":{\"type\":1,\"number\":2}},"
                + "{\"room_id\":\"R2\",\"total_rate\":{\"inclusive\":\"100\",\"currency\":\"USD\"}}"
                + "]}]}}}";
        FliggyAriResponse resp = FliggyAriResponse.parse(raw);

        List<ProductRespDTO> products = service.convertRates(resp.rates(), req(), "H1");

        assertEquals(1, products.size());
        ProductRespDTO p = products.get(0);
        assertEquals("rk-1", p.getProductId());
        assertNotNull(p.getProductKey());
        assertEquals(25800, p.getTotalPrice());
        assertEquals(23000, p.getRoomTotalPrice());
        assertEquals("USD", p.getCurrencyType());
        assertEquals(10015, p.getSupplierId());
        assertEquals(2, p.getMeal().count);

        assertEquals(1.0, registry.counter("quote_dropped_count", "supplier", "FLIGGY",
                "stage", "convert", "reason", "no_session_credentials").count());

        // 单晚缺 daily_rates 用 total_rate 精确回落——priceInfos 是写缓存的唯一载体，
        // 缺了它 productToCache 一个字节都不写（2026-08-28 生产实证：43,166 键全是无货标记）
        assertEquals(1, p.getPriceInfos().size());
        PriceInfo day = p.getPriceInfos().get(0);
        assertEquals("2026-09-01", day.getDate());
        assertEquals(25800, day.getPrice());
        assertEquals(23000, day.getRoomPrice());
        assertEquals(2800, day.getTaxes());
    }

    @Test
    @DisplayName("真实报文的 daily_rates 必须进 priceInfos——写缓存的唯一载体")
    void realPayloadDailyRatesBecomePriceInfos() throws Exception {
        String raw = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/test/resources/fliggy/ari-availability-real-20260827.json"));
        FliggyAriResponse resp = FliggyAriResponse.parse(raw);
        PriceReq req = PriceReq.builder().checkIn("2026-09-10").checkout("2026-09-11")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        req.setOccupancies(List.of("2"));

        List<ProductRespDTO> products = service.convertRates(resp.rates(), req, "50363404");

        assertEquals(2, products.size());
        for (ProductRespDTO p : products) {
            assertNotNull(p.getPriceInfos(), "没有 priceInfos 的报价进不了价格缓存");
            assertEquals(1, p.getPriceInfos().size());
        }
        // 第二条 rate：inclusive 10524 / exclusive 7767 / tax 2757，三项自洽（口径同艺龙）
        PriceInfo day = products.get(1).getPriceInfos().get(0);
        assertEquals("2026-09-10", day.getDate());
        assertEquals(10524, day.getPrice());
        assertEquals(7767, day.getRoomPrice());
        assertEquals(2757, day.getTaxes());

        // 产品名=卖法名（同艺龙口径）：房型名分不出同房型的多个卖法
        assertEquals("素泊りプラン", products.get(0).getProductInfo().getProductName());

        // 真实报文的 inclusive_amount 是数字串（"10524"），退改规则必须解析得出——
        // 丢光规则=cancelClass 恒 UNKNOWN：进不了目录，且上游按"退改从严"兜底
        //（艺龙 26,011 个可免费取消显示为不可退的同款事故）。首条 rate 有罚金 0 的段=免费窗
        ProductRespDTO first = products.get(0);
        assertEquals(3, first.getCancelPolicy().size());
        assertEquals("FREE_CANCELLABLE", first.getIdentity().cancelClass());

        // 时间语义（时区实证见 FliggyRealPayloadTest）：免费段截止 2026-09-08 23:00 北京，
        // 距入住(09-10)日 24:00 = 49 小时——时区若按东京解释会差 1 小时（50），按 UTC 差 8
        com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy freeSeg =
                first.getCancelPolicy().get(2);
        assertEquals(0, freeSeg.getAmount());
        assertEquals(49, freeSeg.getBefore());
        assertEquals("GMT+08:00", freeSeg.getTimeZone());
    }

    @Test
    @DisplayName("全程罚全款 → NON_REFUNDABLE（生产采样 122/122 的 code=2 形态）")
    void fullPenaltyRateClassifiesNonRefundable() {
        // 形态照抄生产实证:单段、onward=当下、inclusive_amount=总价（docs/fliggy §2）
        String raw = "{\"xhotel_distribution_ari_availability_response\":{\"data\":{"
                + "\"request_trace_id\":\"t1\",\"properties\":[{\"hotel_id\":\"H1\",\"rates\":["
                + "{\"rate_key\":\"rk-1\",\"room_id\":\"R1\",\"room_name\":\"大床房\","
                + "\"total_rate\":{\"inclusive\":\"6814\",\"currency\":\"USD\"},"
                + "\"daily_rates\":[{\"date\":\"2026-09-01\",\"inclusive\":\"6814\",\"currency\":\"USD\"}],"
                + "\"cancel_policy\":{\"code\":\"2\",\"rules\":[{\"onward\":\"2026-08-28 12:00:00\","
                + "\"before\":\"2026-09-02 00:00:00\",\"inclusive_amount\":\"6814\",\"currency\":\"USD\"}]},"
                + "\"meals\":{\"type\":0}}]}]}}}";
        List<ProductRespDTO> products = service.convertRates(FliggyAriResponse.parse(raw).rates(), req(), "H1");

        assertEquals(1, products.size());
        assertEquals("NON_REFUNDABLE", products.get(0).getIdentity().cancelClass(),
                "罚全款判不出来=这批货全归 UNKNOWN 进不了目录(40% 覆盖缺口)");
    }

    @Test
    @DisplayName("多晚缺逐日价 → 整条不报且计数（均摊会造出假的日期价）")
    void multiNightWithoutDailyRatesIsDroppedAndCounted() {
        String raw = "{\"xhotel_distribution_ari_availability_response\":{\"data\":{"
                + "\"request_trace_id\":\"t1\",\"properties\":[{\"hotel_id\":\"H1\",\"rates\":["
                + "{\"rate_key\":\"rk-1\",\"room_id\":\"R1\",\"room_name\":\"大床房\","
                + "\"total_rate\":{\"inclusive\":\"25800\",\"currency\":\"USD\"},"
                + "\"meals\":{\"type\":0}}]}]}}}";
        FliggyAriResponse resp = FliggyAriResponse.parse(raw);
        PriceReq twoNights = PriceReq.builder().checkIn("2026-09-01").checkout("2026-09-03")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        twoNights.setOccupancies(List.of("2"));

        List<ProductRespDTO> products = service.convertRates(resp.rates(), twoNights, "H1");

        assertEquals(0, products.size());
        assertEquals(1.0, registry.counter("quote_dropped_count", "supplier", "FLIGGY",
                "stage", "convert", "reason", "no_day_price").count());
    }
}
