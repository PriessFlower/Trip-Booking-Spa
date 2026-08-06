package com.trip.booking.spa.core.api.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trip.booking.spa.core.api.common.bean.CityZone;
import com.trip.booking.spa.core.api.common.bean.SupplierHotelIdList;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author xrt
 */
@Repository
public interface InitTimeZoneMapper extends BaseMapper<CityZone> {
    List<CityZone> getCityZoneListByCityNames(@Param("list") List<CityZone> cityZoneList);

    String getCityZoneByCityName(@Param("cityName") String cityName, @Param("countryName") String countryName);

    void insertBatch(@Param("list") List<CityZone> cityZoneList);

    void delBatch(@Param("list") List<CityZone> cityZoneList);

    @Select("select 1")
    int test();

    List<CityZone> getCityZoneNoneList();

    void delBatchByIds(@Param("cityZoneIds") List<CityZone> cityZoneIds);

    int updateBatch(@Param("list") List<CityZone> cityZones);
}
