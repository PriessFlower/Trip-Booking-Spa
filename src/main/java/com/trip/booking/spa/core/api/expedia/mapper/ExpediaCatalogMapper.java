package com.trip.booking.spa.core.api.expedia.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;

/**
 * 阶段2 加工层持久化：快照 → spa 专属目录（原系统还原表）。
 * 目标表见 config/mysql/spa-catalog-schema.sql / docs/legacy-schema-restoration.md：
 * 目录域 hotel_details / room_base / hotel_picture / hotel_extend
 * 档案域 supplier_hotel_base / supplier_room_base
 */
@Mapper
public interface ExpediaCatalogMapper {

    List<HashMap<String, Object>> selectSnapshots(@Param("propertyId") String propertyId);

    List<String> selectAllPropertyIds();

    String selectCountryIdByCode(@Param("countryCode") String countryCode);

    String selectCityIdByName(@Param("countryCode") String countryCode, @Param("cityName") String cityName);

    int upsertHotelDetails(HashMap<String, Object> params);

    int upsertRoomBase(HashMap<String, Object> params);

    int deleteHotelPictures(@Param("hotelId") String hotelId);

    int insertHotelPicture(HashMap<String, Object> params);

    int upsertHotelExtend(HashMap<String, Object> params);

    int upsertSupplierHotelBase(HashMap<String, Object> params);

    int upsertSupplierRoomBase(HashMap<String, Object> params);

    List<String> selectSupplierHotelIds(@Param("supplierId") int supplierId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    int upsertGlobalProductSupplier(HashMap<String, Object> params);

    int upsertSupplierProductBase(HashMap<String, Object> params);

    int markHotelDetailsInactive(@Param("hotelIds") List<String> hotelIds);
}
