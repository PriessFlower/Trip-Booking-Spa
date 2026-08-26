package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty.PenaltySource;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.gateway.domain.supplier.FailureKind;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 钉住艺龙取消的 AUTH_CONFIG 判定：凭证未配置属我方配置病，不是供应商业务性拒绝。
 *
 * <p>此前该分支只回 FAILED + credentials_missing——态是对的（供应商侧确未发生任何动作），
 * 但成因无处表达，上游会把"我们的钥匙坏了"归因供应商。cursor 的飞猪 session 病正因
 * 无处表达被当"集成死"晾了两个月（FailureKind 类注释），这是本档在 SPA 的第一个消费者。
 *
 * <p>经公开入口 cancel() 走，顺带穿过②模板的告警分支（Monitor 空服务时安全跳过）。
 */
class ElongCancelAuthConfigTest {

    @Test
    void missingCredentialsIsAuthConfigNotSupplierFault() {
        ElongProperties properties = mock(ElongProperties.class);
        when(properties.isConfigured()).thenReturn(false);
        ElongCancelSyncServiceImpl service = new ElongCancelSyncServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);

        CancelResult result = service.cancel(CancelCommand.of(10010, "TB-AUTH-1", null));

        assertEquals(CancelOutcome.FAILED, result.outcome(), "供应商侧确未发生动作，态仍是确定失败");
        assertEquals(FailureKind.AUTH_CONFIG, result.failureKind(), "成因必须标我方配置病");
        assertEquals("credentials_missing", result.supplierErrorCode());
        assertEquals(PenaltySource.NONE, result.penalty().source());
    }
}
