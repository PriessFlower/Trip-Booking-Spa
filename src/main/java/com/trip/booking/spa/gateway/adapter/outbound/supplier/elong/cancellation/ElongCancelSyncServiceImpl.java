package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking.ElongBookingClassifier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking.ElongBookingClassifier.CancelClassification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.cancellation.client.CancelOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongRestCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongOrderCancelRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongOrderDetailRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongRequestEnvelope;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderDetailResponse;
import com.trip.booking.spa.gateway.application.cancellation.AbstractCancelSyncSupportService;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.gateway.domain.shared.Money;
import com.trip.booking.spa.gateway.domain.supplier.FailureKind;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 艺龙取消。bean 名 {@code elongCancelSyncService} 供能力注册表发现——
 * 网关的<b>第一个取消实现</b>（此前五端点里 cancel 零实现）。
 *
 * <p>坐标是我方单号：供应商订单号缺失时先按 AffiliateConfirmationId 反查取回——
 * 最需要取消的场景恰是下单结果不确定时，那时上游没有供应商单号（CancelCommand javadoc）。
 *
 * <p>取消<b>刻意不设 booking-enabled 闸</b>：已存在的真单必须永远可撤，
 * 把"能不能撤单"和"能不能下单"绑在一个开关上，止血时会把善后通道一起关掉。
 *
 * <p>PenaltyAmount=0（不校验罚金，有罚金也取消）：取消由上游发起，罚金已在上游
 * 与旅客确认，网关只管执行；罚金以结构化字段随结果透出供对账（艺龙响应单位是元，
 * 出口一律换分并带币种 CNY——此前拼在中文 message 里、单位元，上游只能正则取）。
 */
@Slf4j
@Service("elongCancelSyncService")
public class ElongCancelSyncServiceImpl extends AbstractCancelSyncSupportService {

    /** 取消类型取官方枚举文案；渠道单无更细的取消原因来源 */
    private static final String CANCEL_CODE_DEFAULT = "行程变更";

    @Resource
    private ElongProperties properties;

    @Override
    protected CancelResult doCancel(CancelCommand command) {
        if (!properties.isConfigured()) {
            log.error("艺龙取消：凭证未配置,orderId={}", command.orderId());
            return CancelResult.failed(command.orderId(), null, "credentials_missing",
                            "艺龙凭证未配置，供应商侧未发生任何动作；修复配置前重试无效")
                    .withFailureKind(FailureKind.AUTH_CONFIG);
        }
        Long supplierOrderId = parseLongQuietly(command.supplierOrderId());
        if (supplierOrderId == null) {
            // 按我方单号反查供应商单号；确证无单即无可撤，确定失败
            ElongOrderDetailResponse detail = queryQuietly(command.orderId());
            if (detail != null && detail.isSucc() && detail.getResult() != null
                    && detail.getResult().getOrderId() != null) {
                supplierOrderId = detail.getResult().getOrderId();
                log.info("艺龙取消：已按我方单号反查到供应商单号,orderId={},sOrderId={}",
                        command.orderId(), supplierOrderId);
            } else if (detail != null && !detail.isSucc()
                    && StringUtils.trimToEmpty(detail.errorCode()).startsWith("H001054")) {
                log.info("艺龙取消：供应商确认订单不存在，无可取消,orderId={}", command.orderId());
                return CancelResult.failed(command.orderId(), null, "H001054",
                        "供应商确认该订单不存在，无可取消");
            } else {
                log.warn("艺龙取消：反查供应商单号未取得确定结果,orderId={}", command.orderId());
                return CancelResult.unknown(command.orderId(), null, null,
                        "无法确定供应商订单号，取消未发出，请稍后重试或先查单");
            }
        }

        ElongOrderCancelRequest cancelRequest = ElongOrderCancelRequest.builder()
                .orderId(supplierOrderId)
                .cancelCode(CANCEL_CODE_DEFAULT)
                .reason(CANCEL_CODE_DEFAULT)
                .penaltyAmount(BigDecimal.ZERO)
                .build();
        String dataJson = JsonUtils.writeObject2Json(
                new ElongRequestEnvelope(properties.getVersion(), cancelRequest));
        ResponseResult<ElongOrderCancelResponse> result = new CancelOrderAccess(properties)
                .access(new ElongRestCall("hotel.order.cancel", dataJson), CallPurpose.ORDER);

        ElongOrderCancelResponse data = result == null ? null : result.getData();
        CancelClassification classification = ElongBookingClassifier.classifyCancel(data);
        log.info("艺龙取消：分类结果,orderId={},sOrderId={},classification={},code={}",
                command.orderId(), supplierOrderId, classification, data == null ? null : data.getCode());

        String sOrderId = String.valueOf(supplierOrderId);
        switch (classification) {
            case SUCCESS:
                BigDecimal penaltyYuan = data.getResult() == null ? null : data.getResult().getPenaltyAmount();
                log.info("艺龙取消：已受理,orderId={},sOrderId={},罚金={}元",
                        command.orderId(), supplierOrderId, penaltyYuan);
                // 罚金字段缺失 ≠ 罚金为 0：如实申报"无从得知"，上游以订单详情 refundDetail 为准
                CancelPenalty penalty = penaltyYuan == null ? CancelPenalty.unknown()
                        : CancelPenalty.fromField(Money.fromYuan(penaltyYuan, "CNY"));
                return CancelResult.success(command.orderId(), sOrderId, penalty,
                        "取消已受理（退款以订单详情 refundDetail 为准）");
            case DETERMINISTIC_FAILURE:
                return CancelResult.failed(command.orderId(), sOrderId, data.errorCode(), data.getCode());
            case AUTH_CONFIG:
                // 请求被拒于门禁（如出口 IP 不在白名单），取消确未发生但病在我方——
                // 错误文案自带艺龙看到的 IP，原样透出便于修白名单
                return CancelResult.failed(command.orderId(), sOrderId, data.errorCode(),
                                "我方凭据/配置被艺龙拒绝，取消未发生；修复配置前重试无效：" + data.getCode())
                        .withFailureKind(FailureKind.AUTH_CONFIG);
            case INDETERMINATE:
            default:
                return CancelResult.unknown(command.orderId(), sOrderId,
                        data == null ? null : data.errorCode(),
                        "取消结果不确定，请查单确证");
        }
    }

    private ElongOrderDetailResponse queryQuietly(String orderId) {
        try {
            String dataJson = JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(),
                    ElongOrderDetailRequest.builder().orderId(0L).affiliateConfirmationId(orderId).build()));
            ResponseResult<ElongOrderDetailResponse> result = new QueryOrderAccess(properties)
                    .access(new ElongRestCall("hotel.order.detail", dataJson), CallPurpose.ORDER);
            return result == null ? null : result.getData();
        } catch (Exception e) {
            log.error("艺龙取消：反查异常,orderId={}", orderId, e);
            return null;
        }
    }

    private static Long parseLongQuietly(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
