package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验价退改解析的判据钉死。样本取自 2026-08-17 生产真实响应
 * （酒店 18984520，住 08-22，经白名单机中继抓取）。
 */
class ElongValidatedCancelPolicyTest {

    private final ElongProductKeyDeriver deriver = new ElongProductKeyDeriver();

    /** 生产真实报文：08-21 18:00 前免费，之后收全额 42.07 元 */
    private static final String REAL_SAMPLE = """
            [{"Penalty":0.0,"PenaltyRMB":0.0,"DateFrom":"1970-01-01T00:00:00+08:00",
              "DateTo":"2026-08-21T18:00:00+08:00"},
             {"Penalty":42.07,"PenaltyRMB":42.07,"DateFrom":"2026-08-21T18:00:00+08:00",
              "DateTo":"2099-12-31T00:00:00+08:00"}]
            """;

    @Test
    void parsesRealProductionSample() {
        List<CancelPolicy> policies = deriver.convertValidatedCancelPolicy("2026-08-22", node(REAL_SAMPLE));
        assertEquals(2, policies.size());

        // 首段免费：08-21 18:00 距入住日(08-22) 24:00 = 30 小时
        CancelPolicy free = policies.get(0);
        assertEquals(1, free.getCancelType());
        assertEquals(RefundType.NO_DEDUCTION, free.getType());
        assertEquals(30, free.getBefore());

        // 末段收费：金额取 PenaltyRMB；DateTo=2099 算出负值由下限 25 兜住
        CancelPolicy charged = policies.get(1);
        assertEquals(RefundType.DEDUCT_BY_AMOUNT, charged.getType());
        assertEquals(42.07, charged.getValue(), 0.001);
        assertEquals(25, charged.getBefore());
    }

    /** R-5.4：缺字段不许猜，整体作废而非漏掉一段——漏一段会把"要收费"说成"免费" */
    @Test
    void missingFieldVoidsWholeList() {
        assertTrue(deriver.convertValidatedCancelPolicy("2026-08-22",
                node("""
                        [{"PenaltyRMB":0.0,"DateTo":"2026-08-21T18:00:00+08:00"},
                         {"Penalty":42.07,"DateFrom":"2026-08-21T18:00:00+08:00"}]
                        """)).isEmpty());
        assertTrue(deriver.convertValidatedCancelPolicy("2026-08-22",
                node("[{\"PenaltyRMB\":0.0,\"DateTo\":\"not-a-time\"}]")).isEmpty());
    }

    @Test
    void emptyOrAbsentYieldsEmpty() {
        assertTrue(deriver.convertValidatedCancelPolicy("2026-08-22", null).isEmpty());
        assertTrue(deriver.convertValidatedCancelPolicy("2026-08-22", node("[]")).isEmpty());
    }

    /** 金额只认 PenaltyRMB：Penalty 是合约币种，当人民币用会算错罚金 */
    @Test
    void amountComesFromPenaltyRmbNotContractCurrency() {
        List<CancelPolicy> policies = deriver.convertValidatedCancelPolicy("2026-08-22",
                node("""
                        [{"Penalty":100.0,"PenaltyRMB":720.5,"DateFrom":"2026-08-20T18:00:00+08:00",
                          "DateTo":"2026-08-21T18:00:00+08:00"}]
                        """));
        assertEquals(720.5, policies.get(0).getValue(), 0.001);
    }

    /**
     * 结果必须与服务器时区无关。生产容器实际跑在 UTC 而艺龙按北京时间报时
     * （2026-08-17 实测），此前基准用服务器时区解析，同一份条款在不同机器上
     * 算出的 before 相差 8 小时——把"还能免费取消多久"说长，旅客据此在窗口外
     * 取消要挨罚金。
     */
    @Test
    void resultIsIndependentOfServerTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            int utc = deriver.convertValidatedCancelPolicy("2026-08-22", node(REAL_SAMPLE)).get(0).getBefore();
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            int sh = deriver.convertValidatedCancelPolicy("2026-08-22", node(REAL_SAMPLE)).get(0).getBefore();
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            int ny = deriver.convertValidatedCancelPolicy("2026-08-22", node(REAL_SAMPLE)).get(0).getBefore();
            assertEquals(30, utc);
            assertEquals(30, sh);
            assertEquals(30, ny);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static JsonNode node(String json) {
        return JsonUtils.readValue(json, JsonNode.class);
    }
}
