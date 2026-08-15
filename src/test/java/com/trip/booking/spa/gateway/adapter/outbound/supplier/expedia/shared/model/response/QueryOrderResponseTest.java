package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉死查单的三态判读。
 *
 * <p>这些用例守的是下单资损防线的另一半：<b>只有「确证不存在」才可重新下单</b>。
 * 把「查单失败」判成「不存在」会重复下单；把「不存在」判成「不确定」会让订单永久悬空。
 * 故任何无法判读的响应都必须落 INDETERMINATE，而不是猜一个方向。
 */
class QueryOrderResponseTest {

    /** 非空数组 = 确证订单存在 */
    @Test
    void nonEmptyArrayMeansFound() {
        String body = "[{\"itinerary_id\":\"88001\",\"affiliate_reference_id\":\"ORDER-1\"}]";

        QueryOrderResponse resp = QueryOrderResponse.of(body);

        assertEquals(QueryOrderResponse.Presence.FOUND, resp.getPresence());
        assertNotNull(resp.firstItinerary());
        assertEquals("88001", resp.firstItinerary().getItinerary_id());
    }

    /** 空数组 = 确证订单不存在，这是唯一可安全重下单的情形 */
    @Test
    void emptyArrayMeansNotFound() {
        QueryOrderResponse resp = QueryOrderResponse.of("[]");

        assertEquals(QueryOrderResponse.Presence.NOT_FOUND, resp.getPresence());
        assertNull(resp.firstItinerary());
    }

    /** 错误响应体不等于订单不存在，必须落 INDETERMINATE */
    @Test
    void errorObjectIsIndeterminateNotNotFound() {
        String body = "{\"type\":\"internal_server_error\",\"message\":\"An internal error occurred\"}";

        QueryOrderResponse resp = QueryOrderResponse.of(body);

        assertEquals(QueryOrderResponse.Presence.INDETERMINATE, resp.getPresence(),
                "查单失败若判成 NOT_FOUND，上游会据此重新下单，造成重复订单");
        assertEquals("internal_server_error", resp.getType());
    }

    /** 空响应体同样不可判读 */
    @Test
    void blankBodyIsIndeterminate() {
        assertEquals(QueryOrderResponse.Presence.INDETERMINATE, QueryOrderResponse.of("").getPresence());
        assertEquals(QueryOrderResponse.Presence.INDETERMINATE, QueryOrderResponse.of(null).getPresence());
    }

    /** 「订单不存在」是一个有效结论，故查单调用本身算成功 */
    @Test
    void notFoundCountsAsSuccessfulQuery() {
        assertEquals(true, QueryOrderResponse.of("[]").isSucc(),
                "NOT_FOUND 是确定结论，不应被视为查单失败");
        assertEquals(false, QueryOrderResponse.of("{\"type\":\"x\"}").isSucc());
    }
}
