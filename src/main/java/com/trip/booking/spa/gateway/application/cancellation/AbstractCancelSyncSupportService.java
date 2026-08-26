package com.trip.booking.spa.gateway.application.cancellation;

import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.gateway.domain.supplier.FailureKind;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单取消模板。持有判定纪律：<b>无法证明取消未生效时一律回报 UNKNOWN，绝不回报失败</b>——
 * 两个方向误判都是资损（判失败而实际已取消 → 旅客到店无房；判成功而实际未取消 →
 * 上游退款放行而房仍占着）。
 *
 * <p>改造说明：此前是 {@code <T>} 两段式（doCancel 出原始响应 + convert 出 DTO），
 * 而两家实现的 doCancel 实际都已在内部编排完、返回自造的中间载体，convert 只剩
 * 载体→DTO 的搬运。领域模型 {@link CancelResult} 取代那些载体后两段塌成一段；
 * "实现忘了填 outcome" 的运行期检查也随之消失——CancelResult 只能经三态工厂构造。
 */
@Slf4j
public abstract class AbstractCancelSyncSupportService implements CancelSyncService {

    @Override
    public final CancelResult cancel(CancelCommand command) {
        try {
            CancelResult result = doCancel(command);

            if (result == null) {
                log.error("CancelSyncService doCancel 返回 null，回报 UNKNOWN, orderId={}", command.orderId());
                return unknown(command, "供应商无响应，取消结果不确定，请查单确证");
            }
            log.info("CancelSyncService orderId={}, outcome={}, penaltySource={}, message={}",
                    command.orderId(), result.outcome(), result.penalty().source(), result.message());

            if (result.failureKind() == FailureKind.AUTH_CONFIG) {
                alarmAuthConfig(command, result);
            }

            if (result.orderId() == null) {
                return result.withOrderId(command.orderId());
            }
            return result;

        } catch (Exception e) {
            log.error("CancelSyncService 异常，回报 UNKNOWN, orderId={}", command.orderId(), e);
            return unknown(command, "取消过程异常，结果不确定，请查单确证：" + e.getClass().getSimpleName());
        }
    }

    private CancelResult unknown(CancelCommand command, String message) {
        return CancelResult.unknown(command.orderId(), command.supplierOrderId(), null, message);
    }

    /**
     * 我方凭据/配置病的告警出口（FailureKind.AUTH_CONFIG 三条纪律之"必须告警到人"）。
     * 日志前缀 [auth-config] 是告警规则的匹配锚，指标 supplier_auth_config 任何非零都该有人看。
     * 判据在 ③（只有适配层读得懂供应商在说什么），告警形状收在 ②——与三态兜底同一分工。
     */
    private void alarmAuthConfig(CancelCommand command, CancelResult result) {
        SupplierSourceEnum supplier = SupplierSourceEnum.getEnum(command.supplierId());
        log.error("[auth-config] 我方凭据/配置病，供应商无辜、修复前重试无效，需人工处理: "
                        + "supplier={}, orderId={}, code={}, message={}",
                supplier == null ? command.supplierId() : supplier.name(),
                command.orderId(), result.supplierErrorCode(), result.message());
        if (supplier != null) {
            Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(supplier));
        }
    }

    /** 唯一允许理解供应商语义的钩子；判"确定"的权力只在这里（architecture.md §4.1） */
    protected abstract CancelResult doCancel(CancelCommand command);
}
