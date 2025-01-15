package com.bingo.hotel.spa.intl.core.api.common.mapper;

import com.bingo.hotel.spa.intl.core.api.common.bean.CityZone;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author xrt
 */
@Repository
public interface InitTimeZoneMapper {
    List<CityZone> getCityZoneListByCityNames(@Param("list") List<CityZone> cityZoneList);

    String getCityZoneByCityName(@Param("cityName") String cityName, @Param("countryName") String countryName);

    void insertBatch(@Param("list") List<CityZone> cityZoneList);

    void delBatch(@Param("list") List<CityZone> cityZoneList);

    @Select("select 1")
    int test();

    List<CityZone> getCityZoneListByCityNamesNew(@Param("list") List<CityZone> cityZoneList);

    void delBatchByIds(@Param("cityZoneIds") List<CityZone> cityZoneIds);
}
