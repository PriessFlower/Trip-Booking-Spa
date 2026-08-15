package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉死对外字段名。
 *
 * <p>字段名以单个小写字母开头、紧跟大写字母时，Jackson 默认会把前导大写串一并压平，
 * {@code sOrderId} 被序列化成 {@code sorderId}；而入站方向大小写不敏感仍能认原名，
 * 两个方向不对称，只读 Java 字段名发现不了。实测（2026-08-11 沙箱）下单成功的响应里
 * 确实是 {@code sorderId}，上游若按文档里的字段名取值必然拿到空。
 *
 * <p>这类偏差不会让任何逻辑报错，只会让上游静默读到 null，故必须由测试守住。
 */
class RespDtoWireNameTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bookingRespKeepsSupplierOrderIdFieldName() throws Exception {
        String json = mapper.writeValueAsString(BookingRespDTO.builder()
                .outcome(BookingOutcome.SUCCESS)
                .sOrderId("7800466075463")
                .sConfirmationNumber("191655787591126")
                .build());

        assertTrue(json.contains("\"sOrderId\""), "线上字段名被压平会让上游静默读到空: " + json);
        assertTrue(json.contains("\"sConfirmationNumber\""), json);
    }

    @Test
    void cancelRespKeepsSupplierOrderIdFieldName() throws Exception {
        String json = mapper.writeValueAsString(CancelRespDTO.builder().sOrderId("7800466075463").build());

        assertTrue(json.contains("\"sOrderId\""), json);
    }

    /**
     * sOrderStatus 同样会被 Jackson 压成 sorderStatus。
     *
     * <p>此前取消未实现，该字段从未被真正序列化，故该缺陷一直潜伏——直到取消接出才暴露。
     * 上游按 sOrderStatus 读取时会静默得到空，把「取消失败」读成「无状态」。
     */
    @Test
    void cancelRespKeepsSupplierOrderStatusFieldName() throws Exception {
        String json = mapper.writeValueAsString(CancelRespDTO.builder().sOrderStatus(2).build());

        assertTrue(json.contains("\"sOrderStatus\""), "线上字段名被压平会让上游静默读到空: " + json);
        assertFalse(json.contains("\"sorderStatus\""), json);
    }
}
