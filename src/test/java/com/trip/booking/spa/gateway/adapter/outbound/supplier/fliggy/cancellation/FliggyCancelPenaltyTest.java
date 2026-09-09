package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyOrderDetailResponse;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty.PenaltySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住飞猪罚金的来源：<b>订单详情，不是取消响应的 forfeit_fee</b>（官方国际分销文档
 * §6「罚金金额以订单详情接口的返回为准」，其字段表里已无罚金）。
 *
 * <p>夹具是同一笔真实订单的两份生产报文（2026-09-09，大阪 MYSTAYS 堺筋本町，
 * 我方单号 260909173834659dd355e09a，联系人与住客姓名电话已脱敏）：取消响应仍带
 * {@code forfeit_fee=-380}，而订单详情是 3176 全额退（罚金 0）。两者直接打架——
 * 这就是为什么不能再读那个字段：照读会申报出一笔 -3.80 美元的"罚金"。
 */
class FliggyCancelPenaltyTest {

    private static FliggyOrderDetailResponse detail(String json) {
        return FliggyOrderDetailResponse.parse(json);
    }

    private static String realDetail() throws Exception {
        return Files.readString(Path.of("src/test/resources/fliggy/order-detail-real-20260909.json"));
    }

    @Test
    @DisplayName("真实免罚单：详情全额退 → 罚金 0 USD；同单取消响应的 forfeit_fee 是 -380，不参与")
    void fullRefundMeansZeroPenaltyAndForfeitFeeIsIgnored() throws Exception {
        CancelPenalty penalty = FliggyCancelSyncServiceImpl.penaltyOf(detail(realDetail()), "260909173834659dd355e09a");

        assertEquals(PenaltySource.FIELD, penalty.source(), "详情给全了，就该是确定的事实");
        assertEquals(0L, penalty.amount().amountCents(), "3176 房费 − 3176 实退 = 免罚");
        assertEquals("USD", penalty.amount().currency(), "币种取详情的 currency_code，不再硬认");

        String rawCancel = Files.readString(Path.of("src/test/resources/fliggy/cancel-real-20260909.json"));
        assertEquals(-380, FliggyCancelResponse.parse(rawCancel).forfeitFee(),
                "同单取消响应确实带着这个数——若把罚金改回读它，上面的 0 会变成 -380");
    }

    @Test
    @DisplayName("部分退款：房费 − 实退 = 罚金")
    void partialRefundYieldsPenalty() throws Exception {
        String json = realDetail().replace("\"buyer_real_refund\":3176", "\"buyer_real_refund\":1176");

        CancelPenalty penalty = FliggyCancelSyncServiceImpl.penaltyOf(detail(json), "T-PARTIAL");

        assertEquals(PenaltySource.FIELD, penalty.source());
        assertEquals(2000L, penalty.amount().amountCents());
    }

    @Test
    @DisplayName("实退为 0：分不清罚全款还是结算未完成 → 不知道，绝不按罚全款申报")
    void zeroRefundIsUnknownNotFullPenalty() throws Exception {
        String json = realDetail().replace("\"buyer_real_refund\":3176", "\"buyer_real_refund\":0");

        CancelPenalty penalty = FliggyCancelSyncServiceImpl.penaltyOf(detail(json), "T-ZERO");

        assertEquals(PenaltySource.NONE, penalty.source());
        assertNull(penalty.amount());
    }

    @Test
    @DisplayName("实退字段缺席（官方：取消未成功时为空）→ 不知道，不当免罚")
    void missingRefundIsUnknown() throws Exception {
        String json = realDetail().replace("\"buyer_real_refund\":3176,", "");

        CancelPenalty penalty = FliggyCancelSyncServiceImpl.penaltyOf(detail(json), "T-MISSING");

        assertEquals(PenaltySource.NONE, penalty.source());
    }

    @Test
    @DisplayName("实退大于房费：详情自相矛盾 → 不知道，不造负罚金")
    void refundOverPriceIsUnknown() throws Exception {
        String json = realDetail().replace("\"buyer_real_refund\":3176", "\"buyer_real_refund\":3556");

        CancelPenalty penalty = FliggyCancelSyncServiceImpl.penaltyOf(detail(json), "T-OVER");

        assertEquals(PenaltySource.NONE, penalty.source(), "cursor 那笔 -27.89 就是负罚金流出去的后果");
    }

    @Test
    @DisplayName("币种缺席 → 不知道（禁裸数值流转，Money 必须带币种）")
    void missingCurrencyIsUnknown() throws Exception {
        String json = realDetail().replace("\"currency_code\":\"USD\",", "");

        CancelPenalty penalty = FliggyCancelSyncServiceImpl.penaltyOf(detail(json), "T-NOCCY");

        assertEquals(PenaltySource.NONE, penalty.source());
    }
}
