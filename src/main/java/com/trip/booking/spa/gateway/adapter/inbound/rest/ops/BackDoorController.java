package com.trip.booking.spa.gateway.adapter.inbound.rest.ops;


import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaCPSQueryPriceService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaStaticDataIngestionService;
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
    private ExpediaStaticDataIngestionService expediaStaticDataIngestionService;
    @Resource
    private com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.catalog.ExpediaCatalogTransformService expediaCatalogTransformService;
    @Resource
    private com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaCatalogSeedService expediaCatalogSeedService;
    @Resource
    private com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaGeographyIngestionService expediaGeographyIngestionService;
    @Resource
    private com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaProductMappingService expediaProductMappingService;

    @GetMapping("/expedia/static/ingest")
    @ApiOperation("Expedia静态数据摄取-按酒店ID逗号分隔")
    public HttpResponse expediaStaticIngest(@RequestParam("propertyIds") String propertyIds,
                                            @RequestParam(value = "language", required = false) String language) {
        List<String> ids = Arrays.asList(propertyIds.split(","));
        int count = expediaStaticDataIngestionService.ingestByPropertyIds(ids, language);
        return HttpResponse.getSuccessInstance(count);
    }

    @GetMapping("/expedia/catalog/transform")
    @ApiOperation("Expedia快照加工进打底目录-按酒店ID逗号分隔")
    public HttpResponse expediaCatalogTransform(@RequestParam("propertyIds") String propertyIds) {
        List<String> ids = Arrays.asList(propertyIds.split(","));
        int count = expediaCatalogTransformService.transformByPropertyIds(ids);
        return HttpResponse.getSuccessInstance(count);
    }

    @GetMapping("/expedia/catalog/seed")
    @ApiOperation("Expedia播种：catalog清单→(可选)摄取→(可选)加工；download/updateDays/startLine 对齐旧链路运维语义")
    public HttpResponse expediaCatalogSeed(@RequestParam(value = "countryCodes", required = false) String countryCodes,
                                           @RequestParam(value = "limit", defaultValue = "0") int limit,
                                           @RequestParam(value = "ingest", defaultValue = "false") boolean ingest,
                                           @RequestParam(value = "transform", defaultValue = "false") boolean transform,
                                           @RequestParam(value = "download", defaultValue = "true") boolean download,
                                           @RequestParam(value = "updateDays", required = false) Integer updateDays,
                                           @RequestParam(value = "startLine", defaultValue = "0") int startLine) {
        java.util.Set<String> countries = org.apache.commons.lang3.StringUtils.isBlank(countryCodes)
                ? java.util.Set.of()
                : java.util.Set.copyOf(Arrays.asList(countryCodes.toUpperCase().split(",")));
        return HttpResponse.getSuccessInstance(expediaCatalogSeedService.seed(
                countries, limit, ingest, transform, download, updateDays, startLine));
    }

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

    @GetMapping("/expedia/catalog/deactivate")
    @ApiOperation("Expedia下线酒店清理（原/delete/expedia/hotel）；since空=7天前")
    public HttpResponse expediaCatalogDeactivate(@RequestParam(value = "since", required = false) String since) {
        List<String> ids = expediaStaticDataIngestionService.fetchAndMarkInactive(since);
        int deactivated = expediaCatalogTransformService.deactivateHotels(ids);
        return HttpResponse.getSuccessInstance(ids.size() + "/" + deactivated);
    }

    @GetMapping("/expedia/geo/countries")
    @ApiOperation("Expedia国家建档（原saveCountryInfo）；continents可选，如 ASIA,ANTARCTICA")
    public HttpResponse expediaGeoCountries(@RequestParam(value = "continents", required = false) String continents) {
        java.util.Set<String> filter = org.apache.commons.lang3.StringUtils.isBlank(continents)
                ? java.util.Set.of()
                : java.util.Set.copyOf(Arrays.asList(continents.toUpperCase().split(",")));
        return HttpResponse.getSuccessInstance(expediaGeographyIngestionService.syncCountries(filter));
    }

    @GetMapping("/expedia/geo/cities")
    @ApiOperation("Expedia城市建档（原saveCityInfo）；countryIds可选(region id逗号分隔)，空=全球（慎用）")
    public HttpResponse expediaGeoCities(@RequestParam(value = "countryIds", required = false) String countryIds) {
        List<String> ids = org.apache.commons.lang3.StringUtils.isBlank(countryIds)
                ? List.of()
                : Arrays.asList(countryIds.split(","));
        return HttpResponse.getSuccessInstance(expediaGeographyIngestionService.syncCities(ids));
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
