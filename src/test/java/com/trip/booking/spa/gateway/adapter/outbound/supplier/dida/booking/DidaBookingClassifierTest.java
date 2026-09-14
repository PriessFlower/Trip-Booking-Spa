package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.DidaBookingClassifier.CancelStepClassification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.DidaBookingClassifier.CreateClassification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOrderWireNameTest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingDetails;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaError;
import com.trip.booking.spa.gateway.domain.booking.OrderState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 判据钉死：白名单制——只有确证"供应商侧无单、重试必再败"的码才判确定失败，表外一律不确定。
 * 反面是 cursor：下单读超时一律报失败，而 2026-09-10 其确认取消的超时实证已在道旅侧生效。
 */
class DidaBookingClassifierTest {

    @Test
    @DisplayName("真实成单报文（Status=2）→ CONFIRMED")
    void realConfirmedIsConfirmed() throws IOException {
        assertEquals(CreateClassification.CONFIRMED, DidaBookingClassifier.classifyCreate(
                DidaOrderWireNameTest.read("/dida/booking-confirm-real-20251128.json", DidaBookingConfirmResponse.class)));
    }

    @Test
    @DisplayName("成功信封但非终态（5 Pending / 6 OnRequest / 0 / 1 / 缺席）→ PENDING，不是成功也不是失败")
    void nonFinalStatusIsPending() {
        for (Integer status : new Integer[]{0, 1, 5, 6, null, 9}) {
            assertEquals(CreateClassification.PENDING, DidaBookingClassifier.classifyCreate(success("BK-1", status)), String.valueOf(status));
        }
    }

    @Test
    @DisplayName("终态 4 Failed → FINAL_FAILED；终态 3 Canceled → FINAL_CANCELED；成功却无单号 → INDETERMINATE")
    void finalStatesAndTornSuccess() {
        assertEquals(CreateClassification.FINAL_FAILED, DidaBookingClassifier.classifyCreate(success("BK-1", 4)));
        assertEquals(CreateClassification.FINAL_CANCELED, DidaBookingClassifier.classifyCreate(success("BK-1", 3)));
        assertEquals(CreateClassification.INDETERMINATE, DidaBookingClassifier.classifyCreate(success(null, 2)));
        assertEquals(CreateClassification.INDETERMINATE, DidaBookingClassifier.classifyCreate(success("", 2)));
    }

    @Test
    @DisplayName("确定失败白名单：校验/额度/风控阶段被拒，供应商侧无单")
    void deterministicFailuresAreWhitelisted() throws IOException {
        for (String code : new String[]{"-2", "3001", "3002", "3005", "3006", "3008", "3011", "3014", "3015", "3021",
                "3022", "3025", "3026", "3027", "3034", "3035", "3038", "3040", "3041", "3050", "4010", "4030", "4031"}) {
            assertEquals(CreateClassification.DETERMINISTIC_FAILURE, DidaBookingClassifier.classifyCreate(error(code, null)), code);
        }
        // 真实报文：3005 入住人信息不正确、-2 请求形态不合法
        assertEquals(CreateClassification.DETERMINISTIC_FAILURE, DidaBookingClassifier.classifyCreate(
                DidaOrderWireNameTest.read("/dida/booking-confirm-error-3005-real.json", DidaBookingConfirmResponse.class)));
        assertEquals(CreateClassification.DETERMINISTIC_FAILURE, DidaBookingClassifier.classifyCreate(
                DidaOrderWireNameTest.read("/dida/booking-confirm-error-minus2-real.json", DidaBookingConfirmResponse.class)));
    }

    @Test
    @DisplayName("说的是一笔已存在订单的码 → REQUERY；Error 里带 BookingID 更是 → REQUERY（优先于码）")
    void existingOrderCodesRequireRequery() {
        for (String code : new String[]{"3018", "3019", "3020", "3033", "3036", "3039", "3042", "3043", "3060", "3070", "3090"}) {
            assertEquals(CreateClassification.REQUERY, DidaBookingClassifier.classifyCreate(error(code, null)), code);
        }
        // 表外码但带单号：道旅侧已有相关订单
        assertEquals(CreateClassification.REQUERY, DidaBookingClassifier.classifyCreate(error("3016", "BK-9")));
        assertEquals(CreateClassification.REQUERY, DidaBookingClassifier.classifyCreate(error("3015", "BK-9")),
                "白名单码若带了单号，也得先反查——单号是比码更强的证据");
    }

    @Test
    @DisplayName("2017/2019 → AUTH_CONFIG：病在我方，不归因供应商")
    void authConfigCodes() {
        assertEquals(CreateClassification.AUTH_CONFIG, DidaBookingClassifier.classifyCreate(error("2017", null)));
        assertEquals(CreateClassification.AUTH_CONFIG, DidaBookingClassifier.classifyCreate(error("2019", null)));
    }

