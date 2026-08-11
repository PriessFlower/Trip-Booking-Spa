package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.common.enums.CheckPriceOutcome;
import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 验价模板。
 *
 * <p><b>本类的核心职责是「绝不把不知道说成不可订」</b>。原实现把一切异常与空响应统一吞成
 * null，控制层再转成一条接口错误，上游只能笼统理解为「验不过」——于是超时、限流、
 * 供应商故障、产品下架、真满房全都长成同一个样子，而这几种情形的正确处置互不相同。
 *
 * <p>故本类的兜底一律回报 {@link CheckPriceOutcome#INDETERMINATE}。判
 * {@link CheckPriceOutcome#SOLD_OUT} 与 {@link CheckPriceOutcome#RATE_DEAD} 的权力
 * 只交给各供应商实现——只有它能读懂供应商是在说「没房了」还是在说「你要的这份报价没了」。
 *
 * @see AbstractBookingSyncSupportService 下单侧的同类纪律
 * @see AbstractOrderQuerySyncSupportService 查单侧的同类纪律
 */
@Slf4j
public abstract class AbstractCheckPriceSyncSupportService<T> implements CheckPriceSyncService {

    @Override
    public CheckPriceRespDTO checkPrice(CheckPriceReq checkPriceReq) {
        try {
            T t = doCheckPrice(checkPriceReq);

            log.info("CheckPriceSyncService checkPriceReq : {} checkResp : {}",
                    JsonUtils.writeObject2Json(checkPriceReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                log.error("CheckPriceSyncService doCheckPrice 无响应，回报 INDETERMINATE, sProductId={}",
                        checkPriceReq.getSProductId());
                return indeterminate("验价无响应，未能确认该产品是否可订，请稍后重试");
            }

            CheckPriceRespDTO respDTO = checkPriceRespConvert(t);

            if (respDTO == null) {
                log.error("CheckPriceSyncService checkPriceRespConvert 返回空，回报 INDETERMINATE, 原始响应={}",
                        JsonUtils.writeObject2Json(t));
                return indeterminate("验价响应无法解析，未能确认该产品是否可订，请稍后重试");
            }
            if (respDTO.getOutcome() == null) {
                // 实现方漏填分态即视为不确定，避免默认值悄悄退化成「可订」或「满房」
                log.error("CheckPriceSyncService 实现未填 outcome，按 INDETERMINATE 处理, sProductId={}",
                        checkPriceReq.getSProductId());
                respDTO.setOutcome(CheckPriceOutcome.INDETERMINATE);
            }
            return respDTO;
        } catch (Exception e) {
            log.error("CheckPriceSyncService 异常，回报 INDETERMINATE, sProductId={}",
                    checkPriceReq.getSProductId(), e);
            return indeterminate("验价过程异常，未能确认该产品是否可订，请稍后重试："
                    + e.getClass().getSimpleName());
        }
    }

    private CheckPriceRespDTO indeterminate(String message) {
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.INDETERMINATE)
                .message(message)
                .build();
    }

    public abstract T doCheckPrice(CheckPriceReq checkPriceReq);

    public abstract CheckPriceRespDTO checkPriceRespConvert(T t);


}
