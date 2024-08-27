package com.bingo.hotel.spa.intl.core.api.huitravel.utils;

import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.NightlyRate;
import com.bingo.hotel.spa.intl.core.util.DateFormatUtils;
import org.apache.commons.compress.utils.Lists;

import java.math.BigDecimal;
import java.util.List;

public class HuiTravelProductConvertUtil {
    public static List<ProductRespDTO> convertRatePlanVO(AvailabilityResponse availabilityResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        availabilityResponse.getResult().getPrices().forEach(productVO -> respDTOList.add(ProductRespDTO.builder()
                        .productId(productVO.getRpid() + "")
//                        .currencyType(productVO.getCurrency())
                        .supplierId(SupplierSourceEnum.HUITRAVEL.getCode())
                        .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(productVO.getName()).build())
//                        .totalPrice((int) (Double.parseDouble(productVO.getTotal_amount_after_tax()) * 100))
                        .hotelId(productVO.getHid() + "")
                        .priceInfos(buildPriceInfos(productVO.getNightlyrate()))
                        .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                        .build())
        );
        return respDTOList;
    }

    public static List<ProductRespDTO> convertRatePlanCheckVO(AvailabilityResponse availabilityResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        BigDecimal totalPrice = availabilityResponse.getCheckResponse().getResult().getNightlyrate().stream()
                .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        availabilityResponse.getResult().getPrices().forEach(productVO -> respDTOList.add(ProductRespDTO.builder()
                        .productId(productVO.getRpid() + "")
//                        .currencyType(productVO.getCurrency())
                        .supplierId(SupplierSourceEnum.HUITRAVEL.getCode())
                        .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(productVO.getName()).build())
                        .totalPrice(totalPrice.multiply(new BigDecimal(100)).intValue())
                        .hotelId(productVO.getHid() + "")
                        .priceInfos(buildPriceInfos(availabilityResponse.getCheckResponse().getResult().getNightlyrate()))
                        .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                        .build())
        );
        return respDTOList;
    }

    public static List<PriceInfo> buildPriceInfos(List<NightlyRate> nightlyrates) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (NightlyRate nightlyrate : nightlyrates) {
            PriceInfo priceInfo = PriceInfo.builder()
                    .date(nightlyrate.getDate())
                    .price(((nightlyrate.getCost().multiply(new BigDecimal(100))).intValue()))
                    .build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }

//    public static List<PriceInfo> buildPriceInfos(String checkIn, String checkOut, double price) {
//        List<PriceInfo> priceInfos = Lists.newArrayList();
//        for (int i = 0; i < daysNumber; i++) {
//            String date = DateFormatUtils.format4y2M2d(
//                    DateFormatUtils.dateAddDays(
//                            DateFormatUtils.parse4y2M2d(checkIn), i));
//
//            PriceInfo priceInfo = PriceInfo.builder()
//                    .date(date)
//                    .price((int) price / daysNumber)
//                    .build();
//            priceInfos.add(priceInfo);
//        }
//        return priceInfos;
//    }

}
