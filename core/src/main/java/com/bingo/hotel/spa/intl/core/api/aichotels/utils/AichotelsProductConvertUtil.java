package com.bingo.hotel.spa.intl.core.api.aichotels.utils;

import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.enums.RefundType;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.prebook.PreBookResponse;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.util.DateFormatUtils;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import org.apache.commons.compress.utils.Lists;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class AichotelsProductConvertUtil {
    public static List<ProductRespDTO> convertRatePlanVO(AvailabilityResponse availabilityResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        availabilityResponse.getRoom_list().forEach(roomVO ->
                roomVO.getRates_and_cancellation_policies().forEach(productVO -> {
                    AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean cancellationInformation
                            = productVO.getCancellation_information();
                    List<CancelPolicy> cancelPolicies
                            = convertCancelPolicy(cancellationInformation, DateFormatUtils.parse(productVO.getRates().get(0).getCheck_in(), "yyyy-MM-dd"),
                            new BigDecimal(productVO.getTotal_amount_after_tax()));
                    respDTOList.add(
                            ProductRespDTO.builder()
                                    .productId(productVO.getRoom_key())
                                    .currencyType(productVO.getCurrency())
                                    .supplierId(SupplierSourceEnum.AICHOTELS.getCode())
                                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomVO.getRoom_name()).build())
                                    .totalPrice((int) (Double.parseDouble(productVO.getTotal_amount_after_tax()) * 100))
                                    .hotelId(availabilityResponse.getHotelCode())
                                    .priceInfos(buildPriceInfos(productVO.getRates()))
                                    .meal(productVO.getBreakfast().getInclude() == 1 ? Meal.builder().count(productVO.getBreakfast().getCount()).build() : Meal.builder().count(0).build())
                                    .cancelPolicy(cancelPolicies)
                                    .maxOccupancy(0)
                                    .build()
                    );
                }));
        return respDTOList;
    }

    public static List<ProductRespDTO> convertRatePlanCheckVO(PreBookResponse preBookResponse, String hotelCode) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        preBookResponse.getRoom_list().forEach(roomVO ->
                roomVO.getRates_and_cancellation_policies().forEach(productVO -> {
                    PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean cancellationInformation
                            = productVO.getCancellation_information();
                    List<CancelPolicy> cancelPolicies
                            = convertCancelPolicy(cancellationInformation, DateFormatUtils.parse4y2M2d(preBookResponse.getCheckIn()),
                            new BigDecimal(productVO.getTotal_amount_after_tax()));
                    respDTOList.add(
                            ProductRespDTO.builder()
                                    .productId(productVO.getRoom_key())
                                    .currencyType(productVO.getCurrency())
                                    .supplierId(SupplierSourceEnum.AICHOTELS.getCode())
                                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomVO.getRoom_name()).build())
                                    .totalPrice((int) (Double.parseDouble(productVO.getTotal_amount_after_tax()) * 100))
                                    .hotelId(hotelCode)
                                    .priceInfos(buildPriceInfosCheck(productVO.getRates(), preBookResponse.getCheckIn(), preBookResponse.getCheckOut()))
                                    .meal(productVO.getBreakfast().getInclude() == 1 ? Meal.builder().count(productVO.getBreakfast().getCount()).build() : Meal.builder().count(0).build())
                                    // .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                    .cancelPolicy(cancelPolicies)
                                    .maxOccupancy(0)
                                    .build()
                    );
                }));
        return respDTOList;
    }

    public static List<CancelPolicy> convertCancelPolicy(PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean
                                                                 ratePlanCancellationPolicy, Date checkIn, BigDecimal totalPrice)  {
        if(ratePlanCancellationPolicy.getSupport_cancel().equals("no")){
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        if(ratePlanCancellationPolicy.getNon_refundable().equals("yes")){
            return List.of(CancelPolicy.builder().cancelType(0).build());
        } else {
            Iterator<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> cancelDetails = ratePlanCancellationPolicy.getDetails().iterator();
            while (cancelDetails.hasNext()) {
                PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean next = cancelDetails.next();
                if(!next.getPolicy_code().equals("CXP")){
                    cancelDetails.remove();
                }
            }
            Collections.sort(ratePlanCancellationPolicy.getDetails(), (map1, map2) -> map1.getDatetime().compareTo(map2.getDatetime()));
            PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean policyOne
                    = ratePlanCancellationPolicy.getDetails().get(0);
            Date date = formatDate(policyOne.getDatetime());
            int diffHour = DateUtil.diffHour(date, solveCheckIn(checkIn));
            if(diffHour <= 24){
                BigDecimal amount = new BigDecimal(policyOne.getAmount_penalty());
                return List.of(CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone(getTimeZone("GMT" + ratePlanCancellationPolicy.getTimezone()))
                    .before(25)
                    .type(totalPrice.subtract(amount).equals(0) ? RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                    .value(amount.doubleValue()).build());
            } else {
                List<CancelPolicy> cancelPolicyList = Lists.newArrayList();
                Iterator<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> iterator = ratePlanCancellationPolicy.getDetails().iterator();
                while (iterator.hasNext()){
                    PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean next = iterator.next();
                    if(next.getPolicy_code().equals("CNS")){
                        iterator.remove();
                    }
                }
                for (PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean policy : ratePlanCancellationPolicy.getDetails()) {
                    BigDecimal amount = new BigDecimal(policy.getAmount_penalty());
                    CancelPolicy cancelPolicy = CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone()))
                            .before(DateUtil.diffHour(formatDate(policy.getDatetime()), solveCheckIn(checkIn)))
                            .type(cancelType(totalPrice,amount,policy.getFee_type()))
                            .value(amount.doubleValue()).build();
                    cancelPolicyList.add(cancelPolicy);
                }
                return cancelPolicyList;
            }
        }
    }
    public static List<CancelPolicy> convertCancelPolicy(AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean ratePlanCancellationPolicy,
                                                         Date checkIn, BigDecimal totalPrice)  {
        if(ratePlanCancellationPolicy.getSupport_cancel().equals("no")){
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        if(ratePlanCancellationPolicy.getNon_refundable().equals("yes")){
            return List.of(CancelPolicy.builder().cancelType(0).build());
        } else {
            Iterator<AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> cancelDetails = ratePlanCancellationPolicy.getDetails().iterator();
            while (cancelDetails.hasNext()) {
                AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean next = cancelDetails.next();
                if(!next.getPolicy_code().equals("CXP")){
                    cancelDetails.remove();
                }
            }
            Collections.sort(ratePlanCancellationPolicy.getDetails(), (map1, map2) ->  map1.getDatetime().compareTo(map2.getDatetime()));
            AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean policyOne
                    = ratePlanCancellationPolicy.getDetails().get(0);

            // 转换为 Date
            Date date = formatDate(policyOne.getDatetime());
            int diffHour = DateUtil.diffHour(date, solveCheckIn(checkIn));
            if(diffHour <= 24){
                BigDecimal amount = new BigDecimal(policyOne.getAmount_penalty());
                return List.of(CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone()))
                        .before(25)
                        .type(totalPrice.subtract(amount).equals(0) ? RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                        .value(amount.doubleValue()).build());
            } else {
                List<CancelPolicy> cancelPolicyList = Lists.newArrayList();
                Iterator<AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> iterator = ratePlanCancellationPolicy.getDetails().iterator();
                while (iterator.hasNext()){
                    AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean next = iterator.next();
                    if(next.getPolicy_code().equals("CNS")){
                        iterator.remove();
                    }
                }
                for (AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean policy : ratePlanCancellationPolicy.getDetails()) {
                    BigDecimal amount = new BigDecimal(policy.getAmount_penalty());
                    CancelPolicy cancelPolicy = CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone()))
                            .before(DateUtil.diffHour(formatDate(policy.getDatetime()), solveCheckIn(checkIn)))
//                            .type(totalPrice.subtract(amount).equals(0) ? RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                            .type(cancelType(totalPrice,amount,policy.getFee_type()))
                            .value(amount.doubleValue()).build();
                    cancelPolicyList.add(cancelPolicy);
                }
                return cancelPolicyList;
            }
        }
    }

    public static String getTimeZone(String timeZone) {
        String[] partRight = timeZone.split(" ");
        String[] zone = partRight[1].split(":");
        String hour = zone[0].substring(3, zone[0].length());
        return hour;
    }

    public static RefundType cancelType(BigDecimal totalPrice,BigDecimal amount,String feeType) {
        if(totalPrice.subtract(amount).equals(0)) {
            return RefundType.NO_DEDUCTION;
        } else if(feeType.equals("percentage")) {
            return RefundType.DEDUCT_BY_PERCENT;
        } else if(feeType.equals("nights ") || feeType.equals("price ") || feeType.equals("total_amount")) {
            return RefundType.DEDUCT_BY_AMOUNT;
        } else {
            return RefundType.DEDUCT_BY_AMOUNT;
        }
    }

    public static Date solveCheckIn(Date checkIn){
        Date date = DateUtil.addDay(checkIn, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String format = sdf.format(date);
        try {
            Date parse = sdf.parse(format);
            return parse;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Date formatDate(String datetime) {
        // 定义日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        // 解析 LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.parse(datetime, formatter);
        // 转换为 Date
        Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        return date;
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

    public static List<PriceInfo> buildPriceInfosCheck(List<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean> ratesBeans, String checkIn, String checkOut) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        int daysNumber = DateFormatUtils.getBetweenDays(checkIn, checkOut);
        if (ratesBeans.size() == daysNumber) {
            for (PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean ratesBean : ratesBeans) {
                PriceInfo priceInfo = PriceInfo.builder()
                        .date(ratesBean.getCheck_in())
                        .price((int) (Double.parseDouble(ratesBean.getAmount_after_tax().getNight_rate()) * 100))
                        .build();
                priceInfos.add(priceInfo);
            }
        } else {
            for (int i = 0; i < daysNumber; i++) {
                String date = DateFormatUtils.format4y2M2d(
                        DateFormatUtils.dateAddDays(
                                DateFormatUtils.parse4y2M2d(checkIn), i));
                PriceInfo priceInfo = PriceInfo.builder()
                        .date(date)
                        .price((int) (Double.parseDouble(ratesBeans.get(0).getAmount_after_tax().getNight_rate()) * 100))
                        .build();
                priceInfos.add(priceInfo);
            }
        }

        return priceInfos;

    }
}
