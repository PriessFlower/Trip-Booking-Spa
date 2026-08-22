package com.trip.booking.spa.gateway.application.cancellation;

import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单取消模板。
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
