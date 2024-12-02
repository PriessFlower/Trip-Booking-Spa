package com.bingo.hotel.spa.intl.core.push.service.impl;


import com.bingo.hotel.spa.intl.core.api.common.mapper.UpHotelMapper;
import com.bingo.hotel.spa.intl.core.push.model.UpHotel;
import com.bingo.hotel.spa.intl.core.push.service.UpHotelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@Slf4j
public class UpHotelServiceImpl implements UpHotelService {
    @Autowired
    private UpHotelMapper upHotelMapper;

    @Override
    public List<String> getHotelListByDistributeId(Integer distributeId) {

        List<String> upHotelDTOList =
                upHotelMapper.getHotelList(distributeId);

        if (CollectionUtils.isEmpty(upHotelDTOList)) {
            log.warn("getHotelListByDistributeId is empty");
        }

        return upHotelDTOList;
    }

    @Override
    public UpHotel getSellerHotel(Integer distributeId, Integer supplierId, Long bgHotelId) {
        return upHotelMapper.getSellerHotel(distributeId, supplierId, bgHotelId);
    }

    @Override
    public Integer getSellerHotel(List<Integer> distributeIds, Integer supplierId, Long bgHotelId) {
        return upHotelMapper.getSellerHotels(distributeIds, supplierId, bgHotelId);
    }

    @Override
    public UpHotel delMeituanHotel(Long bgHotelId) {
        return upHotelMapper.delMeituanHotel(bgHotelId);
    }


    @Override
    public List<String> getHotelListByDistributeId(Integer distributeId, List<Integer> supplierIds) {
        List<String> upHotelDTOList =
                upHotelMapper.getHotelList(distributeId);

        if (CollectionUtils.isEmpty(upHotelDTOList)) {
            log.warn("getHotelListByDistributeIdAndSupplier is empty");
        }

        return upHotelDTOList;
    }

    @Override
    public List<Long> getUpHotelListByDistributeIdAndSupplierIdAndPage(Integer distributeId, List<Integer> supplierIds, int pageNum, int pageSize) {
        List<Long> upHotelDTOList =
                upHotelMapper.getUpHotelListByDistributeIdAndSupplierIdAndPage(supplierIds, distributeId, pageNum, pageSize);

        if (CollectionUtils.isEmpty(upHotelDTOList)) {
            log.warn("getHotelListByDistributeIdAndSupplier is empty");
        }

        return upHotelDTOList;
    }


}
