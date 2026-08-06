package com.trip.booking.spa.core.dao.mapper;

import com.trip.booking.spa.core.dao.entity.HotelInfo;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * action
 *
 * @author zhe.hao
 */
@Repository
public interface HotelInfoMapper {
    /**
     * 修改物理房型
     *
     * @param list
     * @return
     */
    int batchUpdate(@Param("list")List<HotelInfo> list);

}
