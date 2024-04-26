package com.bingo.hotel.spa.intl.core.api.aichotels.utils;

import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.prebook.PreBookResponse;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.util.DateFormatUtils;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class AichotelsProductConvertUtil {
    public static List<ProductRespDTO> convertRatePlanVO(AvailabilityResponse availabilityResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        availabilityResponse.getRoom_list().forEach(roomVO ->
                roomVO.getRates_and_cancellation_policies().forEach(productVO -> respDTOList.add(
                        ProductRespDTO.builder()
                                .productId(productVO.getRoom_key())
                                .currencyType(productVO.getCurrency())
                                .supplierId(SupplierSourceEnum.AICHOTELS.getCode())
                                .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomVO.getRoom_name()).build())
                                .totalPrice((int) (Double.parseDouble(productVO.getTotal_amount_after_tax()) * 100))
                                .hotelId(availabilityResponse.getHotelCode())
                                .priceInfos(buildPriceInfos(productVO.getRates()))
                                .meal(productVO.getBreakfast().getInclude() == 1 ? Meal.builder().count(productVO.getBreakfast().getCount()).build() : Meal.builder().count(0).build())
                                .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                .build()
                )));
        return respDTOList;
    }

    public static List<ProductRespDTO> convertRatePlanCheckVO(PreBookResponse preBookResponse,String hotelCode) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        preBookResponse.getRoom_list().forEach(roomVO ->
                roomVO.getRates_and_cancellation_policies().forEach(productVO -> respDTOList.add(
                        ProductRespDTO.builder()
                                .productId(productVO.getRoom_key())
                                .currencyType(productVO.getCurrency())
                                .supplierId(SupplierSourceEnum.AICHOTELS.getCode())
                                .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomVO.getRoom_name()).build())
                                .totalPrice((int) (Double.parseDouble(productVO.getTotal_amount_after_tax()) * 100))
                                .hotelId(hotelCode)
                                .priceInfos(buildPriceInfosCheck(productVO.getRates()))
                                .meal(productVO.getBreakfast().getInclude() == 1 ? Meal.builder().count(productVO.getBreakfast().getCount()).build() : Meal.builder().count(0).build())
                                .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                .build()
                )));
        return respDTOList;
    }

    public static List<PriceInfo> buildPriceInfos(List<AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean> ratesBeans) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean ratesBean : ratesBeans) {
            PriceInfo priceInfo = PriceInfo.builder()
                    .date(ratesBean.getCheck_in())
                    .price((int) (Double.parseDouble(ratesBean.getAmount_after_tax().getNight_rate()) * 100))
                    .build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }

    public static List<PriceInfo> buildPriceInfosCheck(List<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean> ratesBeans) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean ratesBean : ratesBeans) {
            PriceInfo priceInfo = PriceInfo.builder()
                    .date(ratesBean.getCheck_in())
                    .price((int) (Double.parseDouble(ratesBean.getAmount_after_tax().getNight_rate()) * 100))
                    .build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }
}
