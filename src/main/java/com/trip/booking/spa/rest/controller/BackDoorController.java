package com.trip.booking.spa.rest.controller;


import com.trip.booking.spa.core.api.aichotels.service.AichotelsHotelService;
import com.trip.booking.spa.core.api.didatravel.service.DidatravelHotelService;
import com.trip.booking.spa.core.api.expedia.service.ExpediaCPSQueryPriceService;
import com.trip.booking.spa.core.api.expedia.staticdata.service.ExpediaStaticDataIngestionService;
import com.trip.booking.spa.core.api.fastpay.service.FastPayService;
import com.trip.booking.spa.core.api.huitravel.service.HuiTravelService;
import com.trip.booking.spa.core.api.meituan.service.MeituanStaticInfoService;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkCPSQueryPriceService;
import com.trip.booking.spa.core.api.ratehawk.service.RateHawkService;
import com.trip.booking.spa.core.api.travelconnect.service.TravelconnectHotelService;
import com.trip.booking.spa.rest.common.HttpResponse;
import com.google.common.util.concurrent.RateLimiter;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/hotel")
public class BackDoorController {
    @Autowired
    private TravelconnectHotelService travelconnectHotelService;
    @Autowired
    private AichotelsHotelService aichotelsHotelService;
    @Resource
    private DidatravelHotelService didatravelHotelService;
    @Autowired
    private HuiTravelService huiTravelService;
    @Resource
    private FastPayService fastPayStaticInfoService;
    @Resource
    private RateHawkService rateHawkService;
    @Resource
    private MeituanStaticInfoService meituanStaticInfoService;
    @Resource
    private RateHawkCPSQueryPriceService rateHawkCPSQueryPriceService;
    @Resource
    private ExpediaCPSQueryPriceService expediaCPSQueryPriceService;
    @Resource
    private ExpediaStaticDataIngestionService expediaStaticDataIngestionService;
    @Resource
    private com.trip.booking.spa.core.api.expedia.staticdata.catalog.ExpediaCatalogTransformService expediaCatalogTransformService;
    @Resource
    private com.trip.booking.spa.core.api.expedia.staticdata.service.ExpediaCatalogSeedService expediaCatalogSeedService;
    @Resource
    private com.trip.booking.spa.core.api.expedia.staticdata.service.ExpediaGeographyIngestionService expediaGeographyIngestionService;
    @Resource
    private com.trip.booking.spa.core.api.expedia.staticdata.service.ExpediaProductMappingService expediaProductMappingService;

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
    @ApiOperation("Expedia产品映射建档（原saveOrUpdateProductInfo）；propertyIds空=分页全量")
    public HttpResponse expediaCatalogProducts(@RequestParam(value = "propertyIds", required = false) String propertyIds,
                                               @RequestParam(value = "checkIn", required = false) String checkIn,
                                               @RequestParam(value = "checkOut", required = false) String checkOut,
                                               @RequestParam(value = "startNum", required = false) Integer startNum) {
        List<String> ids = org.apache.commons.lang3.StringUtils.isBlank(propertyIds)
                ? List.of()
                : Arrays.asList(propertyIds.split(","));
        return HttpResponse.getSuccessInstance(expediaProductMappingService.syncProducts(checkIn, checkOut, ids, startNum));
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

    @GetMapping("/push")
    @ApiOperation("HotelList查询")
    public HttpResponse meituanHotelList(@RequestParam("cityId") String cityId,
                                         @RequestParam("checkIn") String checkIn,
                                         @RequestParam("checkOut") String checkOut
    ) {
        System.out.println("push");
        travelconnectHotelService.getHotelCodeListByCity(cityId, checkIn, checkOut);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/push/AIC")
    @ApiOperation("HotelList查询")
    public HttpResponse aicHotelList(@RequestParam("cityId") String cityId) {
        System.out.println("push");
        aichotelsHotelService.getHotelCodeListByCity(cityId);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/push/DL")
    @ApiOperation("酒店静态数据查询-道旅")
    public HttpResponse hotelListDL(@RequestParam("staticType") String staticType,
                                    @RequestParam("startDate") String startDate,
                                    @RequestParam("endDate") String endDate,
                                    @RequestParam("startNum") int startNum,
                                    @RequestParam("endNum") int endNum,
                                    @RequestParam("downloadFlag") boolean downloadFlag) {
        didatravelHotelService.queryAndSaveStaticInfo(staticType, startDate, endDate, startNum, endNum, downloadFlag);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/push/hui")
    @ApiOperation("HotelList查询")
    public HttpResponse huiTravelHotelList(@RequestParam("countryCode") String countryCode, @RequestParam("cityId") String cityId) {
        System.out.println("push");
        huiTravelService.getHotelCodeListByCity(countryCode, cityId);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/save/fastPayHotels/hotel")
    @ApiOperation("酒店静态数据保存-fastPayHotels")
    public HttpResponse expediaSaveHotelInfo(@RequestParam(name = "days") int days,
                                             @RequestParam("type") String type) {
        fastPayStaticInfoService.saveHotelList(days, type);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping(value = "/save/rateHawk/hotel")
    @ApiOperation("酒店静态数据保存-rateHawk")
    public HttpResponse rateHawkSaveHotelInfo(@RequestParam(value = "downloadFlag", required = false) Boolean downloadFlag) {
        rateHawkService.queryAndSaveStaticInfo(downloadFlag);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping(value = "/save/rateHawk/product")
    @ApiOperation("产品静态数据保存-rateHawk")
    public HttpResponse rateHawkSaveProductInfo(@RequestParam(value = "checkInDate", required = false) String checkInDate,
                                                @RequestParam(value = "checkOutDate", required = false) String checkOutDate,
                                                @RequestParam(value = "supplierHotelIds", required = false) List<String> supplierHotelIds,
                                                @RequestParam(value = "startNum", required = false) Integer startNum) {
        rateHawkService.queryAndSaveProductInfo(checkInDate, checkOutDate, supplierHotelIds, startNum);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/meituan/hotel/list")
    @ApiOperation("美团hotelIdList查询")
    public HttpResponse meituanHotelList(@RequestParam("maxId") Long maxId,
                                         @RequestParam("pageSize") Integer pageSize) {

        meituanStaticInfoService.queryHotelIdList(maxId, pageSize);

        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/meituan/hotel/push")
    @ApiOperation("美团酒店/酒店/房型/产品push")
    public HttpResponse meituanHotelList(@RequestParam(value = "pageNumber", required = false) Integer pageNumber,
                                         @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                         @RequestParam(value = "type", required = false) String type) {

        meituanStaticInfoService.saveOrUpdateHotelInfo(pageNumber, pageSize, type);
        return HttpResponse.getSuccessInstance();
    }

    /**
     * 手动触发一轮 RateHawk 刷价。速率、批量与互斥锁由 Service 统一解析，与定时调度一致。
     *
     * <p>本入口不受 task.ratehawk-cps.enabled 约束：定时关闭期间手动补刷是正当运维场景。
     * 该绕过以 warn 日志留痕（PROJECT.md §3.8.4）。
     */
    @GetMapping(value = "/rateHawk/priceCache")
    @ApiOperation("rateHawk价格缓存-手动触发一轮")
    public HttpResponse priceCache() {
        log.warn("[gate] 绕过 task.ratehawk-cps.enabled：BackDoor 手动触发一轮刷价");
        rateHawkCPSQueryPriceService.queryPriceQueueTask(0, 0, "manual");
        return HttpResponse.getSuccessInstance();
    }

    /**
     * 手动触发一轮 Expedia 刷价，语义同 {@link #priceCache()}。
     */
    @GetMapping(value = "/expedia/priceCache")
    @ApiOperation("expedia价格缓存-手动触发一轮")
    public HttpResponse expediaPriceCache() {
        log.warn("[gate] 绕过 task.expedia-cps.enabled：BackDoor 手动触发一轮刷价");
        expediaCPSQueryPriceService.queryPriceQueueTask(0, 0, "manual");
        return HttpResponse.getSuccessInstance();
    }
}
