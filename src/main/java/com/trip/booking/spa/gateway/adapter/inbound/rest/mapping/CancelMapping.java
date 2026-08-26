package com.trip.booking.spa.gateway.adapter.inbound.rest.mapping;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;

/**
 * 取消能力的对外形状翻译：JSON 契约 ↔ 领域模型，只此一处。
 *
 * <p>①层持有翻译，②③不知道 JSON 长什么样——这是五个能力面里第一个矫正依赖方向的
 * （此前能力接口直接吃 REST DTO，适配层甚至能反向改写上游传来的 request 对象）。
 * 对外遗留形状（sOrderStatus 的 0/1/2 码）也收在这里：此前两家实现各写一遍，
 * 一家三个具名常量、一家嵌套三元，同一张码表两种笔迹。
 */
public final class CancelMapping {

    /** 对外的取消状态码：0 取消成功 / 1 取消中（结果待确证）/ 2 取消失败 */
    private static final int S_ORDER_STATUS_CANCELED = 0;
    private static final int S_ORDER_STATUS_PENDING = 1;
    private static final int S_ORDER_STATUS_FAILED = 2;

    private CancelMapping() {
    }

    public static CancelCommand toCommand(CancelReq req) {
        return CancelCommand.of(req.getSupplierId(), req.getOrderId(), req.getSupplierOrderId());
    }

    public static CancelRespDTO toDto(CancelResult result) {
        CancelPenalty penalty = result.penalty();
        return CancelRespDTO.builder()
                .outcome(result.outcome())
                .orderId(result.orderId())
                .sOrderId(result.supplierOrderId())
                .sOrderStatus(statusCodeOf(result.outcome()))
                .message(result.message())
                .orderDesc(result.message())
                .cancelFee(penalty.amount() == null ? null : penalty.amount().amountCents())
                .cancelFeeCurrency(penalty.amount() == null ? null : penalty.amount().currency())
                .penaltySource(penalty.source().name())
                .supplierErrorCode(result.supplierErrorCode())
                .failureKind(result.failureKind() == null ? null : result.failureKind().name())
                .build();
    }

    private static Integer statusCodeOf(CancelOutcome outcome) {
        if (outcome == CancelOutcome.SUCCESS) {
            return S_ORDER_STATUS_CANCELED;
        }
        return outcome == CancelOutcome.FAILED ? S_ORDER_STATUS_FAILED : S_ORDER_STATUS_PENDING;
    }
}
