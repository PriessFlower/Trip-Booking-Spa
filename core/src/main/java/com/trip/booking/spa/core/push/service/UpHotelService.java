package com.trip.booking.spa.core.push.service;


import com.trip.booking.spa.core.push.model.UpHotel;

import java.util.List;

public interface UpHotelService {
    /**
     * 通过分销商ID获取可售酒店列表
     *
     * @param distributeId
     * @return
     */
    List<String> getHotelListByDistributeId(Integer distributeId);

    /**
     * 获取酒店通过酒店ID
     *
     * @param distributeId
     * @param supplierId
     * @param bgHotelId
     * @return
     */
    UpHotel getSellerHotel(Integer distributeId,
                           Integer supplierId, Long bgHotelId);

    /**
     * 获取酒店通过酒店ID
     *
     * @param distributeIds
     * @param supplierId
     * @param bgHotelId
     * @return
     */
    Integer getSellerHotel(List<Integer> distributeIds,
                           Integer supplierId, Long bgHotelId);

    /**
     * 通过酒店ID删除美团白名单酒店
     *
     * @param bgHotelId
     * @return
     */
    UpHotel delMeituanHotel(Long bgHotelId);

    /**
     * 通过分销商供应商ID获取可售酒店列表
     *
     * @param distributeId
     * @return
     */
    List<String> getHotelListByDistributeId(Integer distributeId,List<Integer> supplierIds);


    List<Long> getUpHotelListByDistributeIdAndSupplierIdAndPage(Integer distributeId,List<Integer> supplierIds,int pageNum, int pageSize);

}
