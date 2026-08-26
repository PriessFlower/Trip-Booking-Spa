package com.trip.booking.spa.gateway.adapter.outbound.state.offer;

import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住句柄 TTL 取严：全局配置与各家腐性申报的上限，谁短听谁的。
 *
 * <p>此前 {@code SupplierIdentityProfile} 的 javadoc 已把 OfferStore 写作 tokenTtlCap 的
 * 消费方，而 OfferStore 从未调用过它，实际全家共用一个全局值。没暴露是因为艺龙申报的
 * 上限（10 分钟）与生产全局值（600s）恰好相等——把 Nacos 的 cache.offer.ttl-seconds
 * 调大一次，艺龙的句柄就会活得比马甲的官方有效期还长，下单取到死马甲。
 * cursor 那边的同一个病是 2026-07-19 隔时重放验价 45/47 全灭。
 */
class OfferTtlCappedByProfileTest {

    private static OfferStore storeWith(RedisUtils redis, long globalTtlSeconds) {
        OfferStore store = new OfferStore();
        ReflectionTestUtils.setField(store, "redisUtils", redis);
        ReflectionTestUtils.setField(store, "ttlSeconds", globalTtlSeconds);
        return store;
    }

    /**
     * 全局调到 900 时，艺龙仍须落 600——申报上限来自供应商官方的凭据有效期，
     * 不该被我方的一条配置突破。
     */
    @Test
    void elongIsCappedByItsDeclaredProfile() {
        RedisUtils redis = mock(RedisUtils.class);
        when(redis.setex(anyString(), anyString(), eq(600L))).thenReturn(true);
        OfferStore store = storeWith(redis, 900L);

        store.issue(SupplierSourceEnum.ELONG.getCode(), Map.of("goodsUniqId", "g1"));

        verify(redis).setex(anyString(), anyString(), eq(600L));
    }

    /** Expedia 申报报价码稳定、无 TTL 上限，全局值原样生效 */
    @Test
    void expediaHasNoCapSoGlobalApplies() {
        RedisUtils redis = mock(RedisUtils.class);
        when(redis.setex(anyString(), anyString(), eq(900L))).thenReturn(true);
        OfferStore store = storeWith(redis, 900L);

        store.issue(SupplierSourceEnum.EXPEDIA.getCode(), Map.of("bookHref", "https://x"));

        verify(redis).setex(anyString(), anyString(), eq(900L));
    }

    /** 全局比申报上限还短时听全局的——取严是双向的，不是只朝申报靠 */
    @Test
    void globalWinsWhenItIsStricter() {
        RedisUtils redis = mock(RedisUtils.class);
        OfferStore store = storeWith(redis, 120L);

        assertEquals(120L, store.ttlSecondsOf(SupplierSourceEnum.ELONG.getCode()));
    }

    /**
     * 报给上游的 offerTtlSeconds 必须与真正写进 Redis 的值同源。
     *
     * <p>这是本次修复真正防住的那个缺陷形态：存 600 却告诉上游 900，上游据此判断
     * 「还来得及直接下单」，于是在句柄已死的那 300 秒里去下单。
     */
    @Test
    void reportedTtlMatchesStoredTtl() {
        RedisUtils redis = mock(RedisUtils.class);
        long reported = storeWith(redis, 900L).ttlSecondsOf(SupplierSourceEnum.ELONG.getCode());

        when(redis.setex(anyString(), anyString(), eq(reported))).thenReturn(true);
        storeWith(redis, 900L).issue(SupplierSourceEnum.ELONG.getCode(), Map.of("goodsUniqId", "g1"));

        verify(redis).setex(anyString(), anyString(), eq(reported));
    }
}
