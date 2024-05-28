package com.bingo.hotel.spa.intl.core.api.didatravel.utils;

import com.bingo.hotel.spa.intl.cli.dto.*;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import org.apache.commons.compress.utils.Lists;

import java.math.BigDecimal;
import java.util.List;

public class DidaTravelProductConvertUtil {
    public static List<ProductRespDTO> convertRatePlanVO(DidaTravelResponse didaTravelResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        didaTravelResponse.getSuccess().getPriceDetails().getHotelList().forEach(hotelType -> {
            hotelType.getRatePlanList().forEach(ratePlan -> {
                ProductInfo productInfo = ProductInfo.builder()
                        .inventory(ratePlan.getRoomOccupancy().getRoomNum())
                        .productStatus(1)
                        .productName(ratePlan.getRatePlanName())
                        .build();

                Room room = Room.builder()
                        .roomId(String.valueOf(ratePlan.getRoomTypeID()))
                        .roomName(ratePlan.getRoomName())
                        .build();

                ProductRespDTO build = ProductRespDTO.builder()
                        .productId(ratePlan.getRatePlanID())
                        .currencyType(ratePlan.getCurrency())
                        .supplierId(SupplierSourceEnum.DIDATRAVEL.getCode())
                        .productInfo(productInfo)
                        .totalPrice(ratePlan.getTotalPrice().multiply(BigDecimal.valueOf(100)).intValue())
                        .hotelId(String.valueOf(didaTravelResponse.getSuccess().getPriceDetails().getHotelList().get(0).getHotelID()))
                        .priceInfos(buildPriceInfos(ratePlan.getPriceList()))
                        .room(room)
                        .currency(ratePlan.getCurrency())
                        .meal(ratePlan.getPriceList().get(0).getMealType() == 1 ? Meal.builder().count(0).build() : Meal.builder().count(ratePlan.getPriceList().get(0).getMealAmount()).build())
                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                        .build();
                respDTOList.add(build);
            });
        });
        return respDTOList;
    }

    public static List<PriceInfo> buildPriceInfos(List<DidaTravelResponse.HotelTypeRatePlanPriceInfo> hotelTypeRatePlanPriceInfo ) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        hotelTypeRatePlanPriceInfo.forEach(ratePlanPriceInfo -> {
            PriceInfo priceInfo = PriceInfo.builder()
                    .date(ratePlanPriceInfo.getStayDate())
                    .price(ratePlanPriceInfo.getPrice().multiply(BigDecimal.valueOf(100)).intValue())
                    .build();
            priceInfos.add(priceInfo);
        });
        return priceInfos;
    }
}
