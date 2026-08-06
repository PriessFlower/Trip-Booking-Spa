package com.trip.booking.spa.core.dao.mapper;

import com.trip.booking.spa.core.dao.entity.HotelIdList;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * action
 *
 * @author zhe.hao
 */
@Repository
public interface HotelIdListMapper {

    /**
     * 分页查询酒店列表
     *
     * @param pageNum
     * @param pageSize
     * @param supplierId
     * @return
     */
    List<HotelIdList> getHotelIdList(@Param("pageNum") int pageNum, @Param("pageSize") int pageSize,
                                     @Param("supplierId") int supplierId);

    /**
     * 新增酒店列表
     *
     * @param hotelIdList
     * @return
     */
    int insert(HotelIdList hotelIdList);

    /**
     * 修改酒店列表
     *
     * @param hotelIdList
     * @return
     */
    int update(HotelIdList hotelIdList);

    /**
     * 根据Id删除酒店列表
     *
     * @param id
     * @return
     */
    int deleteById(long id);

    /**
     * 修改酒店列表
     *
     * @param list
     * @return
     */
    int batchUpdate(@Param("list") List<HotelIdList> list);

    /**
     * 修改酒店列表
     *
     * @param list
     * @return
     */
    int batchInsert(@Param("list") List<HotelIdList> list);

}
