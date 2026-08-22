package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.RegionsInfoResponse;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ExpediaGeoMapper;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaRegionsClient;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ThreadPoolUtils;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaContinentEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 阶段3 Geography 档案：国家/城市（州省递归）双语建档，落 country_info / city_info。
 * 流程照抄旧链路 ExpediaStaticInfoServiceImpl.saveCountryInfo / saveCityInfo：
 * 洲际枚举遍历 → Regions 递归 descendants → en+zh 各请求一遍 → 省级 ThreadPoolUtils 并发 + 自旋等待。
 * 适配点仅：推中台改为写本地还原表；新增洲际过滤参数（测试/分批用）。
 */
@Slf4j
@Service
public class ExpediaGeographyIngestionService {

    private static final String LANG_EN = "en-US";
    private static final String LANG_ZH = "zh-CN";

    private final ExpediaRegionsClient regionsClient;
    private final ExpediaGeoMapper geoMapper;

    public ExpediaGeographyIngestionService(ExpediaRegionsClient regionsClient, ExpediaGeoMapper geoMapper) {
        this.regionsClient = regionsClient;
        this.geoMapper = geoMapper;
    }

    /**
     * 国家建档（照抄旧 saveCountryInfo）
     *
     * @param continents 洲际过滤（枚举名，如 ASIA）；空=全部大洲
     * @return 落库国家数
     */
    public int syncCountries(Set<String> continents) {
        AtomicInteger saved = new AtomicInteger();
        for (ExpediaContinentEnum continent : ExpediaContinentEnum.values()) {
            if (CollectionUtils.isNotEmpty(continents) && !continents.contains(continent.name())) {
                continue;
            }
            RegionsInfoResponse continentRegion = regionsClient.fetchRegion(continent.getKey(), LANG_EN);
            if (continentRegion == null || continentRegion.getDescendants() == null
                    || CollectionUtils.isEmpty(continentRegion.getDescendants().getCountry())) {
                continue;
            }
            for (String countryId : continentRegion.getDescendants().getCountry()) {
                try {
                    upsertCountry(countryId, continent.getDesc(), continent.getDesc_cn());
                    saved.incrementAndGet();
                } catch (Exception e) {
                    log.error("保存国家信息异常 countryId={}", countryId, e);
                }
            }
            log.info("大洲 {} 国家建档完成", continent.getDesc());
        }
        log.info("保存expedia国家信息完毕，共 {} 国", saved.get());
        return saved.get();
    }

    /** 照抄旧 queryCountryInfo：en 拿基本信息，zh 补中文名 */
    private void upsertCountry(String countryId, String continent, String continentCn) {
        HashMap<String, Object> p = new HashMap<>();
        p.put("countryId", countryId);
        p.put("continent", continent);
        p.put("continentCn", continentCn);
        p.put("note", SupplierSourceEnum.EXPEDIA.getDesc());
        RegionsInfoResponse en = regionsClient.fetchRegion(countryId, LANG_EN);
        if (en != null) {
            p.put("countryCode", en.getCountry_code());
            p.put("countryName", en.getName());
            p.put("longitude", coordinate(en, true));
            p.put("latitude", coordinate(en, false));
        } else {
            p.put("countryCode", null);
            p.put("countryName", null);
            p.put("longitude", BigDecimal.ZERO);
            p.put("latitude", BigDecimal.ZERO);
        }
        RegionsInfoResponse zh = regionsClient.fetchRegion(countryId, LANG_ZH);
        p.put("countryNameCn", zh == null ? null : zh.getName());
        geoMapper.upsertCountryInfo(p);
    }

    /**
     * 城市建档（照抄旧 saveCityInfo/pushCountry）：指定国家或全球，州省级并发递归
     *
     * @param countryIds 国家 region id 列表；空=遍历全部大洲的全部国家
     * @return 已提交建档的国家数（城市在后台线程递归落库）
     */
    public int syncCities(List<String> countryIds) {
        if (CollectionUtils.isNotEmpty(countryIds)) {
            pushCountry(countryIds);
            return countryIds.size();
        }
        AtomicInteger submitted = new AtomicInteger();
        for (ExpediaContinentEnum continent : ExpediaContinentEnum.values()) {
            RegionsInfoResponse continentRegion = regionsClient.fetchRegion(continent.getKey(), LANG_EN);
            if (continentRegion != null && continentRegion.getDescendants() != null
                    && CollectionUtils.isNotEmpty(continentRegion.getDescendants().getCountry())) {
                List<String> countries = continentRegion.getDescendants().getCountry();
                pushCountry(countries);
                submitted.addAndGet(countries.size());
            }
        }
        return submitted.get();
    }

