package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaRegionsClient;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.RegionsInfoResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按地区查其下酒店 ID（承接旧 ExpediaStaticInfoService.queryHotelIdByCity）。
 *
 * <p>2026-09-08 静态数据链路撤除后，本类是 content 包里唯一剩下的地理读取入口：
 * 每次都现问 Rapid Geography，不落库——原先落 city_info / country_info 的建档路径已随
 * 目录域一同撤除（docs/legacy-schema-restoration.md）。留着它是因为
 * {@code /query/expediaHotelIdByCity} 是在册的 SPA 契约端点（SpaControllerContractTest 钉着）。
 */
@Service
public class ExpediaRegionService {

    private static final String LANG_EN = "en-US";

    private final ExpediaRegionsClient regionsClient;

    public ExpediaRegionService(ExpediaRegionsClient regionsClient) {
        this.regionsClient = regionsClient;
    }

    public List<String> queryHotelIdsByRegion(String regionId) {
        RegionsInfoResponse region = regionsClient.fetchRegion(regionId, LANG_EN, "property_ids");
        if (region == null || CollectionUtils.isEmpty(region.getProperty_ids())) {
            return List.of();
        }
        return region.getProperty_ids();
    }
}
