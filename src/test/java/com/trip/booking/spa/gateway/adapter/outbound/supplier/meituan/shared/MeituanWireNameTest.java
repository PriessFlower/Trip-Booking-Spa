package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanBatchGoodsResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanCpApply;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanGoods;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanHotelGoods;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住美团报文的<b>线上字段名</b>（§4.2.3：wire 字面量是供应商契约，不是本仓词汇）。
 *
 * <p><b>为什么必须读原始 JSON</b>：字段名写错时 Jackson 不报错，只是值恒为 null——本仓已被这种
 * 静默坑过（艺龙 {@code mealCopyWriting} 被当本仓词汇改名，餐食恒 null、67,709 行档案失联）。
 *
 * <p>夹具是 2026-09-14 从生产端点真打回来的报文（酒店 967183，住期 T+7），裁到四条产品：
 * 即时确认+限时取消、即时确认+不可取消、<b>非</b>即时确认、即时确认+含早。
 */
public class MeituanWireNameTest {

    @Test
    @DisplayName("信封与产品：code/result/goodsId/realRoomId/逐日价 都能从真实报文读出")
    void goodsFieldsParse() throws IOException {
        MeituanBatchGoodsResponse resp = fixture();
        assertTrue(resp.isSucc());
        assertFalse(resp.isEmptyResult());
        assertEquals(0, resp.getCode());

        MeituanHotelGoods hotel = resp.hotelOf("967183");
        assertNotNull(hotel, "按酒店 id 取袋——合批时回的顺序不保证，不能按下标取");
        assertEquals(4, hotel.getGoodsList().size());

        MeituanGoods goods = hotel.getGoodsList().get(0);
        assertEquals(158043997015L, goods.getGoodsId());
        assertEquals("尊尚双床客房", goods.getGoodsName());
        assertEquals("Premier Double Queen Room", goods.getGoodsNameEn());
        assertEquals(26899457L, goods.getRealRoomId());
        assertTrue(goods.isInstantConfirm());
        assertEquals(Boolean.TRUE, goods.getImmediateConfirm(), "与 confirmType 同义，用作交叉核对");

        assertEquals(1, goods.getPriceModelList().size());
        assertEquals("2026-09-21", goods.getPriceModelList().get(0).getDate());
        assertEquals(25855L, goods.getPriceModelList().get(0).getPrice(), "单位已是分，不必再乘 100");
    }

    /**
     * 餐食判据取枚举而不取份数——官方两处文档对 {@code count=-1} 的说法相反
     * （批量查价页"无早"、下单前校验页"份数不确定"）。这条断言一旦被改成信 count，
     * 无早产品会被判成"份数未知"而整批落 UNKNOWN、不进目录，且编译与其余测试都不会红。
     */
    @Test
    @DisplayName("餐食：ohMealTypeEnum 是判据，无早恒 count=-1、含早 count≥1")
    void mealTypeCarriesTheEnum() throws IOException {
        MeituanHotelGoods hotel = fixture().hotelOf("967183");

        var noBreakfast = hotel.getGoodsList().get(0).getMealType();
        assertEquals("NO_BREAKFAST", noBreakfast.getOhMealTypeEnum(), "官方参数表未列，线上一直在发");
        assertEquals(-1, noBreakfast.getCount());
        assertEquals("无早", noBreakfast.getDesc());

        var breakfast = hotel.getGoodsList().get(3).getMealType();
        assertEquals("BREAKFAST", breakfast.getOhMealTypeEnum());
        assertEquals(2, breakfast.getCount());
    }

    @Test
    @DisplayName("退改：endDate 是段的右边界、北京时间；penalty 是定额，且可以超过我方总价")
    void cancellationIsRightBounded() throws IOException {
        MeituanGoods goods = fixture().hotelOf("967183").getGoodsList().get(0);
        assertEquals(2, goods.getRefundable(), "2=限时取消");

        MeituanCpApply free = goods.getCpApply().get(0);
        assertEquals("09/20/2026 00:00", free.getEndDate(), "MM/dd/yyyy HH:mm，北京时间");
        assertEquals(0L, free.getPenalty(), "此刻之前取消不收钱");

        MeituanCpApply charged = goods.getCpApply().get(1);
        assertEquals("09/21/2026 23:59", charged.getEndDate());
        assertEquals(28020L, charged.getPenalty());
        assertTrue(charged.getPenalty() > goods.getPriceModelList().get(0).getPrice(),
                "罚金 28020 > 我方单间总价 25855：美团按自己的售价算罚金，折成比例会得出 >100%");
    }

    @Test
    @DisplayName("不可取消的产品 cpApply 为空数组；非即时确认的产品照样带价，得靠 confirmType 挡")
    void nonRefundableAndOnRequestShapes() throws IOException {
        MeituanHotelGoods hotel = fixture().hotelOf("967183");

        MeituanGoods nonRefundable = hotel.getGoodsList().get(1);
        assertEquals(1, nonRefundable.getRefundable());
        assertTrue(nonRefundable.isNonRefundable());
        assertTrue(nonRefundable.getCpApply().isEmpty());

        MeituanGoods onRequest = hotel.getGoodsList().get(2);
        assertEquals(2, onRequest.getConfirmType(), "2=非即时确认");
        assertFalse(onRequest.isInstantConfirm());
        assertFalse(onRequest.getPriceModelList().isEmpty(), "它有价——不挡就会被当成可卖的货");
    }

    public static MeituanBatchGoodsResponse fixture() throws IOException {
        return read("/meituan/batch-goods-967183-real-20260914.json", MeituanBatchGoodsResponse.class);
    }

    public static <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream in = MeituanWireNameTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "夹具缺失: " + resource);
            return JsonUtils.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }
}
