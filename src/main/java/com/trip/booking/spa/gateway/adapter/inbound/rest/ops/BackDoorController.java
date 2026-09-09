package com.trip.booking.spa.gateway.adapter.inbound.rest.ops;


import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaCPSQueryPriceService;
import com.trip.booking.spa.gateway.adapter.inbound.rest.common.HttpResponse;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;


/**
 * 运维后门端点（网关侧）。§3.8.9 的绕闸例外统一落在本包，便于审计。
 * 旧供应商的后门已拆至 legacy/ops/LegacyBackDoorController（URL 不变），随迁移逐个消亡。
 */
@Slf4j
@RestController
@RequestMapping("/hotel")
public class BackDoorController {
    @Resource
    private ExpediaCPSQueryPriceService expediaCPSQueryPriceService;
    @Resource
    private com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaProductMappingService expediaProductMappingService;

    @GetMapping("/expedia/catalog/products")
    @ApiOperation("Expedia产品映射建档（原saveOrUpdateProductInfo）；propertyIds空=分页全量；"
            + "occupancy空=2（须与真实流量一致，否则 productKey 不相等、目录取不到）")
    public HttpResponse expediaCatalogProducts(@RequestParam(value = "propertyIds", required = false) String propertyIds,
                                               @RequestParam(value = "checkIn", required = false) String checkIn,
                                               @RequestParam(value = "checkOut", required = false) String checkOut,
                                               @RequestParam(value = "startNum", required = false) Integer startNum,
                                               @RequestParam(value = "occupancy", required = false) String occupancy) {
        List<String> ids = org.apache.commons.lang3.StringUtils.isBlank(propertyIds)
                ? List.of()
                : Arrays.asList(propertyIds.split(","));
        return HttpResponse.getSuccessInstance(
                expediaProductMappingService.syncProducts(checkIn, checkOut, ids, startNum, occupancy));
    }

    /**
     * 手动触发一轮 Expedia 刷价。速率、批量与互斥锁由 Service 统一解析，与定时调度一致。
     *
     * <p>本入口不受 task.expedia-cps.enabled 约束：定时关闭期间手动补刷是正当运维场景。
     * 该绕过以 warn 日志留痕（PROJECT.md §3.8.4）。
     */
    @GetMapping(value = "/expedia/priceCache")
    @ApiOperation("expedia价格缓存-手动触发一轮")
    public HttpResponse expediaPriceCache() {
        log.warn("[gate] 绕过 task.expedia-cps.enabled：BackDoor 手动触发一轮刷价");
        expediaCPSQueryPriceService.queryPriceQueueTask("manual");
        return HttpResponse.getSuccessInstance();
    }
}
