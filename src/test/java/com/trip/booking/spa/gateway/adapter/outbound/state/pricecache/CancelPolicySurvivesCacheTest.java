package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.platform.redis.RedisUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * 退改条款必须<b>穿过缓存活着出来</b>——写进去、读回来、装进响应，一路不能断。
 *
 * <p><b>为什么单独钉这一组</b>：2026-08-20 瘦身时把 cancelPolicy 从缓存载荷里删掉了，
 * 以为可以从档案表补——但档案表一行只对应一个卖法、没有住期维度，只存得下粗分类。
 * 读侧于是只补了房型/餐食/产品名，cancelPolicy 恒为 null，上游按「退改从严」兜底，
 * <b>26,011 个可免费取消的卖法在渠道侧全部显示为不可退</b>。
 *
 * <p>更值得记的是它<b>为什么没被测出来</b>：当时给 ProductAttribute 加了 cancelClass 字段、
 * 写了注释、也做了反证——但反证只覆盖餐食。<b>字段加了没有出口，而没有测试去用它。</b>
 * 故本组不测"字段在不在"，只测"从写到读这条路通不通"。
 */
class CancelPolicySurvivesCacheTest {

    private static final String DATE = "2026-09-01";

    private PriceCacheServiceImpl service;
    private RedisUtils redisUtils;

    @BeforeEach
    void setUp() {
        service = new PriceCacheServiceImpl();
        ReflectionTestUtils.setField(service, "productCatalogService", Mockito.mock(com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogService.class));
        redisUtils = Mockito.mock(RedisUtils.class);
        ProductAttributeReader reader = Mockito.mock(ProductAttributeReader.class);
        Mockito.when(reader.batchGet(Mockito.anyInt(), Mockito.anyList())).thenReturn(Map.of());
        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(service, "productAttributeReader", reader);
        ReflectionTestUtils.setField(service, "priceCacheTrimmer", new PriceCacheTrimmer());
        ReflectionTestUtils.setField(service, "abnormalPriceGuard", new AbnormalPriceGuard());
        ReflectionTestUtils.setField(service, "priceCacheTtlPolicy", new PriceCacheTtlPolicy());
    }

    private static Supplier sup() {
        return Supplier.builder().supplierId(10010).sHotelId("H1").build();
    }

    private static PriceReq req() {
        return PriceReq.builder().checkIn(DATE).checkout("2026-09-02")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).build();
    }

    /** 可免费取消：入住前 36 小时前退，不扣款 */
    private static List<CancelPolicy> freeCancel() {
        return List.of(CancelPolicy.builder()
                .cancelType(1).type(RefundType.NO_DEDUCTION).before(36)
                .moveUpCancelDays(1).moveUpCancelHour("18:00:00").timeZone("Asia/Shanghai")
                .build());
    }

    @Test
    @DisplayName("写：退改条款要真的进缓存载荷")
    void policyIsWrittenIntoTheQuote() {
        ProductRespDTO p = ProductRespDTO.builder()
                .hotelId("H1").productId("易腐票").productKey("k".repeat(64))
                .cancelPolicy(freeCancel())
                .priceInfos(List.of(PriceInfo.builder().date(DATE).price(29317).build()))
                .build();

        service.productToCache(List.of(p), req(), sup());

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(redisUtils, Mockito.atLeastOnce())
                .setex(keyCap.capture(), valCap.capture(), anyLong());
        String quote = null;
        for (int i = 0; i < keyCap.getAllValues().size(); i++) {
            if (keyCap.getAllValues().get(i).startsWith("quote:")) {
                quote = valCap.getAllValues().get(i);
            }
        }
        assertNotNull(quote, "没找到 quote 写入，实际键: " + keyCap.getAllValues());
        assertTrue(quote.contains("cancelType"),
                "退改条款没进缓存——上游会按不可退兜底: " + quote);
    }

    @Test
    @DisplayName("读：退改条款要原样装回响应")
    void policyComesBackOnRead() {
        String pk = "k".repeat(64);
        Mockito.when(redisUtils.hashMapListAndKey(Mockito.anyList()))
                .thenReturn(Map.of("price:10010:H1:1:" + DATE,
                        Map.of(pk, "{\"price\":29317,\"taxes\":0,\"roomPrice\":29317}")));
        // 缓存里的票据载荷带着条款
        Mockito.when(redisUtils.get("quote:10010:H1:" + pk)).thenReturn(
                JsonUtils.writeObject2Json(java.util.Map.of(
                        "productId", "易腐票", "productKey", pk,
                        "cancelPolicy", List.of(java.util.Map.of(
                                "cancelType", 1, "before", 36, "type", "NO_DEDUCTION")))));

        List<ProductRespDTO> out = service.getPrice(req(), sup());

        assertEquals(1, out.size());
        List<CancelPolicy> policy = out.get(0).getCancelPolicy();
        assertNotNull(policy, "退改条款丢了——渠道侧会把可免费取消的房显示成不可退");
        assertEquals(1, policy.size());
        assertEquals(1, policy.get(0).getCancelType(), "cancelType=1 才是可取消");
        assertEquals(36, policy.get(0).getBefore());
    }
}
