package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入缓存裁剪的判据钉死（docs/price-refresh.md F-3）。
 *
 * <p>核心命题：分组键是 productKey（等价类）而非"含早"，故<b>每个卖法都保得住代表</b>——
 * 这是与 cursor 的 capLowestPerBreakfastGroup 的关键分歧。
 */
class PriceCacheTrimmerTest {

    private PriceCacheTrimmer trimmer;

    @BeforeEach
    void setUp() {
        trimmer = new PriceCacheTrimmer();
        ReflectionTestUtils.setField(trimmer, "keepPerKey", 2);
    }

    private static ProductRespDTO p(String id, String key, Integer price) {
        return ProductRespDTO.builder().hotelId("H1").productId(id).productKey(key).totalPrice(price).build();
    }

    @Test
    void keepsCheapestNPerEquivalenceClass() {
        List<ProductRespDTO> kept = trimmer.trim(List.of(
                p("a", "K1", 500), p("b", "K1", 300), p("c", "K1", 400), p("d", "K1", 900)));
        assertEquals(2, kept.size());
        assertEquals(List.of("b", "c"), kept.stream().map(ProductRespDTO::getProductId).toList());
    }

    /**
     * 本类存在的理由：不同等价类各自保留代表。cursor 按"含早"分组会把
     * 退改条款不同的产品挤进同一组，贵而可免费取消的那条被裁掉、该卖法整体消失。
     */
    @Test
    void everyEquivalenceClassSurvives() {
        List<ProductRespDTO> kept = trimmer.trim(List.of(
                p("cheap-norefund", "K-NONREFUND", 300),
                p("mid-norefund", "K-NONREFUND", 350),
                p("expensive-free-cancel", "K-FREE", 900),   // 贵但可免费取消
                p("cheap-breakfast", "K-BREAKFAST", 400)));
        List<String> ids = kept.stream().map(ProductRespDTO::getProductId).toList();
        assertEquals(4, kept.size());
        assertTrue(ids.contains("expensive-free-cancel"), "最贵的那条不能因为贵就被裁——它是独立卖法");
        assertTrue(ids.contains("cheap-breakfast"));
    }

    /** F-3.4：缺价的排最后、不占名额 */
    @Test
    void productsWithoutPriceRankLast() {
        List<ProductRespDTO> kept = trimmer.trim(List.of(
                p("no-price", "K1", null), p("cheap", "K1", 200), p("mid", "K1", 400), p("dear", "K1", 800)));
        assertEquals(List.of("cheap", "mid"), kept.stream().map(ProductRespDTO::getProductId).toList());
    }

    /**
     * 无 productKey 时不裁：没有键就无从判断谁与谁等价，此时裁剪等于随机丢弃卖法。
     * 宁可多存也不能误裁。
     */
    @Test
    void neverTrimsWhenProductKeyAbsent() {
        List<ProductRespDTO> input = List.of(
                p("a", null, 500), p("b", null, 300), p("c", "", 400));
        assertSame(input, trimmer.trim(input));
    }

    @Test
    void groupsSmallerThanLimitPassThrough() {
        List<ProductRespDTO> kept = trimmer.trim(List.of(
                p("a", "K1", 500), p("b", "K2", 300)));
        assertEquals(2, kept.size());
    }

    /** keepPerKey ≤ 0 = 关闭裁剪（排障用），原样返回 */
    @Test
    void canBeDisabled() {
        ReflectionTestUtils.setField(trimmer, "keepPerKey", 0);
        List<ProductRespDTO> input = List.of(p("a", "K1", 500), p("b", "K1", 300), p("c", "K1", 400));
        assertSame(input, trimmer.trim(input));
    }

    @Test
    void handlesNullAndSingleton() {
        assertEquals(null, trimmer.trim(null));
        List<ProductRespDTO> one = List.of(p("a", "K1", 100));
        assertSame(one, trimmer.trim(one));
    }
}
