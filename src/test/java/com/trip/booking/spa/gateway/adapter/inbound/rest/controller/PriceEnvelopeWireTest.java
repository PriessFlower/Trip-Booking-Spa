package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ResponseDTO;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉死查价响应的线上形状——这是上游真正看到的东西。
 *
 * <p><b>兼容性前提</b>：上游 tg-trip-cursor 的 {@code SpaGatewayClient.queryPrices} 只看
 * HTTP 状态码与 {@code result} 是否为 null，{@code success}/{@code code}/{@code errorMsg}
 * 三个字段一个都没读；且 {@code SpaEnvelope} 标了 {@code @JsonIgnoreProperties(ignoreUnknown)}、
 * ObjectMapper 关了 {@code FAIL_ON_UNKNOWN_PROPERTIES}。故新增 {@code outcome} 字段
 * 对上游是纯增量：现有分支一条都不改变，等它接线后才开始区分无货与未能确认。
 *
 * <p>本测试守的就是这个「纯增量」——{@code result} 的形状（数组）和失败时的
 * {@code result=null} 都不许变，否则上游反序列化会失败并把整家供应商判成不可用。
 */
class PriceEnvelopeWireTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(ResponseDTO<?> resp) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(resp));
    }

    @Test
    void availableKeepsArrayResultAndCarriesOutcome() throws Exception {
        JsonNode node = json(SpaController.toPriceResponse(
                PricingOutcome.AVAILABLE, List.of(new ProductRespDTO())));

        assertTrue(node.path("result").isArray(), "result 必须仍是数组: " + node);
        assertTrue(node.path("success").asBoolean());
        assertEquals("AVAILABLE", node.path("outcome").asText());
    }

    /** 「确实没有」是成功的回答：如实回空数组，上游据此可停止重试 */
    @Test
    void noInventoryIsASuccessfulEmptyArray() throws Exception {
        JsonNode node = json(SpaController.toPriceResponse(PricingOutcome.NO_INVENTORY, List.of()));

        assertTrue(node.path("result").isArray());
        assertEquals(0, node.path("result").size());
        assertTrue(node.path("success").asBoolean());
        assertEquals("NO_INVENTORY", node.path("outcome").asText());
    }

    /**
     * 「没问出来」才算失败，且必须保持 result=null——上游现有判读正是看这个。
     * 若改成空数组，上游会走「产品数=0」分支，把未能确认读成确实没有。
     */
    @Test
    void indeterminateKeepsNullResultAndFailFlags() throws Exception {
        JsonNode node = json(SpaController.toPriceResponse(PricingOutcome.INDETERMINATE, List.of()));

        assertTrue(node.path("result").isNull(), "result 必须仍为 null: " + node);
        assertFalse(node.path("success").asBoolean());
        assertEquals(-1, node.path("code").asInt());
        assertEquals("INDETERMINATE", node.path("outcome").asText());
    }

    /** 其余端点不填 outcome，响应形状必须一字不变 */
    @Test
    void otherEndpointsDoNotGrowAnOutcomeField() throws Exception {
        JsonNode node = json(ResponseDTO.success(
                CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.BOOKABLE).build()));

        assertFalse(node.has("outcome"),
                "信封上的 outcome 只服务查价；其余端点的分态在 result 里，不该多出一个空字段: " + node);
        assertEquals("BOOKABLE", node.path("result").path("outcome").asText(),
                "验价的分态仍在 result 内，位置不变");
    }
}
