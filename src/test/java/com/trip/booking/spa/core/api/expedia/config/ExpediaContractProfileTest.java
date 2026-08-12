package com.trip.booking.spa.core.api.expedia.config;

import com.trip.booking.spa.core.api.expedia.bean.request.QueryPriceRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住「车道参数必须成组同行」。
 *
 * <p>四项参数各自都是合法字符串，混用时 Expedia 不报错，只是返回另一条车道的报价——
 * 这类错配无法由运行期观察发现，故必须在启动期拦下。本测试即为该护栏的回归。
 */
class ExpediaContractProfileTest {

    private static final String B2B_POS = "B2B_SA_PKG_MOD_AGENT";
    private static final String B2C_POS = "B2C_SA_MOD_XSELL_APP";

    /** PDF p6 的 B2B 一列，整组取用应通过 */
    @Test
    void acceptsB2bProfileAsAWhole() {
        assertDoesNotThrow(() -> profile(B2B_POS, "EAC", "2", "agent_tool").afterPropertiesSet());
    }

    /** PDF p6 的 B2C 一列，整组取用应通过 */
    @Test
    void acceptsB2cProfileAsAWhole() {
        assertDoesNotThrow(() -> profile(B2C_POS, "EAC", "1", "mobile_app").afterPropertiesSet());
    }

    /**
     * 本次改动要防的正是这一种：配置切到 B2C，而 sales_channel 仍停在 B2B 值。
     * 改动前 sales_channel 硬编码在通道层，这个组合必然发生且无声。
     */
    @Test
    void rejectsB2cPointOfSaleCarryingB2bSalesChannel() {
        ExpediaContractProfile mixed = profile(B2C_POS, "EAC", "1", "agent_tool");

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, mixed::afterPropertiesSet);

        assertAll(
                () -> assertTrue(failure.getMessage().contains("agent_tool"),
                        "异常应回报实际收到的取值，否则运维无从判断错在哪一项"),
                () -> assertTrue(failure.getMessage().contains("mobile_app"),
                        "异常应给出该车道的期望取值"));
    }

    /** 反向：车道整体是 B2B，但 payment_terms 落在 B2C 的 1 上 */
    @Test
    void rejectsB2bProfileCarryingB2cPaymentTerms() {
        assertThrows(IllegalStateException.class,
                profile(B2B_POS, "EAC", "1", "agent_tool")::afterPropertiesSet);
    }

    /** 缺项同样不成车道：漏配 sales_channel 时不得当作「沿用旧行为」放行 */
    @Test
    void rejectsMissingSalesChannel() {
        assertThrows(IllegalStateException.class,
                profile(B2B_POS, "EAC", "2", null)::afterPropertiesSet);
    }

    /**
     * 查价起手式必须一次写全四项车道参数。
     * 改动前这四项分散在五个构造点，任一处漏填即与其余请求走上不同车道。
     */
    @Test
    void requestBuilderStampsTheWholeLane() {
        QueryPriceRequest request = profile(B2B_POS, "EAC", "2", "agent_tool")
                .newRequestBuilder()
                .property_id("12345")
                .build();

        assertAll(
                () -> assertEquals(B2B_POS, request.getPartner_point_of_sale()),
                () -> assertEquals("EAC", request.getBilling_terms()),
                () -> assertEquals("2", request.getPayment_terms()),
                () -> assertEquals("agent_tool", request.getSales_channel()),
                () -> assertEquals("250", request.getRate_plan_count()));
    }

    /** 换一条车道，起手式应整组跟着换，不得有任何一项留在原车道 */
    @Test
    void requestBuilderSwitchesEveryParameterTogether() {
        QueryPriceRequest request = profile(B2C_POS, "EAC", "1", "mobile_app")
                .newRequestBuilder()
                .property_id("12345")
                .build();

        assertAll(
                () -> assertEquals(B2C_POS, request.getPartner_point_of_sale()),
                () -> assertEquals("1", request.getPayment_terms()),
                () -> assertEquals("mobile_app", request.getSales_channel()));
    }

    /**
     * 验价链接补参数与查价起手式同源。
     * 此处不含 sales_channel 是有意为之：验价自接入起就不带该参数且实测通行，
     * 补发属未经验证的行为变更，不与本次收口混做。
     */
    @Test
    void appendsContractTermsToPriceCheckHref() {
        String href = profile(B2B_POS, "EAC", "2", "agent_tool")
                .appendTo("/v3/properties/1/availability?token=abc");

        assertAll(
                () -> assertTrue(href.contains("&billing_terms=EAC")),
                () -> assertTrue(href.contains("&payment_terms=2")),
                () -> assertTrue(href.contains("&partner_point_of_sale=" + B2B_POS)),
                () -> assertTrue(href.startsWith("/v3/properties/1/availability?token=abc")));
    }

    /** 链接为空时原样返回，不得拼出一个只有参数的畸形串 */
    @Test
    void leavesBlankPriceCheckHrefUntouched() {
        assertEquals("", profile(B2B_POS, "EAC", "2", "agent_tool").appendTo(""));
    }

    private ExpediaContractProfile profile(String pointOfSale, String billingTerms,
                                           String paymentTerms, String salesChannel) {
        return new ExpediaContractProfile(pointOfSale, billingTerms, paymentTerms, salesChannel,
                new ExpediaRapidProperties());
    }
}
