package com.trip.booking.spa.gateway.application.cancellation;

import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 取消能力的判定纪律模板。
 *
 * <p>取消请求同样可能「已发出但响应丢失」——超时、连接中断、5xx。此时供应商侧可能已取消、
 * 也可能未取消，而两个方向的误判都是资损：判失败而实际已取消，上游继续持有一个已不存在的
 * 订单，旅客到店无房；判成功而实际未取消，上游据此退款放行，房仍占着、费用仍在产生。
 *
 * <p>故本类的兜底一律回报 {@link CancelOutcome#UNKNOWN}：无法证明取消请求未在供应商侧
 * 生效时，就不能替上游作出判断。判 {@link CancelOutcome#FAILED} 的权力只交给各供应商
 * 实现——只有它能识别「订单不存在」「已过取消期限」这类确证不会因重试而改变的结果。
 *
 * <p>另注：本类捕获一切异常并判 UNKNOWN，其中包含本地限流拒绝这类「请求根本没发出去」的
 * 情形。严格说那属于「重试即可成功」，与 UNKNOWN 的语义不完全吻合，但判 UNKNOWN 是安全的
 * ——上游至多白查一次单，而反过来会造成资损。
 *
 * @see AbstractBookingSyncSupportService 下单侧的同构模板，纪律一致
 */
@Slf4j
public abstract class AbstractCancelSyncSupportService<T> implements CancelSyncService {

    @Override
    public CancelRespDTO cancel(CancelReq cancelReq) {
        try {
            T t = doCancel(cancelReq);

            log.info("CancelSyncService cancelReq : {}, cancelResp:{}", JsonUtils.writeObject2Json(cancelReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                log.error("CancelSyncService doCancel 无响应，回报 UNKNOWN, orderId={}", cancelReq.getOrderId());
                return unknown(cancelReq, "供应商无响应，取消结果不确定，请查单确证");
            }

            CancelRespDTO cancelRespDTO = cancelRespConvert(t);

            if (cancelRespDTO == null) {
                log.error("CancelSyncService cancelRespConvert 返回空，回报 UNKNOWN, orderId={}, 原始响应={}",
                        cancelReq.getOrderId(), JsonUtils.writeObject2Json(t));
                return unknown(cancelReq, "供应商响应无法解析，取消结果不确定，请查单确证");
            }

            if (cancelRespDTO.getOutcome() == null) {
                log.error("CancelSyncService 实现未填 outcome，按 UNKNOWN 处理, orderId={}", cancelReq.getOrderId());
                cancelRespDTO.setOutcome(CancelOutcome.UNKNOWN);
            }
            if (cancelRespDTO.getOrderId() == null) {
                cancelRespDTO.setOrderId(cancelReq.getOrderId());
            }

            return cancelRespDTO;

        } catch (Exception e) {
            log.error("CancelSyncService 异常，回报 UNKNOWN, orderId={}", cancelReq.getOrderId(), e);
            return unknown(cancelReq, "取消过程异常，结果不确定，请查单确证：" + e.getClass().getSimpleName());
        }
    }

    private CancelRespDTO unknown(CancelReq cancelReq, String message) {
        return CancelRespDTO.builder()
                .outcome(CancelOutcome.UNKNOWN)
                .orderId(cancelReq.getOrderId())
                .sOrderId(cancelReq.getSupplierOrderId())
                .message(message)
                .build();
    }

    public abstract T doCancel(CancelReq cancelReq);

    public abstract CancelRespDTO cancelRespConvert(T t);

}
