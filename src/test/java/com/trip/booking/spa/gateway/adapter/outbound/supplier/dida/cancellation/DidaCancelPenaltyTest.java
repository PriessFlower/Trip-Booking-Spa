package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOrderWireNameTest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelResponse;
import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty.PenaltySource;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.gateway.domain.supplier.FailureKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住道旅罚金的来源：<b>预取消响应的 Amount+Currency</b>（官方 booking-pre-cancel 字段表；确认取消
 * 成功是空对象，无处可读）。缺一即 NONE——"字段给的 0"（2026-09-10 真实免罚单）与"没给所以不知道"
 * 是两回事，塌在一起上游就把「不知道」当「免费取消」。
 */
class DidaCancelPenaltyTest {

    @Test
    @DisplayName("真实预取消报文：6849 CNY → FIELD 684900 分 CNY")
    void realPenaltyIsField() throws IOException {
        CancelPenalty penalty = DidaCancelSyncServiceImpl.penaltyOf(
                DidaOrderWireNameTest.read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class), "T-1");
        assertEquals(PenaltySource.FIELD, penalty.source());
        assertEquals(684900L, penalty.amount().amountCents());
        assertEquals("CNY", penalty.amount().currency());
    }

    @Test
    @DisplayName("真实免罚单（Amount=0）：是确定的 0，不是不知道")
    void realZeroIsFieldZero() throws IOException {
        CancelPenalty penalty = DidaCancelSyncServiceImpl.penaltyOf(
                DidaOrderWireNameTest.read("/dida/booking-cancel-free-real-20260910.json", DidaBookingCancelResponse.class), "T-2");
        assertEquals(PenaltySource.FIELD, penalty.source());
        assertEquals(0L, penalty.amount().amountCents());
    }

    @Test
    @DisplayName("币种缺席 / 金额缺席 / 金额为负 / 无响应 → NONE")
    void incompleteIsUnknown() throws IOException {
        DidaBookingCancelResponse noCurrency = DidaOrderWireNameTest.read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class);
        noCurrency.getSuccess().setCurrency(null);
        assertEquals(PenaltySource.NONE, DidaCancelSyncServiceImpl.penaltyOf(noCurrency, "T-3").source());

        DidaBookingCancelResponse noAmount = DidaOrderWireNameTest.read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class);
        noAmount.getSuccess().setAmount(null);
        assertEquals(PenaltySource.NONE, DidaCancelSyncServiceImpl.penaltyOf(noAmount, "T-4").source());

        DidaBookingCancelResponse negative = DidaOrderWireNameTest.read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class);
        negative.getSuccess().setAmount(new java.math.BigDecimal("-1"));
        assertEquals(PenaltySource.NONE, DidaCancelSyncServiceImpl.penaltyOf(negative, "T-5").source());

        CancelPenalty none = DidaCancelSyncServiceImpl.penaltyOf(null, "T-6");
        assertEquals(PenaltySource.NONE, none.source());
        assertNull(none.amount());
    }

    /** 凭证未配置属我方配置病：态是 FAILED（供应商侧确未发生动作），成因标 AUTH_CONFIG，经公开入口走一遍模板 */
    @Test
    @DisplayName("凭证未配置 → FAILED + AUTH_CONFIG + credentials_missing，罚金 NONE")
    void missingCredentialsIsAuthConfig() {
        DidaProperties props = new DidaProperties();
        DidaCancelSyncServiceImpl service = new DidaCancelSyncServiceImpl();
        ReflectionTestUtils.setField(service, "properties", props);

        CancelResult result = service.cancel(CancelCommand.of(10020, "TB-AUTH-1", null));

        assertEquals(CancelOutcome.FAILED, result.outcome());
        assertEquals(FailureKind.AUTH_CONFIG, result.failureKind());
        assertEquals("credentials_missing", result.supplierErrorCode());
        assertEquals(PenaltySource.NONE, result.penalty().source());
    }
}
