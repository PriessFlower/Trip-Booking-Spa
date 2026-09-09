package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.cancellation.client.CancelOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyOrderDetailResponse;
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
 * 飞猪取消：我方单号足以定位（B5）。<b>罚金以订单详情为准</b>——官方国际分销文档
 * （docs/fliggy/distribution-api.md §6）明示这一点，取消响应的字段表里也已没有罚金。
 * 故取消成功后再查一次单，按「房费总额 − 买家实退」得出罚金，币种取详情的
 * {@code currency_code}；任一环节取不到就回 unknown，不猜 0 元。
 */
@Slf4j
@Service("fliggyCancelSyncService")
public class FliggyCancelSyncServiceImpl extends AbstractCancelSyncSupportService {

    private static final String METHOD_CANCEL = "taobao.xhotel.trade.international.distribution.cancel";

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
            Integer offContractFee = resp.forfeitFee();
            if (offContractFee != null) {
                // 字段表已无此字段而报文仍带（2026-09-09 实证 -380，同单详情却是全额退）。
                // 只记不用：它变没变、跟详情合不合，靠这行日志看
                log.info("飞猪取消：响应仍带 forfeit_fee={}，不作罚金依据,orderId={}",
                        offContractFee, command.orderId());
            }
            return CancelResult.success(command.orderId(), null,
                    penaltyFromOrderDetail(command.orderId()), "取消成功");
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

    /** 取消已成功，再查单取罚金。查不到、字段缺、数对不上一律 unknown——罚金宁可不知，不可猜 */
    private CancelPenalty penaltyFromOrderDetail(String orderId) {
        ResponseResult<FliggyOrderDetailResponse> result = new QueryOrderAccess(properties)
                .access(QueryOrderAccess.callByOrderId(orderId, properties.getDistributor()), CallPurpose.ORDER);
        FliggyOrderDetailResponse detail = result == null ? null : result.getData();
        if (detail == null || !detail.isSucc()) {
            log.warn("飞猪取消：已取消但查单未取得详情，罚金无从得知,orderId={}", orderId);
            return CancelPenalty.unknown();
        }
        return penaltyOf(detail, orderId);
    }

    /** 详情 → 罚金。{@link #penaltyFromOrderDetail} 的判定部分，单测直接喂真实报文 */
    static CancelPenalty penaltyOf(FliggyOrderDetailResponse detail, String orderId) {
        Integer total = detail.totalRoomPrice();
        Integer refund = detail.buyerRealRefund();
        String currency = detail.currencyCode();
        if (total == null || refund == null || currency == null || currency.isBlank()) {
            log.warn("飞猪取消：详情缺房费/实退/币种，罚金无从得知,orderId={},total={},refund={},currency={}",
                    orderId, total, refund, currency);
            return CancelPenalty.unknown();
        }
        if (refund <= 0) {
            // 「一分没退」既可能是罚全款，也可能是退款还没结算完（我们没有实证能分开这两种）。
            // 判成罚全款会让上游照单扣客人的钱，故只报不知道
            log.warn("飞猪取消：实退为 {}，分不清罚全款还是结算未完成,orderId={},total={}",
                    refund, orderId, total);
            return CancelPenalty.unknown();
        }
        if (refund > total) {
            log.warn("飞猪取消：实退大于房费，详情自相矛盾,orderId={},total={},refund={}",
                    orderId, total, refund);
            return CancelPenalty.unknown();
        }
        return CancelPenalty.fromField(Money.ofCents(total - refund, currency));
    }
}
