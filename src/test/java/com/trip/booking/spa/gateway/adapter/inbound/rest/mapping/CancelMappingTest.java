package com.trip.booking.spa.gateway.adapter.inbound.rest.mapping;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.gateway.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住取消的对外翻译：领域 → JSON 只在 CancelMapping 一处发生。
 *
 * <p>要害是罚金：结构化字段（分 + 币种 + 来源）取代"拼进中文 message、单位元"的老路。
 * "无从得知"必须显式是 NONE 且金额为空——塌成 0 会被上游当免费取消。
 */
class CancelMappingTest {

    /** 艺龙形态：字段给出的罚金 45.79 元 → 4579 分 + CNY + FIELD */
    @Test
    void structuredPenaltySurvivesTranslation() {
        CancelResult result = CancelResult.success("TB1", "101092975649",
                CancelPenalty.fromField(Money.fromYuan(new BigDecimal("45.79"), "CNY")), "取消已受理");

        CancelRespDTO dto = CancelMapping.toDto(result);

        assertEquals(4579L, dto.getCancelFee());
        assertEquals("CNY", dto.getCancelFeeCurrency());
        assertEquals("FIELD", dto.getPenaltySource());
        assertEquals(0, dto.getSOrderStatus());
    }

    /** Expedia 形态：接口不给罚金 → NONE 且金额为空，绝不是 0 */
    @Test
    void unknownPenaltyIsNoneNotZero() {
        CancelResult result = CancelResult.success("TB1", "itin-9",
                CancelPenalty.unknown(), null);

        CancelRespDTO dto = CancelMapping.toDto(result);

        assertNull(dto.getCancelFee(), "无从得知不是 0——0 会被上游当免费取消");
        assertNull(dto.getCancelFeeCurrency());
        assertEquals("NONE", dto.getPenaltySource());
    }

    /** 遗留状态码表：SUCCESS→0 / UNKNOWN→1 / FAILED→2，原两家各写一遍，现收拢一处 */
    @Test
    void legacyStatusCodesKeepTheirWireShape() {
        assertEquals(1, CancelMapping.toDto(
                CancelResult.unknown("TB1", null, null, "请查单")).getSOrderStatus());
        assertEquals(2, CancelMapping.toDto(
                CancelResult.failed("TB1", null, "E1", "订单不存在")).getSOrderStatus());
    }

    /** FAILED 必须带供应商原生错误码透出（B6：失败分类必须可辨） */
    @Test
    void supplierErrorCodeSurvivesOnFailure() {
        CancelRespDTO dto = CancelMapping.toDto(
                CancelResult.failed("TB1", "888", "H001054", "供应商确认该订单不存在"));

        assertEquals("H001054", dto.getSupplierErrorCode());
    }

    /** 入参翻译：三个坐标原样进领域指令 */
    @Test
    void requestTranslatesToCommand() {
        CancelCommand command = CancelMapping.toCommand(CancelReq.builder()
                .supplierId(10010).orderId("TB1").supplierOrderId("999").build());

        assertEquals(10010, command.supplierId());
        assertEquals("TB1", command.orderId());
        assertEquals("999", command.supplierOrderId());
    }

    /** outcome 原样透出——mapper 只翻译形状，不改判定 */
    @Test
    void outcomePassesThroughUntouched() {
        assertEquals(CancelOutcome.SUCCESS, CancelMapping.toDto(CancelResult.success(
                "TB1", null, CancelPenalty.unknown(), null)).getOutcome());
    }
}
