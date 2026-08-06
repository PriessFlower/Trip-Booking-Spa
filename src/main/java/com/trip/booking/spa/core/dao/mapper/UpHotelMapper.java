package com.trip.booking.spa.core.dao.mapper;

import com.trip.booking.spa.core.dao.entity.UpHotel;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author cxj
 * @date 2024/1/15
 * @Description
 */
@Repository
public interface UpHotelMapper {

    /**
     * 通过供应关系获取去重的酒店列表
     *
     * @param supplierList
     * @param distributeId
     * @return
     */
    List<String> getHotelList(@Param("dId") Integer distributeId);

    /**
     * 获取酒店通过酒店ID
     *
     * @param distributeId
     * @param supplierId
     * @param bgHotelId
     * @return
     */
    UpHotel getSellerHotel(@Param("dId") Integer distributeId,
                           @Param("sId") Integer supplierId, @Param("hId") Long bgHotelId);


    /**
     * 获取酒店通过酒店ID
     *
     * @param distributeIds
     * @param supplierId
     * @param bgHotelId
     * @return
     */
    Integer getSellerHotels(@Param("dList") List<Integer> distributeIds,
                           @Param("sId") Integer supplierId, @Param("hId") Long bgHotelId);


    /**
     * 获取酒店列表，带分页
     *
     * @param lastId
     * @param pageSize
     * @return
     */
    List<UpHotel> getUpHotelsWithLimit(@Param("lastId") long lastId, @Param("pageSize") int pageSize);

    /**
     * 通过酒店ID删除美团白名单酒店
     *
     * @param bgHotelId
     * @return
     */
    UpHotel delMeituanHotel(@Param("hId") Long bgHotelId);


    /**
     * 通过酒店ID删除同程艺龙白名单酒店
     *
     * @param bgHotelId
     * @return
     */
    UpHotel delElongHotel(@Param("hId") Long bgHotelId);


    /**
     * 通过供应关系获取去重的酒店分页列表
     *
     * @param supplierList
     * @param distributeId
     * @return
     */
    List<Long> getUpHotelListByDistributeIdAndSupplierIdAndPage(@Param("sList") List<Integer> supplierList, @Param("dId") Integer distributeId,@Param("pageNum") int pageNum,@Param("pageSize") int pageSize);

    /**
     * @description:获取全量可售酒店id
     * @author: dick_w
     * @date: 2025/1/14 14:47
     * @param: []
     * @return: java.util.List<java.lang.String>
     **/
    List<String> getAllUpHotelList();
}