    /** 照抄旧 pushCountry：zh 名 + en 的省级 descendants，每省一个线程递归 */
    private void pushCountry(List<String> countryIds) {
        countryIds.forEach(countryId -> {
            waitIfBacklogged();
            String nameCn = "";
            RegionsInfoResponse zh = regionsClient.fetchRegion(countryId, LANG_ZH);
            if (zh != null) {
                nameCn = StringUtils.defaultString(zh.getName());
            }
            RegionsInfoResponse en = regionsClient.fetchRegion(countryId, LANG_EN);
            if (en != null && en.getDescendants() != null) {
                RegionsInfoResponse.Descendants descendants = en.getDescendants();
                String finalNameCn = nameCn;
                // 补齐旧链路缺失的类型：都会区（曼谷等在此）优先，其次大区
                for (String vicinity : safe(descendants.getMulti_city_vicinity())) {
                    ThreadPoolUtils.execute(() ->
                            queryCityInfo(vicinity, countryId, en.getName(), finalNameCn, countryId));
                }
                for (String region : safe(descendants.getHigh_level_region())) {
                    ThreadPoolUtils.execute(() ->
                            queryCityInfo(region, countryId, en.getName(), finalNameCn, countryId));
                }
                if (CollectionUtils.isNotEmpty(descendants.getProvince_state())) {
                    for (String provinceState : descendants.getProvince_state()) {
                        ThreadPoolUtils.execute(() -> {
                            queryCityInfo(provinceState, countryId, en.getName(), finalNameCn, countryId);
                            log.info("{} 下省份 {} 建档完毕", finalNameCn, provinceState);
                        });
                    }
                }
                // 旧代码注释保留的分支：部分国家没有省级，城市直挂国家
                if (CollectionUtils.isNotEmpty(descendants.getCity())) {
                    for (String city : descendants.getCity()) {
                        ThreadPoolUtils.execute(() ->
                                queryCityInfo(city, countryId, en.getName(), finalNameCn, countryId));
                    }
                }
            }
        });
    }

    /** 照抄旧 waitIfBacklogged：队列积压超过 10 就等上一个国家跑完 */
    private void waitIfBacklogged() {
        if (ThreadPoolUtils.getThreadPool().getQueue().size() > 10) {
            try {
                log.info("等待上一个国家查询完毕");
                Thread.sleep(20 * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            waitIfBacklogged();
        }
    }

    /** 照抄旧 queryCityInfo：zh 名 → en 详情/坐标/类型 → 递归下级（省/市），逐节点落库 */
    private void queryCityInfo(String cityId, String stateId, String stateName, String stateNameCn, String countryId) {
        try {
            HashMap<String, Object> p = new HashMap<>();
            p.put("cityId", cityId);
            p.put("stateId", stateId);
            p.put("stateName", stateName);
            p.put("stateNameCn", stateNameCn);
            p.put("countryId", countryId);

            String nameCn = "";
            RegionsInfoResponse zh = regionsClient.fetchRegion(cityId, LANG_ZH);
            if (zh != null) {
                nameCn = StringUtils.defaultString(zh.getName());
            }
            p.put("cityNameCn", nameCn);

            RegionsInfoResponse en = regionsClient.fetchRegion(cityId, LANG_EN);
            if (en != null) {
                p.put("cityName", en.getName());
                p.put("longitude", coordinate(en, true));
                p.put("latitude", coordinate(en, false));
                p.put("note", en.getType());
            } else {
                p.put("cityName", null);
                p.put("longitude", BigDecimal.ZERO);
                p.put("latitude", BigDecimal.ZERO);
                p.put("note", null);
            }
            geoMapper.upsertCityInfo(p);

            if (en != null && en.getDescendants() != null) {
                RegionsInfoResponse.Descendants descendants = en.getDescendants();
                for (String vicinity : safe(descendants.getMulti_city_vicinity())) {
                    queryCityInfo(vicinity, cityId, en.getName(), nameCn, countryId);
                }
                for (String region : safe(descendants.getHigh_level_region())) {
                    queryCityInfo(region, cityId, en.getName(), nameCn, countryId);
                }
                if (CollectionUtils.isNotEmpty(descendants.getProvince_state())) {
                    for (String provinceState : descendants.getProvince_state()) {
                        queryCityInfo(provinceState, cityId, en.getName(), nameCn, countryId);
                    }
                }
                if (CollectionUtils.isNotEmpty(descendants.getCity())) {
                    for (String city : descendants.getCity()) {
                        queryCityInfo(city, cityId, en.getName(), nameCn, countryId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("保存城市信息异常 cityId={}", cityId, e);
        }
    }

    /** 承接旧 ExpediaStaticInfoService.queryHotelIdByCity：按地区查其下全部酒店ID（include=property_ids） */
    public List<String> queryHotelIdsByRegion(String regionId) {
        RegionsInfoResponse region = regionsClient.fetchRegion(regionId, LANG_EN, "property_ids");
        if (region == null || CollectionUtils.isEmpty(region.getProperty_ids())) {
            return List.of();
        }
        return region.getProperty_ids();
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    /** 照抄旧坐标处理：setScale(10, HALF_EVEN)，空值记 0 */
    private BigDecimal coordinate(RegionsInfoResponse region, boolean longitude) {
        if (region.getCoordinates() == null) {
            return BigDecimal.ZERO;
        }
        String value = longitude ? region.getCoordinates().getCenter_longitude()
                : region.getCoordinates().getCenter_latitude();
        if (StringUtils.isBlank(value)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value).setScale(10, RoundingMode.HALF_EVEN);
    }
}
