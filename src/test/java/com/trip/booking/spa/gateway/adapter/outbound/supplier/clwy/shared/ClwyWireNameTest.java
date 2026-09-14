package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyCancellationPenalty;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyDayPrice;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyPriceResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRatePlan;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRoomType;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住差旅无忧报文的<b>线上字段名</b>（§4.2.3：wire 字面量是供应商契约，不是本仓词汇）。
 *
 * <p><b>为什么必须读原始 JSON</b>：字段名写错时 Jackson 不报错，只是值恒为 null——本仓已被这种
 * 静默坑过（艺龙 {@code mealCopyWriting} 被当本仓词汇改名，餐食恒 null、67,709 行档案失联）。
 *
 * <p>夹具是 2026-09-14 从生产端点真打回来的报文（酒店 218951，住期 T+7，阿里云出口），
 * 裁到一个房型两条报价：一条有退改一条没有。
 */
public class ClwyWireNameTest {

    @Test
    @DisplayName("报价响应：信封、酒店、房型、报价、逐晚价的每个字段都能从真实报文读出")
    void priceFieldsParse() throws IOException {
        ClwyPriceResponse resp = fixture();
        assertTrue(resp.isSucc());
        assertFalse(resp.isEmptyResult());
        assertEquals(200, resp.getCode());

        assertEquals("218951", resp.firstHotel().getHotelId());
        assertEquals("图尔基尼宫酒店", resp.firstHotel().getHotelNameCn());
        assertEquals("Palazzo Turchini", resp.firstHotel().getHotelNameEn());

        ClwyRoomType roomType = resp.firstHotel().getRoomTypeList().get(0);
        assertEquals("22720265", roomType.getRoomTypeId());
        assertEquals("行政双人房或双床房", roomType.getRoomTypeNameCn());
        assertEquals(2, roomType.getRatePlanList().size());

        ClwyRatePlan plan = roomType.getRatePlanList().get(0);
        assertEquals("40f43641489389fe9a8e0d9ec2881e21b9|3", plan.getRatePlanId());
        assertEquals(2, plan.getBreakfast());
        assertFalse(plan.hourRoom());

        ClwyDayPrice day = plan.getRatePlanPriceList().get(0);
        assertEquals("2026-09-21", day.getDate());
        assertEquals(0, day.getPrice().compareTo(new BigDecimal("2682.64")));
        assertEquals("CNY", day.getCurrency());
        assertEquals(4, day.getRoomCount());
        assertTrue(day.onSale());
    }

    /**
     * 退改时刻的三处「文档与实际不符」，逐条钉住——这三条任一被"纠正"回文档的说法，
     * 解析就会静默失败（退改恒 UNKNOWN、产品全不进目录），而编译与其余测试都不会红。
     */
    @Test
    @DisplayName("退改：cityTimeZone 是小时偏移不是时区名；from 无偏移；newFrom 的偏移是单位数")
    void cancellationShapeIsNotWhatTheDocSays() throws IOException {
        ClwyRatePlan plan = fixture().firstHotel().getRoomTypeList().get(0).getRatePlanList().get(0);

        assertEquals("1", plan.getCityTimeZone(), "是小时偏移字符串，不是 Europe/Rome 这种时区名");

        ClwyCancellationPenalty first = plan.getCancellationPenalties().get(0);
        assertEquals("2026-09-13 23:00:00", first.getFrom(), "本地时刻、空格分隔、无偏移");
        assertEquals("2026-09-13T23:00:00+1:00", first.getNewFrom(), "文档没有此字段；偏移是单位数，不合 ISO");
        assertEquals(0, first.getAmount().compareTo(new BigDecimal("536.51")));

        ClwyCancellationPenalty second = plan.getCancellationPenalties().get(1);
        assertEquals("2026-09-18 21:59:00", second.getFrom());
        assertEquals(0, second.getAmount().compareTo(new BigDecimal("2682.64")), "末段罚金=全额房费");
    }

    @Test
    @DisplayName("到店付费用：curreny 与 currency 两个拼写线上同时存在且同值，取用优先正确的那个")
    void feeCarriesBothCurrencySpellings() throws IOException {
        var fee = fixture().firstHotel().getRoomTypeList().get(0)
                .getRatePlanList().get(0).getFeeList().get(0);
        assertEquals("property fees or taxes", fee.getFeeName());
        assertEquals(0, fee.getFeePrice().compareTo(new BigDecimal("13.92")));
        assertEquals("USD", fee.getCurreny(), "官方参数表那个拼错的");
        assertEquals("USD", fee.getCurrency(), "线上实际也发的正确拼写");
        assertEquals("USD", fee.currencyOf());
    }

    @Test
    @DisplayName("同一房型下可以既有带退改的报价、也有不带的——后者按 UNKNOWN 走，不是解析失败")
    void someRatePlansCarryNoCancellationAtAll() throws IOException {
        ClwyRatePlan plan = fixture().firstHotel().getRoomTypeList().get(0).getRatePlanList().get(1);
        assertEquals("1268081beadef3bc2f3ce5c236f9fedc6df|0", plan.getRatePlanId());
        assertNull(plan.getCancellationPenalties());
        assertNull(plan.getCityTimeZone());
        assertNull(plan.getFeeList());
        assertNotNull(plan.getRatePlanPriceList(), "但逐晚价是有的，说明它可售");
    }

    public static ClwyPriceResponse fixture() throws IOException {
        return read("/clwy/price-218951-real-20260914.json", ClwyPriceResponse.class);
    }

    public static <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream in = ClwyWireNameTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "夹具缺失: " + resource);
            return JsonUtils.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }
}
