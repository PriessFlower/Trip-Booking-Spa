package com.trip.booking.spa.gateway.application.cancellation;

import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;

/**
 * 取消能力。入参出参是领域模型，不是对外 JSON——五个能力接口此前直接吃 REST DTO，
 * ②③被①的 JSON 契约绑架（依赖方向倒挂），取消是第一个矫正的能力面。
 * 对外形状的翻译收在 ① 的 CancelMapping。
 */
public interface CancelSyncService {

    CancelResult cancel(CancelCommand command);
}
