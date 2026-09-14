package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService.RefreshOutcome;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 刷价的「查一次 → 写缓存 → 给三态」归骨架（{@code refreshViaQuery}，2026-09-07 上提）。
 *
 * <p>此前这三件事在每家的 {@code refreshOne} 与 {@code queryPricesCache} 里各写一份，
 * 艺龙与飞猪逐字相同。上提之后必须有测试盯住——反证发现：把骨架里的
 * {@code productToCache} 删掉，全量单测<b>一条都不红</b>，写缓存这一跳当时是无人看守的。
 *
 * <p>三态口径是本类的重点：INDETERMINATE 必须 FAILED 且<b>不动缓存</b>（F-5.1，一次
 * 网络抖动不许清掉在售价）；空列表必须<b>照写</b>（F-5.2 落无货标记、清僵尸价 B7）——
 * 少了后者，卖光的店会一直挂着上一轮的陈价。
 */
class RefreshViaQueryTest {

    /** 一行刷价任务：T+3 入住、住一晚 */
    private record Row(String shId) implements RefreshTaskRow {
        @Override
        public String getShId() {
            return shId;
        }

        @Override
        public int getDelayCheckIn() {
            return 3;
        }

        @Override
        public int getDelayCheckOut() {
            return 4;
        }

        @Override
        public int getPriorityLevelNumber() {
            return 0;
        }

        @Override
        public int getTemporaryUpgrade() {
            return 0;
        }

        @Override
        public void setTemporaryUpgrade(int temporaryUpgrade) {
        }

        @Override
        public Date getUpgradeDeadline() {
            return null;
        }
    }

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    static class StubRefresh extends AbstractCPSQueryPriceService<Row> {
        PricingResult next;
        PriceQuery seenRequest;

        @Override
        protected RedissonClient redissonClient() {
            return null;
        }

        @Override
        protected String lockKey() {
            return "stub";
        }

        @Override
        protected SupplierSourceEnum supplier() {
            return SupplierSourceEnum.FLIGGY;
        }

        @Override
        protected boolean gateOpen() {
            return false;
        }

        @Override
        protected List<Integer> tiers() {
            return List.of(0);
        }

        @Override
        protected int batchSize(int priority) {
            return 1;
        }

        @Override
        protected int concurrency(int priority) {
            return 1;
        }

        @Override
        protected double declaredQps(int priority) {
            return 1;
        }

        @Override
        protected List<Row> nextBatch(int priority, int temporaryUpgrade, int batchSize) {
            return List.of();
        }

        @Override
        protected List<String> dimensions() {
            return List.of("1");
        }

        @Override
        protected RefreshOutcome refreshOne(Row row, String dimension) {
            return refreshViaQuery(row, dimension);
        }

        @Override
        protected void markRefreshed(Row row) {
        }

        @Override
        protected ZoneId supplierZone() {
            return BEIJING;
        }

        @Override
        protected PricingResult queryForRefresh(PriceQuery request) {
            seenRequest = request;
            return next;
        }
    }

    private static StubRefresh flowWith(PriceCacheService cache) {
        StubRefresh flow = new StubRefresh();
        ReflectionTestUtils.setField(flow, "priceCacheService", cache);
        return flow;
    }

    @Test
    @DisplayName("有在售 → ON_SALE，且真的写了缓存")
    void onSaleWritesTheCache() {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        StubRefresh flow = flowWith(cache);
        flow.next = PricingResult.of(List.of(Product.builder().productId("P1").build()));

        assertEquals(RefreshOutcome.ON_SALE, flow.refreshOne(new Row("H1"), "2"));

        ArgumentCaptor<List> products = ArgumentCaptor.forClass(List.class);
        Mockito.verify(cache).productToCache(products.capture(), Mockito.any());
        assertEquals(1, products.getValue().size());
    }

    @Test
    @DisplayName("明确无货 → EMPTY，空列表照写（不写则僵尸价一直挂着，B7）")
    void emptyStillWritesSoTheMarkerLands() {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        StubRefresh flow = flowWith(cache);
        flow.next = PricingResult.noInventory();

        assertEquals(RefreshOutcome.EMPTY, flow.refreshOne(new Row("H1"), "1"));

        ArgumentCaptor<List> products = ArgumentCaptor.forClass(List.class);
        Mockito.verify(cache).productToCache(products.capture(), Mockito.any());
        assertEquals(0, products.getValue().size());
    }

    @Test
    @DisplayName("没问出结果 → FAILED，且一个字节都不许碰缓存（F-5.1）")
    void indeterminateMustNotTouchTheCache() {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        StubRefresh flow = flowWith(cache);
        flow.next = PricingResult.indeterminate();

        assertEquals(RefreshOutcome.FAILED, flow.refreshOne(new Row("H1"), "1"));

        Mockito.verify(cache, Mockito.never())
                .productToCache(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("查价返回 null 也按 FAILED 兜底，不动缓存")
    void nullResultIsAlsoFailed() {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        StubRefresh flow = flowWith(cache);
        flow.next = null;

        assertEquals(RefreshOutcome.FAILED, flow.refreshOne(new Row("H1"), "1"));
        Mockito.verify(cache, Mockito.never())
                .productToCache(Mockito.any(), Mockito.any());
    }

    /**
     * 日期按各家申报的时区换算——2026-08-25 生产事故：容器无 TZ、JVM 走 UTC，
     * 整个日期窗口错位一天。roomNum 恒 1 亦是已验证的选择（缓存价单间口径）。
     */
    @Test
    @DisplayName("请求形状：维度进成人数、日期按申报时区算、roomNum 恒 1")
    void requestShapeFollowsRowAndZone() {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        StubRefresh flow = flowWith(cache);
        flow.next = PricingResult.noInventory();

        flow.refreshOne(new Row("H1"), "2");

        LocalDate today = LocalDate.now(BEIJING);
        assertEquals(2, flow.seenRequest.adultNum());
        assertEquals(1, flow.seenRequest.roomNum(), "缓存键不含间数、缓存价是单间口径");
        assertEquals(today.plusDays(3).toString(), flow.seenRequest.checkIn());
        assertEquals(today.plusDays(4).toString(), flow.seenRequest.checkOut());
    }

    @Test
    @DisplayName("Supplier 必须带上申报的供应商编号——缺了缓存键就写到别人家去了")
    void supplierCarriesTheDeclaredCode() {
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        StubRefresh flow = flowWith(cache);
        flow.next = PricingResult.noInventory();

        flow.refreshOne(new Row("H9"), "1");

        // 供应商坐标现在就在指令里，不再是并排的第二个参数
        ArgumentCaptor<PriceQuery> pq = ArgumentCaptor.forClass(PriceQuery.class);
        Mockito.verify(cache).productToCache(Mockito.any(), pq.capture());
        assertEquals(SupplierSourceEnum.FLIGGY.getCode(), pq.getValue().supplierId());
        assertEquals("H9", pq.getValue().supplierHotelId());
    }
}
