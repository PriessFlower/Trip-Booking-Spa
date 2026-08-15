package com.trip.booking.spa.gateway.adapter.outbound.state.offer;

import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 钉住报价句柄的一次性票据语义（用完即焚）。
 *
 * <p>此前句柄下单成功后仍存活到 TTL 届满，重复下单的防线全在供应商侧幂等——
 * Expedia 恰好有 affiliate_reference_id 拒重，别家未必有。核销把防线收回自己家。
 */
class OfferStoreConsumeTest {

    /** 核销必须落到 Redis 删除——键带仓库前缀 */
    @Test
    void consumeDeletesTheHandle() {
        RedisUtils redis = mock(RedisUtils.class);
        OfferStore store = new OfferStore();
        ReflectionTestUtils.setField(store, "redisUtils", redis);

        store.consume("of_abc123");

        verify(redis).remove("offer:of_abc123");
    }

    /** 空句柄是无操作，不许碰 Redis——FAILED 路径上 offerId 可能为空 */
    @Test
    void blankHandleIsNoOp() {
        RedisUtils redis = mock(RedisUtils.class);
        OfferStore store = new OfferStore();
        ReflectionTestUtils.setField(store, "redisUtils", redis);

        store.consume(null);
        store.consume("  ");

        verify(redis, never()).remove(anyString());
    }

    /**
     * 核销失败绝不能抛出——此刻订单已成立，收尾动作失败不许污染已确定的成功结论。
     * 漏核销的兜底是供应商幂等 + TTL 自然过期。
     */
    @Test
    void consumeFailureMustNotThrow() {
        RedisUtils redis = mock(RedisUtils.class);
        doThrow(new RuntimeException("redis 抖动")).when(redis).remove(anyString());
        OfferStore store = new OfferStore();
        ReflectionTestUtils.setField(store, "redisUtils", redis);

        assertDoesNotThrow(() -> store.consume("of_abc123"));
    }
}
