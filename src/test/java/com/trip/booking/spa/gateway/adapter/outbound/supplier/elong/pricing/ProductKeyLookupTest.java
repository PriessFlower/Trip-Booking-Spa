package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * productKey 反查自愈的判据钉死(供应商网关承接方案批次2)。
 *
 * <p>背景:cursor 出价用 SPA productId 作不透明句柄,渠道协议无处存 key;
 * 验价回传只有 productId。本网关发的票,详情缓存里存着 key(#80)——自己找回,
 * resolve 换票才有检索键。反查是增益路径:查不到/异常一律 null,不影响主流程。
 */
class ProductKeyLookupTest {

    private ElongPriceServiceImpl service;
    private RedisUtils redisUtils;

    @BeforeEach
    void setUp() {
        service = new ElongPriceServiceImpl();
        redisUtils = Mockito.mock(RedisUtils.class);
        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
    }

    @Test
    void recoversKeyFromDetailCache() {
        Mockito.when(redisUtils.get("product:4173:392135581"))
                .thenReturn("{\"productId\":\"392135581\",\"productKey\":\"2a8c7eb804d4ff\",\"totalPrice\":20994}");
        assertEquals("2a8c7eb804d4ff", service.lookupProductKeyFromCache("4173", "392135581"));
    }

    /** 老缓存条目(无 key 字段)→ null,不炸 */
    @Test
    void legacyEntryWithoutKeyReturnsNull() {
        Mockito.when(redisUtils.get(anyString())).thenReturn("{\"productId\":\"392135581\"}");
        assertNull(service.lookupProductKeyFromCache("4173", "392135581"));
    }

    /** 外来票(缓存无此详情)→ null */
    @Test
    void unknownTicketReturnsNull() {
        Mockito.when(redisUtils.get(anyString())).thenReturn(null);
        assertNull(service.lookupProductKeyFromCache("4173", "crawl-era-id"));
    }

    /** 缓存内容损坏 → null,绝不抛(增益路径不影响验价主流程) */
    @Test
    void corruptJsonReturnsNullNotThrow() {
        Mockito.when(redisUtils.get(anyString())).thenReturn("{这不是JSON");
        assertNull(service.lookupProductKeyFromCache("4173", "392135581"));
    }
}
