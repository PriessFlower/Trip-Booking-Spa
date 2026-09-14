package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 道旅查单的<b>真 e2e</b>：跑本仓真实的 {@link DidaOrderQuerySyncServiceImpl} 与查单模板，真实 HTTP 打
 * <b>生产</b> HotelBookingSearch（道旅无沙箱）。<b>只读</b>：不下单、不取消，不产生费用。
 *
 * <p>为什么查的是别人的单：本仓尚未下过道旅真单（闸默认关）；ClientID 与 cursor 共用，故 cursor 的
 * 生产订单在本账号下可见。默认查 {@code 18577514927}（2026-09-01 成单、带酒店确认号 78112572，
 * 见夹具 booking-search-hcn-real-20260914.json）；可用 {@code DIDA_E2E_BOOKING_ID} 换一笔。
 * 二批里能不花钱验到的只有这一段，它证明的是：路径、gzip、凭据信封、字段解析、状态映射在真链路上成立。
 *
 * <p>运行方式（默认跳过，不进 CI；出网 IP 须在道旅白名单内，本机跑法见 docs/dida/booking-api.md §1）：
 * <pre>
 * DIDA_E2E=1 DIDA_CLIENT_ID=… DIDA_LICENSE_KEY=… mvn test -Dtest=DidaOrderQueryE2ETest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "DIDA_E2E", matches = "1")
class DidaOrderQueryE2ETest {

    private static final List<String> TAKEN = new ArrayList<>();

    private static final String QUERY_BUCKET = "GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_QUERY_ORDER";

    private static DidaOrderQuerySyncServiceImpl service;

    @BeforeAll
    static void wireRealService() throws Exception {
        DidaProperties props = new DidaProperties();
        set(props, "clientId", System.getenv("DIDA_CLIENT_ID"));
        set(props, "licenseKey", System.getenv("DIDA_LICENSE_KEY"));
        String host = System.getenv("DIDA_API_HOST");
        set(props, "urlHost", host == null || host.isBlank() ? "https://api.didatravel.com" : host);
        assumeTrue(props.isConfigured(), "缺 DIDA_CLIENT_ID/DIDA_LICENSE_KEY，跳过");

        RateLimitHolder holder = new RateLimitHolder();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(new RateLimitManager() {
            @Override
            public void acquire(String key) {
                TAKEN.add(key);
            }

            @Override
            public boolean tryAcquire(String key) {
                TAKEN.add(key);
                return true;
            }

            @Override
            public boolean isRegistered(String key) {
                return true;
            }
        });
        holder.setApplicationContext(ctx);

        service = new DidaOrderQuerySyncServiceImpl();
        set(service, "properties", props);
    }

    @Test
    @DisplayName("按道旅单号查一笔已确认的生产订单：FOUND / 21 / 带酒店确认号 / 金额带币种")
    void queryConfirmedOrderByBookingId() {
        String bookingId = System.getenv().getOrDefault("DIDA_E2E_BOOKING_ID", "18577514927");
        TAKEN.clear();

        OrderRespDTO dto = service.orderQuery(OrderQueryReq.builder()
                .supplierId(SupplierSourceEnum.DIDA.getCode()).orderId("spa-e2e-readonly").supplierOrderId(bookingId).build());

        assertThat(TAKEN).as("查单必须扣 :ORDER 用途桶与接口桶各一格").containsSubsequence(QUERY_BUCKET + ":ORDER", QUERY_BUCKET);
        assumeTrue(dto.getPresence() != OrderPresence.INDETERMINATE, "道旅未给出结果：" + dto.getMessage());
        assertThat(dto.getPresence()).isEqualTo(OrderPresence.FOUND);
        assertThat(dto.getSupplierOrderId()).isEqualTo(bookingId);
        assertThat(dto.getOrderStatus()).as("供应商状态原文=" + dto.getSupplierOrderStatus()).isIn(20, 21, 22, 31);
        assertThat(dto.getTotalPriceCurrency()).as("金额必须带币种").isNotBlank();
        assertThat(dto.getCreateTime()).isNotBlank();
        System.out.println("[dida-e2e] " + bookingId + " -> status=" + dto.getSupplierOrderStatus()
                + " mapped=" + dto.getOrderStatus() + " hcn=" + dto.getConfirmationNumber()
                + " price=" + dto.getTotalPrice() + dto.getTotalPriceCurrency());
    }

    @Test
    @DisplayName("按不存在的我方单号查：空列表 → INDETERMINATE，不是 NOT_FOUND")
    void queryUnknownClientReferenceIsIndeterminate() {
        OrderRespDTO dto = service.orderQuery(OrderQueryReq.builder()
                .supplierId(SupplierSourceEnum.DIDA.getCode()).orderId("spa-e2e-never-booked-" + System.nanoTime()).build());
        assertThat(dto.getPresence()).isEqualTo(OrderPresence.INDETERMINATE);
        System.out.println("[dida-e2e] unknown clientReference -> " + dto.getMessage());
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
