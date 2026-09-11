package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.order;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.mapping.OrderQueryMapping;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderDetailResponse;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 以<b>生产实采的真实报文</b>驱动查单转换链，并与改动前的线上输出逐字段比对（同数据 A/B）。
 *
 * <p><b>为什么需要它</b>：查单刚从 REST DTO 切到领域模型，单测夹具若是手造的，
 * 就只证明"我写的转换符合我造的输入"。§4.2.6 要求夹具必须是供应商真实报文。
 *
 * <p>报文采集方式（2026-09-11）：在 trip-offline 生产机上向<b>改动前</b>的在产服务
 * 打 {@code POST /client/spa/order}，供应商 10010、我方单号取一个必然不存在的值，
 * 从容器日志取回艺龙原始响应。只读，不产生真单与费用。
 *
 * <p><b>基线</b>（同一次请求，改动前在产代码的对外输出）：
 * <pre>
 * {"presence":"NOT_FOUND","message":"供应商确认订单不存在(H001054)",
 *  "supplierOrderId":null,"supplierProductId":null,"totalPrice":null,"settlePrice":null,
 *  "createTime":null,"orderStatus":null,"supplierOrderStatus":null,"confirmationNumber":null}
 * </pre>
 * 本测试断言改动后逐字段与之相同——重构不许改变对外形状。
 */
class ElongOrderQueryRealPayloadTest {

    private static ElongOrderDetailResponse realAbsentOrder() throws Exception {
        String json = Files.readString(
                Path.of("src/test/resources/elong/order-detail-absent-real-20260911.json"));
        return JsonUtils.decodeJson(json, new TypeReference<ElongOrderDetailResponse>() {
        });
    }

    @Test
    @DisplayName("真实 H001054 报文：判 NOT_FOUND，且对外形状与改动前逐字段一致")
    void realAbsentOrderMatchesTheProductionBaseline() throws Exception {
        OrderQueryResult result =
                new ElongOrderQuerySyncServiceImpl().orderQueryRespConvert(realAbsentOrder());

        assertEquals(OrderPresence.NOT_FOUND, result.presence(),
                "H001054 是艺龙官方的「订单不存在」，这是唯一允许上游重新下单的一态");

        OrderRespDTO dto = OrderQueryMapping.toDto(result);
        assertEquals(OrderPresence.NOT_FOUND, dto.getPresence());
        assertEquals("供应商确认订单不存在(H001054)", dto.getMessage());
        assertNull(dto.getSupplierOrderId());
        assertNull(dto.getSupplierProductId());
        assertNull(dto.getTotalPrice());
        assertNull(dto.getSettlePrice());
        assertNull(dto.getCreateTime());
        assertNull(dto.getOrderStatus(), "没查到就没有状态可报，不得取默认值");
        assertNull(dto.getSupplierOrderStatus());
        assertNull(dto.getConfirmationNumber());
    }
}
