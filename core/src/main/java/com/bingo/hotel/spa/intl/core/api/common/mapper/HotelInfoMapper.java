package com.bingo.hotel.spa.intl.core.api.common.mapper;

import com.bingo.hotel.spa.intl.core.api.model.HotelInfo;
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
