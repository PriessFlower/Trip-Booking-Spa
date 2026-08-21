package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongDataValidateResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 {@code H001189}（每日价口径不符）在自纠正失败后的分态。
 *
 * <p><b>为什么单独测</b>：这个码此前落在 classifyValidateError 的兜底分支，日志写「未核实错误码」。
 * 2026-08-21 查清后它已单列，日志也改了口径——但"单列"这件事编译期看不出来，一次重构就可能
 * 把它退回兜底分支，而退回去之后行为看似没变（都是 INDETERMINATE），只有日志措辞不同。
 * 那种回归靠人眼审是发现不了的。
 *
 * <p>同时钉住<b>不得升级为 RATE_DEAD</b>：该报价本身有效（Rate 两个接口一致、库存也在），
 * 拦下它的是 MinRate 口径差。说成 RATE_DEAD 就是把不知道的事说成确定的。
 */
class ElongDayPriceMismatchClassifyTest {

    private static CheckPriceRespDTO classify(String responseJson) throws Exception {
        ElongDataValidateResponse data = JsonUtils.readValue(responseJson, ElongDataValidateResponse.class);
        ElongRatePlan plan = new ElongRatePlan();
        plan.setGoodsUniqId("61582324A20A69427977A0Atest");
        CheckPriceReq req = CheckPriceReq.builder().supplierId(10010).sHotelId("61534233")
                .checkIn("2026-08-24").checkOut("2026-08-25").roomNum(1).adultCount(1).build();

        Method m = ElongPriceServiceImpl.class.getDeclaredMethod("classifyValidateError",
                CheckPriceReq.class, ElongRatePlan.class, ElongDataValidateResponse.class);
        m.setAccessible(true);
        return (CheckPriceRespDTO) m.invoke(new ElongPriceServiceImpl(), req, plan, data);
    }

    private static String rejected(String code) {
        return "{\"Code\":\"" + code + "\",\"Result\":{\"ResultCode\":\"Rate\","
                + "\"ErrorMessage\":\"" + code + "\"}}";
    }

    @Test
    @DisplayName("H001189 走专属分支：落 INDETERMINATE，且文案点名成因")
    void perDayMismatchIsIndeterminate() throws Exception {
        CheckPriceRespDTO resp = classify(rejected("H001189|每日价传参异常，2026-08-24价格异常"));

        assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.INDETERMINATE);
        assertThat(resp.getMessage()).contains("H001189");
        // 「每日价口径不符」只出现在专属分支里。断言它，是为了让"分支被重构回兜底"这种回归
        // 真的能变红——只断言 outcome 和错误码是不够的，兜底分支两者完全相同（2026-08-21
        // 第一版反证就是这么假通过的）
        assertThat(resp.getMessage()).as("文案须点名成因，否则与兜底分支无从区分")
                .contains("每日价口径不符");
        assertThat(resp.getMessage()).as("语义是可重试").contains("请稍后重试");
    }

    @Test
    @DisplayName("H001189 不得被当成产品级死态——报价本身是有效的")
    void perDayMismatchIsNotRateDead() throws Exception {
        assertThat(classify(rejected("H001189|每日价传参异常，2026-08-24价格异常")).getOutcome())
                .isNotEqualTo(CheckPriceOutcome.RATE_DEAD);
    }

    @Test
    @DisplayName("与 H001188 分开：后者是我方请求组装缺陷，两者不可归并")
    void doesNotSwallowTheAssemblyDefectCode() throws Exception {
        // 都是 INDETERMINATE，但走的是不同分支、落不同日志。这里锁住"H001188 仍被单独识别"，
        // 防止有人图省事把 188/189 合成一个 startsWith("H0011")
        assertThat(classify(rejected("H001188|每日价传参异常")).getOutcome())
                .isEqualTo(CheckPriceOutcome.INDETERMINATE);
        assertThat(classify(rejected("H001188|每日价传参异常")).getMessage())
                .as("H001188 的文案是「验价请求异常」，与 H001189 的「验价未通过」不同")
                .contains("验价请求异常");
        assertThat(classify(rejected("H001189|每日价传参异常")).getMessage())
                .contains("每日价口径不符");
    }

    @Test
    @DisplayName("真正未核实的码仍走兜底，不被 H001189 分支误吞")
    void unknownCodesStillFallThrough() throws Exception {
        CheckPriceRespDTO resp = classify(rejected("H009999|某个没见过的码"));

        assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.INDETERMINATE);
        assertThat(resp.getMessage()).contains("H009999");
        assertThat(resp.getMessage()).as("未核实的码不该被贴上已查清的成因")
                .doesNotContain("每日价口径不符");
    }
}
