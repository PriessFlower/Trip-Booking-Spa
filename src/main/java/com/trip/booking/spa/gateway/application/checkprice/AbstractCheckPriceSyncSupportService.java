package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 验价模板。
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
            if (respDTO.getOutcome() == CheckPriceOutcome.BOOKABLE) {
                // 说可订就必须给得出能下单的凭据：缺句柄上游无从下单，缺剩余时效上游无从判断
                // 该直接下单还是先重新验价。收在模板而非各家实现里——现网两家本就必带，
                // 这道关卡是给下一家的：漏了不会报错，只会让上游拿一个空凭据去建单
                if (StringUtils.isBlank(respDTO.getOfferId())) {
                    log.error("CheckPriceSyncService 称可订却无报价句柄，按 INDETERMINATE 处理, sProductId={}",
                            checkPriceReq.getSProductId());
                    return indeterminate("验价结果自相矛盾（称可订却无报价句柄），未能确认该产品是否可订");
                }
                if (respDTO.getOfferTtlSeconds() == null || respDTO.getOfferTtlSeconds() <= 0) {
                    log.error("CheckPriceSyncService 称可订却无句柄时效，按 INDETERMINATE 处理, sProductId={},offerId={}",
                            checkPriceReq.getSProductId(), respDTO.getOfferId());
                    return indeterminate("验价结果自相矛盾（报价句柄无有效时效），未能确认该产品是否可订");
                }
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
