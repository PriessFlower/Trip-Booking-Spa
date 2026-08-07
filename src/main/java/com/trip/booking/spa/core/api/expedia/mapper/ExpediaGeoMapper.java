package com.trip.booking.spa.core.api.expedia.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.HashMap;

/**
 * Geography 档案持久化：country_info / city_info（原系统还原表，
 * 见 config/mysql/spa-catalog-schema.sql / docs/legacy-schema-restoration.md）。
 */
@Mapper
public interface ExpediaGeoMapper {

    int upsertCountryInfo(HashMap<String, Object> params);

    int upsertCityInfo(HashMap<String, Object> params);
}
