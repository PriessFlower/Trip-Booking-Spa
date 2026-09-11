package com.trip.booking.spa.gateway.domain.booking;

import java.util.Objects;

/**
 * 下单结果：②③层的出参。对外 JSON 的形状（{@code sOrderId} 那类线上字段名）在 ① 的
 * BookingMapping，本类只承载事实。
 *
 * <p>三态由工厂钉死（{@link #success}／{@link #failed}／{@link #unknown}），
 * outcome 不可能为 null——此前模板靠运行期检查"实现忘了填 outcome"，现在这类遗忘
 * 在构造上就不成立。
 *
 * <p><b>UNKNOWN 不是失败</b>：请求可能已送达供应商而响应丢失，本地无从区分。
 * 上游必须凭我方单号查单确证，不得直接退款重下，见 {@link BookingOutcome}。
 */
public final class BookingResult {

    private final BookingOutcome outcome;
    private final String orderId;
    private final String supplierOrderId;
    private final String confirmationNumber;
    private final String supplierErrorCode;
    private final String supplierErrorMessage;
    /** 人可读的结果说明，仅用于日志与排障，禁止作为分支判据 */
    private final String message;

    private BookingResult(BookingOutcome outcome, String orderId, String supplierOrderId,
                          String confirmationNumber, String supplierErrorCode,
                          String supplierErrorMessage, String message) {
        this.outcome = Objects.requireNonNull(outcome);
        this.orderId = orderId;
        this.supplierOrderId = supplierOrderId;
        this.confirmationNumber = confirmationNumber;
        this.supplierErrorCode = supplierErrorCode;
        this.supplierErrorMessage = supplierErrorMessage;
        this.message = message;
    }

    /** 供应商确认订单成立。supplierOrderId 此时必然有值——没有单号的"成功"无法对账 */
    public static BookingResult success(String orderId, String supplierOrderId,
                                        String confirmationNumber, String message) {
        return new BookingResult(BookingOutcome.SUCCESS, orderId,
                Objects.requireNonNull(supplierOrderId, "SUCCESS 必须带供应商单号"),
                confirmationNumber, null, null, message);
    }

    /**
     * 确定失败：供应商侧<b>什么都没发生</b>，上游可直接终结订单并退款，不必查单。
     * 须带该家原生错误码供事后复核判据。
     */
    public static BookingResult failed(String orderId, String supplierErrorCode,
                                       String supplierErrorMessage, String message) {
        return new BookingResult(BookingOutcome.FAILED, orderId, null, null,
                supplierErrorCode, supplierErrorMessage, message);
    }

    /** 结果不确定，上游必须查单确证。所有兜底路径都应落到这里 */
    public static BookingResult unknown(String orderId, String message) {
        return new BookingResult(BookingOutcome.UNKNOWN, orderId, null, null, null, null, message);
    }

    /** UNKNOWN 但供应商给了错误码：码留着供事后校准判据（该判 FAILED 还是 UNKNOWN） */
    public static BookingResult unknown(String orderId, String supplierErrorCode,
                                        String supplierErrorMessage, String message) {
        return new BookingResult(BookingOutcome.UNKNOWN, orderId, null, null,
                supplierErrorCode, supplierErrorMessage, message);
    }

    /** 补上我方单号：模板在实现漏填时回填，其余字段一律不动 */
    public BookingResult withOrderId(String fallbackOrderId) {
        return orderId != null ? this
                : new BookingResult(outcome, fallbackOrderId, supplierOrderId, confirmationNumber,
                supplierErrorCode, supplierErrorMessage, message);
    }

    public BookingOutcome outcome() {
        return outcome;
    }

    public String orderId() {
        return orderId;
    }

    public String supplierOrderId() {
        return supplierOrderId;
    }

    public String confirmationNumber() {
        return confirmationNumber;
    }

    public String supplierErrorCode() {
        return supplierErrorCode;
    }

    public String supplierErrorMessage() {
        return supplierErrorMessage;
    }

    public String message() {
        return message;
    }
}
