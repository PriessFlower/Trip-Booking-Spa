package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongDataValidateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongOrderCreateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongRequestEnvelope;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉死申报总价发到线上的<b>键名</b>必须是艺龙的 {@code TotalPrice}。
 *
 * <p><b>为什么需要这个测试</b>：Java 字段名已从 {@code totalPrice} 改为
 * {@code declaredTotal}（口径是「我方申报给艺龙的金额」，不是中性的「总价」——结算按它走，
 * 见 {@code ElongNightlyRate} 类 javadoc）。两个请求类都带
 * {@code @JsonNaming(UpperCamelCaseStrategy)}，若无 {@code @JsonProperty} 钉住，改名会把
 * 键名一并变成 {@code DeclaredTotal}——艺龙收不到总价，验价与下单<b>静默失败</b>，
 * 而改名本身在编译期毫无痕迹。
 *
 * <p>用生产的 {@link JsonUtils} 序列化而非裸 {@code ObjectMapper}：验证环境与生产不一致
 * 等于没验证（教训见 commit 8e30eee 正文）。
 */
class ElongDeclaredTotalWireNameTest {

    @Test
    @DisplayName("验价请求的申报总价键名是 TotalPrice，且不出现 Java 字段名")
    void validateRequestKeepsWireName() {
        ElongDataValidateRequest request = ElongDataValidateRequest.builder()
                .hotelId("61497910")
                .declaredTotal(new BigDecimal("193.40"))
                .build();

        String json = JsonUtils.writeObject2Json(new ElongRequestEnvelope("1.62", request));

        assertThat(json).contains("\"TotalPrice\":193.40");
        assertThat(json).doesNotContain("DeclaredTotal").doesNotContain("declaredTotal");
    }

    @Test
    @DisplayName("下单请求的申报总价键名是 TotalPrice，且不出现 Java 字段名")
    void createOrderRequestKeepsWireName() {
        ElongOrderCreateRequest request = ElongOrderCreateRequest.builder()
                .hotelId("61497910")
                .declaredTotal(new BigDecimal("193.40"))
                .build();

        String json = JsonUtils.writeObject2Json(new ElongRequestEnvelope("1.62", request));

        assertThat(json).contains("\"TotalPrice\":193.40");
        assertThat(json).doesNotContain("DeclaredTotal").doesNotContain("declaredTotal");
    }
}
