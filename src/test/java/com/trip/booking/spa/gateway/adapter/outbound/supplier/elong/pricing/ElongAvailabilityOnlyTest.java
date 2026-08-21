package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉死渠道验价档（{@code verifyLevel=AVAILABILITY}）的应答形状。
 *
 * <p><b>为什么必须有</b>：这一档不打验价，所以<b>没有任何"此刻可成单"的证据</b>。
 * 一旦它签发了 offerId 或回了 {@link CheckPriceOutcome#BOOKABLE}，上游就会拿一个必死的
 * 会话级凭证去建单——把不确定说成确定（R-1.6）。这两件事在编译期都拦不住，只能靠测试钉住。
 *
 * <p>另钉两条口径：退改取 {@code hotel.detail} 的 {@code PrepayResult}
 * （艺龙【国际酒店】国际对接指南明文要求），剩余房量取 {@code CurrentAlloment}
 * （2026-08-21 实测与验价回传的 {@code RestInventoryCount} 相等）。
 */
class ElongAvailabilityOnlyTest {

    private static CheckPriceRespDTO invoke(ElongRatePlan plan, VerifyLevel level) throws Exception {
        ElongPriceServiceImpl svc = new ElongPriceServiceImpl();
        Field deriver = ElongPriceServiceImpl.class.getDeclaredField("productKeyDeriver");
        deriver.setAccessible(true);
        deriver.set(svc, new ElongProductKeyDeriver());

        CheckPriceReq req = CheckPriceReq.builder()
                .supplierId(10010).sHotelId("61497910").sProductId("G1")
                .checkIn("2026-08-24").checkOut("2026-08-25")
                .roomNum(1).adultCount(1).childNum(0)
                .verifyLevel(level)
                .build();

        Method m = ElongPriceServiceImpl.class.getDeclaredMethod(
                "availabilityOnlyResp", CheckPriceReq.class, ElongRatePlan.class);
        m.setAccessible(true);
        return (CheckPriceRespDTO) m.invoke(svc, req, plan);
    }

    private static ElongRatePlan plan(Integer allotment) {
        ElongNightlyRate n = new ElongNightlyRate();
        n.setDate("2026-08-24T00:00:00+08:00");
        n.setRate(new BigDecimal("185.40"));
        n.setMinRate(new BigDecimal("161.97"));
        n.setMember(new BigDecimal("193.40"));
        ElongRatePlan p = new ElongRatePlan();
        p.setStatus(Boolean.TRUE);
        p.setRoomTypeId("0053");
        p.setGoodsUniqId("G1");
        p.setCurrentAlloment(allotment);
        p.setNightlyRates(List.of(n));
        return p;
    }

    @Test
    @DisplayName("回 AVAILABLE，绝不回 BOOKABLE——没验价就没有可订的证据")
    void reportsAvailableNotBookable() throws Exception {
        CheckPriceRespDTO resp = invoke(plan(2), VerifyLevel.AVAILABILITY);
        assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.AVAILABLE);
        assertThat(resp.getOutcome()).isNotEqualTo(CheckPriceOutcome.BOOKABLE);
    }

    @Test
    @DisplayName("offerId 必须为 null——现货里的马甲到下单时必已过期，签了会诱导上游拿必死凭据建单")
    void neverIssuesAnOfferHandle() throws Exception {
        CheckPriceRespDTO resp = invoke(plan(2), VerifyLevel.AVAILABILITY);
        assertThat(resp.getOfferId()).isNull();
        assertThat(resp.getOfferTtlSeconds()).isNull();
    }

    @Test
    @DisplayName("价格取 Rate、税费为 Rate−MinRate，与查价面同源")
    void priceUsesSettlementBasis() throws Exception {
        CheckPriceRespDTO resp = invoke(plan(2), VerifyLevel.AVAILABILITY);
        assertThat(resp.getSalePrice()).isEqualTo(18540);
        assertThat(resp.getPriceInfos()).hasSize(1);
        assertThat(resp.getPriceInfos().get(0).getRoomPrice()).isEqualTo(16197);
        assertThat(resp.getPriceInfos().get(0).getTaxes()).isEqualTo(2343);
    }

    @Test
    @DisplayName("剩余房量取 CurrentAlloment，0 原样透出而非当作无房")
    void remainRoomComesFromAllotmentIncludingZero() throws Exception {
        assertThat(invoke(plan(2), VerifyLevel.AVAILABILITY).getRemainRoomNum()).isEqualTo(2);
        assertThat(invoke(plan(0), VerifyLevel.AVAILABILITY).getRemainRoomNum()).isEqualTo(0);
    }

    @Test
    @DisplayName("缺每日价时落 INDETERMINATE，不许说成有货")
    void missingNightlyRateIsIndeterminate() throws Exception {
        ElongRatePlan p = plan(2);
        p.setNightlyRates(List.of());
        CheckPriceRespDTO resp = invoke(p, VerifyLevel.AVAILABILITY);
        assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.INDETERMINATE);
        assertThat(resp.getOfferId()).isNull();
    }

    @Test
    @DisplayName("退改取 detail 的 PrepayResult；解析不出即空列表，不猜")
    void cancelPolicyComesFromPrepayResultAndIsNeverGuessed() throws Exception {
        CheckPriceRespDTO resp = invoke(plan(2), VerifyLevel.AVAILABILITY);
        List<CancelPolicy> policies = resp.getCancelPolicy();
        assertThat(policies).isNotNull().isEmpty();
    }
}
