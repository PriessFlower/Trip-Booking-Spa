package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 缓存读侧重建的 room.roomId 必须是<b>展示/归组用的静态房型号</b>，不是身份成分（艺龙销售号）。
 * 2026-09-08：实时路径改了适配器后，缓存路径仍吐销售号——因为它从档案表按身份成分重建；此处钉住新列的优先级与回落。
 */
class ProductAttributeDisplayRoomIdTest {

    @Test
    @DisplayName("有展示房号就用它（艺龙：物理 0029 而不是销售 0053）")
    void prefersDisplayRoomId() {
        ProductAttributeReader.ProductAttribute a = ProductAttributeReader.ProductAttribute.builder()
                .roomId("0053").displayRoomId("0029").productName("高级大床房").build();
        assertEquals("0029", a.toRoom().getRoomId());
        assertEquals("高级大床房", a.toRoom().getRoomName());
    }

    @Test
    @DisplayName("老行没有展示房号（列刚加、还没被刷价 upsert 覆盖）→ 退回身份房号，行为等同过去")
    void fallsBackToIdentityRoomId() {
        assertEquals("0053", ProductAttributeReader.ProductAttribute.builder().roomId("0053").displayRoomId(null).build().toRoom().getRoomId());
        assertEquals("0053", ProductAttributeReader.ProductAttribute.builder().roomId("0053").displayRoomId("").build().toRoom().getRoomId());
    }
}
