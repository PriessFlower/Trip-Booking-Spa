package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.mapping.OrderQueryMapping;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyOrderDetailResponse;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 以真实报文驱动飞猪查单转换链（§4.2.6：夹具必须是供应商真实报文）。
 *
 * <p>用的是既有夹具 {@code fliggy/order-detail-real-20260909.json}——2026-09-09 首单闭环
 * 那笔真单的详情响应。查单能力刚切到领域模型，这条守的是"真实报文经新链路后形状不变"。
 *
 * <p><b>飞猪永不判 NOT_FOUND</b>：「订单不存在」的确定性码未实证前，把没查出来说成没有
 * 会招重复下单，故未成功一律 INDETERMINATE。这条纪律本次未改动，一并钉住。
 */
class FliggyOrderQueryRealPayloadTest {

    private static FliggyOrderDetailResponse realOrder() throws Exception {
        return FliggyOrderDetailResponse.parse(Files.readString(
                Path.of("src/test/resources/fliggy/order-detail-real-20260909.json")));
    }

    @Test
    @DisplayName("真实成功报文：判 FOUND，状态原文进 message，不做任何状态翻译")
    void realOrderIsFoundAndUntranslated() throws Exception {
        OrderQueryResult result =
                new FliggyOrderQuerySyncServiceImpl().orderQueryRespConvert(realOrder());

        assertEquals(OrderPresence.FOUND, result.presence());
        assertNotNull(result.message(), "现阶段只回报「在」与供应商原文");
        assertNull(result.state(),
                "order_status 各态真实报文未见，状态映射待实测补齐——识别不出的绝不猜");

        OrderRespDTO dto = OrderQueryMapping.toDto(result);
        assertEquals(OrderPresence.FOUND, dto.getPresence());
        assertNull(dto.getOrderStatus(), "没有语义状态就没有对外码，不得取默认值");
    }
}
