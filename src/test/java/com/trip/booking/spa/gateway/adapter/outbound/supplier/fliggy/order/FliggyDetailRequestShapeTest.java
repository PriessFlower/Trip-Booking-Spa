package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住查单入参形态：业务参数只有一个 {@code order_base_req}，值是 JSON 串。
 *
 * <p>此前 SPA 把 {@code dis_order_id}/{@code distributor} 平铺发送——代码注释自认「未确认，
 * 错了首测就改」，而它从没被真打过。〔实证 2026-09-09〕平铺真打回
 * {@code code 40 Missing required arguments:order_base_req}，即这条查单链路从未通过；
 * 包起来后同一笔单正常返回。官方 §5 标 {@code order_base_req} 为 Object，cursor 生产报文同形。
 */
class FliggyDetailRequestShapeTest {

    @Test
    @DisplayName("查单入参包在 order_base_req 里，不平铺")
    void detailCallWrapsParamsInOrderBaseReq() {
        FliggyTopCall call = QueryOrderAccess.callByOrderId("260909173834659dd355e09a", "DIS_TRIPBOOKING_1st");

        assertEquals("taobao.xhotel.order.international.distribution.detail", call.getMethod());
        Map<String, String> biz = call.getBizParams();
        assertEquals(1, biz.size(), "平铺发送会多出 dis_order_id/distributor 两个顶层参数");
        String baseReq = biz.get("order_base_req");
        assertTrue(baseReq.contains("\"dis_order_id\":\"260909173834659dd355e09a\""), baseReq);
        assertTrue(baseReq.contains("\"distributor\":\"DIS_TRIPBOOKING_1st\""), baseReq);
    }
}