    @Test
    @DisplayName("无响应、3016 下单失败、3000/3010/3012 系统类、表外新码 → INDETERMINATE")
    void unknownAndSystemCodesAreIndeterminate() {
        assertEquals(CreateClassification.INDETERMINATE, DidaBookingClassifier.classifyCreate(null));
        for (String code : new String[]{"3000", "3010", "3012", "3016", "3080", "9999"}) {
            assertEquals(CreateClassification.INDETERMINATE, DidaBookingClassifier.classifyCreate(error(code, null)), code);
        }
    }

    @Test
    @DisplayName("订单状态 → 我方状态：2→BOOKED、3→CANCELED、4→BOOK_FAILED、0/1/5/6→BOOKING、表外→null 保留原文")
    void orderStatusMapping() {
        assertEquals(OrderState.BOOKED, DidaBookingClassifier.toOrderState(2));
        assertEquals(OrderState.CANCELED, DidaBookingClassifier.toOrderState(3));
        assertEquals(OrderState.BOOK_FAILED, DidaBookingClassifier.toOrderState(4));
        for (int s : new int[]{0, 1, 5, 6}) {
            assertEquals(OrderState.BOOKING, DidaBookingClassifier.toOrderState(s), String.valueOf(s));
        }
        assertNull(DidaBookingClassifier.toOrderState(7));
        assertNull(DidaBookingClassifier.toOrderState(null));
    }

    @Test
    @DisplayName("预取消：成功→ACCEPTED；3018→ALREADY_CANCELED（真实报文）；2017→AUTH_CONFIG；其余错误→确定失败；无响应→不确定")
    void preCancelClassification() throws IOException {
        assertEquals(CancelStepClassification.ACCEPTED, DidaBookingClassifier.classifyPreCancel(
                DidaOrderWireNameTest.read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class)));
        assertEquals(CancelStepClassification.ALREADY_CANCELED, DidaBookingClassifier.classifyPreCancel(
                DidaOrderWireNameTest.read("/dida/booking-cancel-error-3018-real-20260910.json", DidaBookingCancelResponse.class)));
        assertEquals(CancelStepClassification.AUTH_CONFIG, DidaBookingClassifier.classifyPreCancel(preCancelError("2017")));
        for (String code : new String[]{"3003", "3031", "4000", "9999"}) {
            assertEquals(CancelStepClassification.DETERMINISTIC_FAILURE, DidaBookingClassifier.classifyPreCancel(preCancelError(code)), code);
        }
        assertEquals(CancelStepClassification.INDETERMINATE, DidaBookingClassifier.classifyPreCancel(null));
    }

    @Test
    @DisplayName("确认取消：空 Success→ACCEPTED；3003/3004/3007→确定失败；4000 与表外码→不确定（超时会生效，不敢判败）")
    void cancelConfirmClassification() throws IOException {
        assertEquals(CancelStepClassification.ACCEPTED, DidaBookingClassifier.classifyCancelConfirm(
                DidaOrderWireNameTest.read("/dida/booking-cancel-confirm-real-20260131.json", DidaBookingCancelConfirmResponse.class)));
        for (String code : new String[]{"3003", "3004", "3007"}) {
            assertEquals(CancelStepClassification.DETERMINISTIC_FAILURE, DidaBookingClassifier.classifyCancelConfirm(confirmError(code)), code);
        }
        assertEquals(CancelStepClassification.ALREADY_CANCELED, DidaBookingClassifier.classifyCancelConfirm(confirmError("3018")));
        assertEquals(CancelStepClassification.AUTH_CONFIG, DidaBookingClassifier.classifyCancelConfirm(confirmError("2019")));
        for (String code : new String[]{"4000", "3000", "9999"}) {
            assertEquals(CancelStepClassification.INDETERMINATE, DidaBookingClassifier.classifyCancelConfirm(confirmError(code)), code);
        }
        assertEquals(CancelStepClassification.INDETERMINATE, DidaBookingClassifier.classifyCancelConfirm(null));
    }

    private static DidaBookingConfirmResponse success(String bookingId, Integer status) {
        DidaBookingDetails details = new DidaBookingDetails();
        details.setBookingId(bookingId);
        details.setStatus(status);
        DidaBookingConfirmResponse.Success success = new DidaBookingConfirmResponse.Success();
        success.setBookingDetails(details);
        DidaBookingConfirmResponse resp = new DidaBookingConfirmResponse();
        resp.setSuccess(success);
        return resp;
    }

    private static DidaBookingConfirmResponse error(String code, String bookingId) {
        DidaError error = new DidaError();
        error.setCode(code);
        error.setMessage("test");
        error.setBookingId(bookingId);
        DidaBookingConfirmResponse resp = new DidaBookingConfirmResponse();
        resp.setError(error);
        return resp;
    }

    private static DidaBookingCancelResponse preCancelError(String code) {
        DidaError error = new DidaError();
        error.setCode(code);
        DidaBookingCancelResponse resp = new DidaBookingCancelResponse();
        resp.setError(error);
        return resp;
    }

    private static DidaBookingCancelConfirmResponse confirmError(String code) {
        DidaError error = new DidaError();
        error.setCode(code);
        DidaBookingCancelConfirmResponse resp = new DidaBookingCancelConfirmResponse();
        resp.setError(error);
        return resp;
    }
}
