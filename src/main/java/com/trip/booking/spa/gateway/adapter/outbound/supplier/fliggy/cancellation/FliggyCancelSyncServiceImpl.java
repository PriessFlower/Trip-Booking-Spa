package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.cancellation.client.CancelOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyCancelResponse;
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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞猪取消。官方明示我方单号（{@code dis_order_id}）足以定位（B5），不要求飞猪单号。
 *
 * <p>罚金：{@code forfeit_fee} 是结构化字段（penaltySource=FIELD），但<b>官方未标币种</b>
 * ——按 cursor 生产实证以 USD 计（快照必测清单第 2 项，沙箱确证前这是唯一依据）。
 * 取不到罚金回 {@link CancelPenalty#unknown()}，不猜 0 元。
 */
@Slf4j
@Service("fliggyCancelSyncService")
public class FliggyCancelSyncServiceImpl extends AbstractCancelSyncSupportService {

    private static final String METHOD_CANCEL = "taobao.xhotel.trade.international.distribution.cancel";

    /** forfeit_fee 的币种：官方未标，cursor 实证 USD——沙箱确证后若不符须同步改 */
    private static final String FORFEIT_CURRENCY = "USD";

    @Resource
    private FliggyProperties properties;

    @Override
    protected CancelResult doCancel(CancelCommand command) {
        if (!properties.isConfigured()) {
            return CancelResult.failed(command.orderId(), null, "credentials_missing",
                    "飞猪凭证未配置，供应商侧未发生任何动作");
        }
        Map<String, Object> baseReq = new LinkedHashMap<>();
        baseReq.put("dis_order_id", command.orderId());
        baseReq.put("distributor", properties.getDistributor());
        ResponseResult<FliggyCancelResponse> result = new CancelOrderAccess(properties)
                .access(new FliggyTopCall(METHOD_CANCEL,
                        Map.of("order_base_req", JsonUtils.writeObject2Json(baseReq))), CallPurpose.ORDER);
        FliggyCancelResponse resp = result == null ? null : result.getData();
        if (resp == null) {
            return CancelResult.unknown(command.orderId(), null, null,
                    "取消未取得结果，可能已生效——请查单确证后再决定是否重试");
        }
        if (resp.isPlatformError()) {
            if (resp.isCredentialFailure()) {
                // 平台拒绝=请求未达业务,确定没取消;凭据病标 AUTH_CONFIG,模板负责告警与埋点
                return CancelResult.failed(command.orderId(), null, resp.metricErrorCode(),
                        "我方凭据/配置病，请求未被受理").withFailureKind(FailureKind.AUTH_CONFIG);
            }
            if (resp.isPlatformThrottled()) {
                // 频控重试会变，不符合「确定失败」语义——引导稍后重试并查单
                return CancelResult.unknown(command.orderId(), null, resp.metricErrorCode(),
                        "平台频控，请稍后重试并查单确证");
            }
            return CancelResult.failed(command.orderId(), null, resp.metricErrorCode(),
                    "供应商平台拒绝了请求：" + resp.platformError());
        }
        Boolean cancelSuccess = resp.cancelSuccess();
        if (resp.isSucc() && Boolean.TRUE.equals(cancelSuccess)) {
            Integer fee = resp.forfeitFee();
            CancelPenalty penalty = fee == null ? CancelPenalty.unknown()
                    : CancelPenalty.fromField(Money.ofCents(fee, FORFEIT_CURRENCY));
            return CancelResult.success(command.orderId(), null, penalty, "取消成功");
        }
        if (resp.isSucc() && Boolean.FALSE.equals(cancelSuccess)) {
            return CancelResult.failed(command.orderId(), null, resp.bizErrorCode(),
                    "供应商拒绝取消该订单");
        }
        // 业务层失败或 cancel_success 缺席：码义未核实，取消可能已生效——不确定
        log.warn("飞猪取消：结果不明,orderId={},bizErrorCode={}", command.orderId(), resp.bizErrorCode());
        return CancelResult.unknown(command.orderId(), null, resp.metricErrorCode(),
                "供应商未确认取消结果，请查单确证");
    }
}
