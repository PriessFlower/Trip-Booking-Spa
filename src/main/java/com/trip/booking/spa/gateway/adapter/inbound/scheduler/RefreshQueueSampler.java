package com.trip.booking.spa.gateway.adapter.inbound.scheduler;

import com.trip.booking.spa.gateway.application.pricing.AbstractCPSQueryPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把每家刷价清单的覆盖面推成两个 gauge：清单里共多少家店、其中多少家当前有货
 * （F-2.6 的验收数字，口径见 docs/price-refresh.md）。
 *
 * <p><b>不看闸</b>：关着闸的家也采——「清单播了多少家、一家都没刷出货」正是要看见的状态。
 *
 * <p><b>十分钟一次</b>：一次采样＝六次全表 {@code COUNT DISTINCT}，而覆盖面按天变化，
 * 采得再密也没有新信息，只是多压一遍生产库。
 */
@Slf4j
@Component
public class RefreshQueueSampler {

    /** 六家刷价服务，按骨架类型注入——新接一家自动进来，本类不必改（§4.1.3） */
    private final List<AbstractCPSQueryPriceService<?>> refreshServices;

    public RefreshQueueSampler(List<AbstractCPSQueryPriceService<?>> refreshServices) {
        this.refreshServices = refreshServices;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    public void sample() {
        for (AbstractCPSQueryPriceService<?> service : refreshServices) {
            try {
                service.sampleQueueHotels();
            } catch (Exception e) {
                // 一家数不出来不许拖累其余五家：这是观测，不是做功
                log.warn("[refresh] 清单覆盖面采样失败：{}", service.getClass().getSimpleName(), e);
            }
        }
    }
}
