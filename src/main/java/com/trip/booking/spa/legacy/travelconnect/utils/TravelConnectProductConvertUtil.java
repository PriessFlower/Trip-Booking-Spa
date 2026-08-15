package com.trip.booking.spa.legacy.travelconnect.utils;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.platform.util.DateFormatUtils;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class TravelConnectProductConvertUtil {
    public static List<ProductRespDTO> convertRatePlanVO(SearchResponse searchResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        searchResponse.getData().getHoteldetail().getRooms().forEach(productVO -> respDTOList.add(
                ProductRespDTO.builder()
                        .productId(buildProductId(productVO.getRoomid(), productVO.isIncludebreakfast() ? 1 : 0, productVO.getAdultcount(), productVO.getChildcount()))
                        .planSession(productVO.getPlansid())
                        .currencyType(productVO.getCurrency())
                        .supplierId(SupplierSourceEnum.TRAVELCONNECT.getCode())
                        .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(productVO.getRoomname()).build())
                        .totalPrice((int) productVO.getTotal() * 100)
                        .hotelId(searchResponse.getData().getHoteldetail().getHotelcode())
                        .priceInfos(buildPriceInfos(searchResponse.getCheckInDate(), searchResponse.getCheckOutDate(), productVO.getTotal() * 100))
                        .meal(productVO.isIncludebreakfast() ? Meal.builder().count(productVO.getAdultcount()).build() : Meal.builder().count(0).build())
                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                        .build()
        ));
        return respDTOList;
    }

    private static String buildProductId(String roomId, Integer breakfast, int adultCount, int childCount) {
        return roomId + "_" + breakfast + "_" + adultCount + "_" + childCount;
    }

    public static List<PriceInfo> buildPriceInfos(String checkIn, String checkOut, double price) {
        int daysNumber = DateFormatUtils.getBetweenDays(checkIn, checkOut);
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (int i = 0; i < daysNumber; i++) {
            String date = DateFormatUtils.format4y2M2d(
                    DateFormatUtils.dateAddDays(
                            DateFormatUtils.parse4y2M2d(checkIn), i));

            PriceInfo priceInfo = PriceInfo.builder()
                    .date(date)
                    .price((int) price / daysNumber)
                    .build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }
}
