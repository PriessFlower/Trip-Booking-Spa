package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 缓存读侧的 room.roomId 必须优先取票据里随刷价写入的静态房型号，而不是档案表的身份成分。
 *
 * <p>2026-09-08：PR #212 把艺龙适配器的 roomId 改成物理号后，实时路径对了，缓存路径仍吐销售号——
 * 因为读侧按 productKey 从档案表重建 room，取的是身份成分 supplier_room_id（艺龙 = RoomTypeId）。
 * 生产 61504129 新包后两轮刷价，缓存路径仍是 0053~0071。修法：房型号随票据走，读侧优先用它。
 */
class PriceCacheRoomIdFromQuoteTest {

    private static ProductAttributeReader.ProductAttribute attr(String identityRoomId) {
        return ProductAttributeReader.ProductAttribute.builder().roomId(identityRoomId).productName("高级大床房").build();
    }

    @Test
    @DisplayName("票据带静态房型号：roomId 用票据的（物理 0029），名字仍取档案")
    void quoteRoomIdWins() {
        Room r = PriceCacheServiceImpl.roomFor(attr("0053"), "0029");
        assertEquals("0029", r.getRoomId());
        assertEquals("高级大床房", r.getRoomName());
    }

    @Test
    @DisplayName("老票据没带房型号：整体退回档案重建，行为等同过去")
    void legacyQuoteFallsBackToCatalog() {
        assertEquals("0053", PriceCacheServiceImpl.roomFor(attr("0053"), null).getRoomId());
        assertEquals("0053", PriceCacheServiceImpl.roomFor(attr("0053"), "").getRoomId());
    }

    @Test
    @DisplayName("档案缺席：有票据房号就只给号；两边都没有给 null")
    void noCatalog() {
        Room r = PriceCacheServiceImpl.roomFor(null, "0029");
        assertEquals("0029", r.getRoomId());
        assertNull(r.getRoomName());
        assertNull(PriceCacheServiceImpl.roomFor(null, null));
    }
}
