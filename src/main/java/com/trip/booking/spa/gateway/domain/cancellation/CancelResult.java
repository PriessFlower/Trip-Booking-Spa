package com.trip.booking.spa.gateway.domain.cancellation;

import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.domain.supplier.FailureKind;

import java.util.Objects;

/**
 * 取消结果：②③层的出参。对外 JSON 的形状（sOrderStatus 的 0/1/2 码等）在 ① 的
 * CancelMapping，本类只承载事实。
 *
 * <p>三态由工厂钉死（{@link #success}／{@link #failed}／{@link #unknown}），
 * outcome 不可能为 null——此前模板靠运行期检查"实现忘了填 outcome"，现在这类遗忘
 * 在构造上就不成立。两家适配层各自的中间载体（艺龙 CancelOutcomeHolder、Expedia
 * 内部 CancelResult）由本类取代：同一个形状写了四遍的那个类，就是它。
 */
public final class CancelResult {

    private final CancelOutcome outcome;
    private final String orderId;
    private final String supplierOrderId;
    private final CancelPenalty penalty;
    private final String supplierErrorCode;
    private final String message;
    /** 失败成因档，可空；目前唯一取值 AUTH_CONFIG（我方配置病），纪律见 {@link FailureKind} */
    private final FailureKind failureKind;

    private CancelResult(CancelOutcome outcome, String orderId, String supplierOrderId,
                         CancelPenalty penalty, String supplierErrorCode, String message,
                         FailureKind failureKind) {
        this.outcome = Objects.requireNonNull(outcome);
        this.orderId = orderId;
        this.supplierOrderId = supplierOrderId;
        this.penalty = Objects.requireNonNull(penalty, "罚金不明请用 CancelPenalty.unknown()，不是 null");
        this.supplierErrorCode = supplierErrorCode;
        this.message = message;
        this.failureKind = failureKind;
    }

    public static CancelResult success(String orderId, String supplierOrderId,
                                       CancelPenalty penalty, String message) {
        return new CancelResult(CancelOutcome.SUCCESS, orderId, supplierOrderId, penalty, null, message, null);
    }

    /** 确定失败：供应商明确给出业务性拒绝，重试必再失败。须带该家原生错误码供辨识 */
    public static CancelResult failed(String orderId, String supplierOrderId,
                                      String supplierErrorCode, String message) {
        return new CancelResult(CancelOutcome.FAILED, orderId, supplierOrderId,
                CancelPenalty.unknown(), supplierErrorCode, message, null);
    }

    /** 结果不确定：取消可能已生效。message 必须引导上游查单确证 */
    public static CancelResult unknown(String orderId, String supplierOrderId,
                                       String supplierErrorCode, String message) {
        return new CancelResult(CancelOutcome.UNKNOWN, orderId, supplierOrderId,
                CancelPenalty.unknown(), supplierErrorCode, message, null);
    }

    /** 模板兜底用：实现未回填我方单号时补齐，其余照抄 */
    public CancelResult withOrderId(String orderId) {
        return new CancelResult(outcome, orderId, supplierOrderId, penalty, supplierErrorCode, message,
                failureKind);
    }

    /** 标注失败成因档（如 AUTH_CONFIG）。②模板据此告警与埋点，①原样透出 */
    public CancelResult withFailureKind(FailureKind kind) {
        return new CancelResult(outcome, orderId, supplierOrderId, penalty, supplierErrorCode, message, kind);
    }

    public CancelOutcome outcome() {
        return outcome;
    }

    public String orderId() {
        return orderId;
    }

    public String supplierOrderId() {
        return supplierOrderId;
    }

    public CancelPenalty penalty() {
        return penalty;
    }

    public String supplierErrorCode() {
        return supplierErrorCode;
    }

    public String message() {
        return message;
    }

    /** 可为 null（多数失败并无特殊成因档） */
    public FailureKind failureKind() {
        return failureKind;
    }
}
