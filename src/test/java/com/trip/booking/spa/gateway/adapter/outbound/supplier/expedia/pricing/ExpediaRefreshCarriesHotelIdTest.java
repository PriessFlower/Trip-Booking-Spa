package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 守住一件事：<b>刷价发出的查价指令必须带着这一行的酒店号</b>。
 *
 * <p>它已经出过一次，代价是三天。查价指令合一（#240）把酒店号从单独的 {@code Supplier} 参数
 * 挪进了 {@code PriceQuery}；公共骨架 {@code AbstractCPSQueryPriceService#refreshViaQuery} 改对了，
 * 艺龙飞猪道旅都走它所以无事，而 Expedia 是唯一自建刷价流程的一家，它那份没跟着改——酒店号
 * 留在了一个建好之后再没人读的 {@code Supplier} 局部变量里，Java 不会为此报错。
 *
 * <p>后果（2026-09-14 生产实测）：自 2026-09-11 23:08 那次部署起，Expedia 刷价<b>全量失败</b>，
 * 8 分钟窗口 1,773 次调用无一成功，请求里 {@code property_id} 恒为 null；产品目录最后一次写入
 * 停在 23:07:47，恰在重启前 15 秒。而任务表的 {@code last_time} 照常推进（记账不看成败），
 * 所以从"刷价在不在跑"这个角度看毫无异常。
 *
 * <p><b>为什么必须由测试守而不是靠看</b>：这个缺陷在类型上完全合法。{@code PriceQuery.build()}
 * 只对住期做了非空校验，酒店号可以为 null（上游多家查价的映射路径确实会先留空再逐家填）。
 * 所以编译器帮不上忙，只能在这里钉住。
 */
class ExpediaRefreshCarriesHotelIdTest {

    private static final String HOTEL = "100359776";

    @Test
    @DisplayName("刷价一行：发出的查价指令必须带这一行的酒店号与供应商编码")
    void refreshPassesHotelIdIntoTheQuery() {
        AtomicReference<PriceQuery> sent = new AtomicReference<>();
        ExpediaCPSQueryPriceServiceImpl service = new ExpediaCPSQueryPriceServiceImpl();
        ReflectionTestUtils.setField(service, "expediaPriceService", capturingService(sent));

        ExpediaQueryPriceTask row = new ExpediaQueryPriceTask();
        row.setShId(HOTEL);
        row.setDelayCheckIn(3);
        row.setDelayCheckOut(4);

        service.refreshOne(row, "2");

        PriceQuery query = sent.get();
        assertNotNull(query, "刷价必须真的发出一次查价");
        assertEquals(HOTEL, query.supplierHotelId(),
                "酒店号没进查价指令——下游会拿 null 去问 Expedia，对方拒答，这一行等于白刷");
        assertEquals(SupplierSourceEnum.EXPEDIA.getCode(), query.supplierId(),
                "供应商编码同属坐标，一并带上");
        assertEquals(2, query.adultNum(), "占用维度取自 dimension");
        assertEquals(1, query.roomNum());
    }

    /** 只关心「发出去的是什么」，不关心供应商怎么答；返回空列表即「答了没货」 */
    private static ExpediaPriceService capturingService(AtomicReference<PriceQuery> sent) {
        return new ExpediaPriceService() {
            @Override
            public PricingResult queryPrices(PriceQuery request) {
                throw new UnsupportedOperationException("刷价不走这条");
            }

            @Override
            public List<Product> queryProductPrice(PriceQuery request) {
                throw new UnsupportedOperationException("刷价不走这条");
            }

            @Override
            public List<Product> queryPricesCache(PriceQuery request) {
                sent.set(request);
                return List.of();
            }
        };
    }
}
