package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking.ElongBookingClassifier.CancelClassification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking.ElongBookingClassifier.Classification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCreateResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 判据钉死：白名单制——只有确证"供应商侧无单、重试必再败"的码才判确定失败，
 * 表外一律不确定（反面：cursor 把"未返回订单号"一律判失败 → 幽灵单）。
 */
class ElongBookingClassifierTest {

    @Test
    void orderIdWinsOverErrorCode() {
        // 响应撕裂：错误码与订单号并存时订单已成立，按订单号判（移植风险⑧）
        ElongOrderCreateResponse resp = create("H001085|底层异常", 12345L);
        assertEquals(Classification.SUCCESS, ElongBookingClassifier.classifyCreate(resp));
    }

    @Test
    void deterministicFailuresAreWhitelisted() {
        for (String code : new String[]{"H001012", "H001039", "H001083", "H001084", "H001097", "H001188", "H001197",
                "H000033"}) {
            assertEquals(Classification.DETERMINISTIC_FAILURE,
                    ElongBookingClassifier.classifyCreate(create(code + "|msg", null)), code);
        }
    }

    @Test
    void duplicateSuspectsRequireRequery() {
        assertEquals(Classification.DUPLICATE_SUSPECT,
                ElongBookingClassifier.classifyCreate(create("H001043|订单重复或过快提交", null)));
        assertEquals(Classification.DUPLICATE_SUSPECT,
                ElongBookingClassifier.classifyCreate(create("H001045|疑似重复订单", null)));
    }

    @Test
    void unknownCodesAndNoResponseAreIndeterminate() {
        // H001085 官方建议重试（底层提交异常）——供应商侧状态不明，绝不可判失败
        assertEquals(Classification.INDETERMINATE,
                ElongBookingClassifier.classifyCreate(create("H001085|底层提交订单异常", null)));
        assertEquals(Classification.INDETERMINATE,
                ElongBookingClassifier.classifyCreate(create("H999999|表外新码", null)));
        assertEquals(Classification.INDETERMINATE, ElongBookingClassifier.classifyCreate(null));
    }

    @Test
    void cancelSuccessAndIdempotentAlreadyCancelled() {
        assertEquals(CancelClassification.SUCCESS,
                ElongBookingClassifier.classifyCancel(cancel("0", Boolean.TRUE)));
        // H001056 已处于取消状态：目标状态已达成，幂等成功
        assertEquals(CancelClassification.SUCCESS,
                ElongBookingClassifier.classifyCancel(cancel("H001056|已取消", null)));
    }

    @Test
    void cancelDeterministicFailuresAreWhitelisted() {
        for (String code : new String[]{"H001054", "H001094", "H001139", "H001151"}) {
            assertEquals(CancelClassification.DETERMINISTIC_FAILURE,
                    ElongBookingClassifier.classifyCancel(cancel(code + "|msg", null)), code);
        }
    }

    @Test
    void cancelUnknownCodesAreIndeterminate() {
        assertEquals(CancelClassification.INDETERMINATE,
                ElongBookingClassifier.classifyCancel(cancel("H888888|表外", Boolean.FALSE)));
        assertEquals(CancelClassification.INDETERMINATE, ElongBookingClassifier.classifyCancel(null));
    }

    /**
     * A101010012 访问IP错误 = 我方配置病（AUTH_CONFIG），不归因供应商、须告警。
     * 白名单同样只登记有实证的码：表外的 A 类码（如频控 A201010001 走通道层、
     * 其余未实证的门禁码）仍走 INDETERMINATE 老路，不猜。
     */
    @Test
    void cancelIpWhitelistRejectionIsAuthConfig() {
        assertEquals(CancelClassification.AUTH_CONFIG,
                ElongBookingClassifier.classifyCancel(cancel("A101010012|访问IP错误,当前IP:1.2.3.4", null)));
        assertEquals(CancelClassification.INDETERMINATE,
                ElongBookingClassifier.classifyCancel(cancel("A999999999|表外门禁码", null)));
    }

    private static ElongOrderCreateResponse create(String code, Long orderId) {
        ElongOrderCreateResponse resp = new ElongOrderCreateResponse();
        resp.setCode(code);
        if (orderId != null) {
            ElongOrderCreateResponse.Result result = new ElongOrderCreateResponse.Result();
            result.setOrderId(orderId);
            resp.setResult(result);
        }
        return resp;
    }

    private static ElongOrderCancelResponse cancel(String code, Boolean successs) {
        ElongOrderCancelResponse resp = new ElongOrderCancelResponse();
        resp.setCode(code);
        if (successs != null) {
            ElongOrderCancelResponse.Result result = new ElongOrderCancelResponse.Result();
            result.setSuccesss(successs);
            resp.setResult(result);
        }
        return resp;
    }
}
