package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.booking;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CreateOrderResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.booking.ExpediaBookingClassifier.Classification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉死下单响应的三态判定。
 *
 * <p>用例中的响应体取自 2026-08-10 沙箱实测原文，非构造样本。
 * 这些判据每一条都对应一种资损：判错方向就是「已退款却仍占房」或「凭空承认订单」。
 */
class ExpediaBookingClassifierTest {

    private static CreateOrderResponse resp(String itineraryId, String type) {
        CreateOrderResponse r = new CreateOrderResponse();
        r.setItinerary_id(itineraryId);
        r.setType(type);
        return r;
    }

    /** 拿到订单号即成功 */
    @Test
    void itineraryIdMeansSuccess() {
        assertEquals(Classification.SUCCESS,
                ExpediaBookingClassifier.classify(201, resp("7717630846973", null), "{}"));
    }

    /** 响应撕裂：错误字段与订单号同时出现时，订单已成立，按订单号判 */
    @Test
    void itineraryIdWinsOverErrorFields() {
        assertEquals(Classification.SUCCESS,
                ExpediaBookingClassifier.classify(400, resp("7717630846973", "some_error"), "{}"),
                "订单号已下发即表示订单成立，不得因并存的错误字段判失败");
    }

    /**
     * duplicate_itinerary 虽为 HTTP 400，含义却是「首次已成功」。
     * 实测原文取自沙箱重复下单。
     */
    @Test
    void duplicateItineraryIsNotFailure() {
        String raw = "{\"type\":\"invalid_input\",\"message\":\"An invalid request was sent in, "
                + "please check the nested errors for details.\",\"errors\":[{\"type\":\"duplicate_itinerary\","
                + "\"message\":\"An itinerary already exists with this affiliate reference id.\"}]}";

        assertEquals(Classification.DUPLICATE,
                ExpediaBookingClassifier.classify(400, resp(null, "invalid_input"), raw),
                "判成失败会导致上游退款，而 Expedia 侧订单仍在——两头空");
    }

    /** 无响应（超时、连接中断）不足以断定未下单 */
    @Test
    void noResponseIsIndeterminate() {
        assertEquals(Classification.INDETERMINATE,
                ExpediaBookingClassifier.classify(0, null, null));
    }

    /** EPS 明确规定 5xx 表示双方都不知道最终状态 */
    @Test
    void serverErrorsAreIndeterminate() {
        for (int code : new int[]{500, 502, 503, 504}) {
            assertEquals(Classification.INDETERMINATE,
                    ExpediaBookingClassifier.classify(code, null, "{}"),
                    code + " 须按结果不确定处理");
        }
    }

    /** 499 表示请求可能已在供应商侧生效 */
    @Test
    void code499IsIndeterminate() {
        assertEquals(Classification.INDETERMINATE,
                ExpediaBookingClassifier.classify(499, null, "{}"));
    }

    /** EPS 规定 409/410 须先反查是否已产生重复订单 */
    @Test
    void conflictAndGoneAreIndeterminate() {
        assertEquals(Classification.INDETERMINATE,
                ExpediaBookingClassifier.classify(409, null, "{}"));
        assertEquals(Classification.INDETERMINATE,
                ExpediaBookingClassifier.classify(410, null, "{}"));
    }

    /** 参数非法属业务性拒绝，重试必再败。实测原文取自沙箱缺 billing_contact 的响应 */
    @Test
    void invalidInputIsDeterministicFailure() {
        String raw = "{\"type\":\"invalid_input\",\"message\":\"An invalid request was sent in, "
                + "please check the nested errors for details.\",\"errors\":[{\"type\":"
                + "\"payments.billing_contact.required\",\"message\":\"Billing contact is required.\"}]}";

        assertEquals(Classification.DETERMINISTIC_FAILURE,
                ExpediaBookingClassifier.classify(400, resp(null, "invalid_input"), raw));
    }

    /** 2xx 却没有订单号，形态不可判读，不敢当成功也不敢当失败 */
    @Test
    void successStatusWithoutItineraryIdIsIndeterminate() {
        assertEquals(Classification.INDETERMINATE,
                ExpediaBookingClassifier.classify(200, resp(null, null), "{}"));
    }
}
