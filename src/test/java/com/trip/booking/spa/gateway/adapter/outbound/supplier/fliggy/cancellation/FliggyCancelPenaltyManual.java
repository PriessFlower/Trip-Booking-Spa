package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyOrderDetailResponse;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 罚金来源的<b>取证工具</b>（手动跑，CI 不含）：对一笔真实订单打查单，看罚金三件套
 * （{@code total_room_price} / {@code buyer_real_refund} / {@code currency_code}）
 * 与 {@link FliggyCancelSyncServiceImpl#penaltyOf} 的结论。
 *
 * <p>罚金已改为取自订单详情（docs/fliggy/distribution-api.md §6），而真取消一笔单是
 * 花钱且不可逆的动作。这条只读通路让「详情拿不拿得到罚金、入参形态对不对」先单独验掉。
 *
 * <pre>
 * set -a; . .env; set +a
 * FLIGGY_DETAIL=1 mvn test -Dtest=FliggyCancelPenaltyManual -Dspa.detail.orderId=&lt;我方单号&gt;
 * </pre>
 *
 * <p>本机出口在美国，飞猪实测须借国内出口（{@code -DsocksProxyHost}）。
 */
@EnabledIfEnvironmentVariable(named = "FLIGGY_DETAIL", matches = "1")
class FliggyCancelPenaltyManual {

    @Test
    void probeOneOrderDetail() {
        String orderId = System.getProperty("spa.detail.orderId");
        assertNotNull(orderId, "须给 -Dspa.detail.orderId=<我方单号>");

        FliggyProperties props = new FliggyProperties();
        props.setAppKey(System.getenv("FLIGGY_APP_KEY"));
        ReflectionTestUtils.setField(props, "secret", System.getenv("FLIGGY_SECRET"));
        ReflectionTestUtils.setField(props, "session", System.getenv("FLIGGY_SESSION"));
        ReflectionTestUtils.setField(props, "distributor", System.getenv("FLIGGY_DISTRIBUTOR"));
        String host = System.getenv("FLIGGY_API_HOST");
        ReflectionTestUtils.setField(props, "urlHost",
                host == null || host.isBlank() ? "https://eco.taobao.com/router/rest" : host);
        assertTrue(props.isConfigured(), "缺 FLIGGY_* 凭据（先 source .env）");

        RateLimitHolder holder = new RateLimitHolder();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(new RateLimitManager() {
            @Override public void acquire(String key) { }
            @Override public boolean tryAcquire(String key) { return true; }
            @Override public boolean isRegistered(String key) { return true; }
        });
        holder.setApplicationContext(ctx);

        ResponseResult<FliggyOrderDetailResponse> result = new QueryOrderAccess(props)
                .access(QueryOrderAccess.callByOrderId(orderId, props.getDistributor()), CallPurpose.ORDER);
        FliggyOrderDetailResponse resp = result == null ? null : result.getData();

        System.out.println("[detail] raw=" + (result == null ? null : result.getOrigData()));
        if (resp == null) {
            return;
        }
        System.out.println("[detail] platformError=" + resp.platformError()
                + ", isSucc=" + resp.isSucc()
                + ", status=" + resp.orderStatus() + "/" + resp.orderStatusDesc()
                + ", total=" + resp.totalRoomPrice()
                + ", refund=" + resp.buyerRealRefund()
                + ", currency=" + resp.currencyCode());
        if (resp.isSucc()) {
            CancelPenalty penalty = FliggyCancelSyncServiceImpl.penaltyOf(resp, orderId);
            System.out.println("[detail] penaltySource=" + penalty.source() + ", amount="
                    + (penalty.amount() == null ? null
                    : penalty.amount().amountCents() + " " + penalty.amount().currency()));
        }
    }
}
