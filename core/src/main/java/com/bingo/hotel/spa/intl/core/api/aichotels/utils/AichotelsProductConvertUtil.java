package com.bingo.hotel.spa.intl.core.api.aichotels.utils;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.request.SupplierHotelInfoRequest;
import com.bingo.hotel.base.intl.cli.response.GetCityInfoBySupplierHotelIdResponse;
import com.bingo.hotel.base.intl.cli.result.BaseResult;
import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.Room;
import com.bingo.hotel.spa.intl.cli.enums.RefundType;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.prebook.PreBookResponse;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.utils.DidaTravelProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.util.DateFormatUtils;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Component
@Slf4j
public class AichotelsProductConvertUtil {

    @Autowired
    private HotelBaseIntlClient hotelBaseIntlClient;
    @Autowired
    private DidaTravelProductConvertUtil didaTravelProductConvertUtil;
    public List<ProductRespDTO> convertRatePlanVO(AvailabilityResponse availabilityResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        availabilityResponse.getRoom_list().forEach(roomVO ->
                roomVO.getRates_and_cancellation_policies().forEach(productVO -> {
                    AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean cancellationInformation
                            = productVO.getCancellation_information();
                    List<CancelPolicy> cancelPolicies
                            = convertCancelPolicy(cancellationInformation, DateFormatUtils.parse(productVO.getRates().get(0).getCheck_in(), "yyyy-MM-dd"),
                            DateFormatUtils.parse(productVO.getRates().get(productVO.getRates().size() - 1).getCheck_out(), "yyyy-MM-dd"),
                            new BigDecimal(productVO.getTotal_amount_after_tax()),availabilityResponse.getHotelCode());
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
                                    .room(Room.builder().roomId(roomVO.getRoom_type()).roomName(roomVO.getRoom_name()).build())
                                    .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                    .cancelPolicy(cancelPolicies)
                                    .maxOccupancy(0)
                                    .build()
                    );
                }));
        return respDTOList;
    }

    public List<ProductRespDTO> convertRatePlanCheckVO(PreBookResponse preBookResponse, String hotelCode) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        preBookResponse.getRoom_list().forEach(roomVO ->
                roomVO.getRates_and_cancellation_policies().forEach(productVO -> {
                    PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean cancellationInformation
                            = productVO.getCancellation_information();
                    List<CancelPolicy> cancelPolicies
                            = convertCancelPolicy(cancellationInformation, DateFormatUtils.parse4y2M2d(preBookResponse.getCheckIn()),
                                    DateFormatUtils.parse4y2M2d(preBookResponse.getCheckOut()),
                            new BigDecimal(productVO.getTotal_amount_after_tax()), hotelCode);
                    respDTOList.add(
                            ProductRespDTO.builder()
                                    .productId(productVO.getRoom_key())
                                    .currencyType(productVO.getCurrency())
                                    .supplierId(SupplierSourceEnum.AICHOTELS.getCode())
                                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomVO.getRoom_name()).build())
                                    .totalPrice((int) (Double.parseDouble(productVO.getTotal_amount_after_tax()) * 100))
                                    .hotelId(hotelCode)
                                    .priceInfos(buildPriceInfosCheck(productVO.getRates(), preBookResponse.getCheckIn(), preBookResponse.getCheckOut()))
                                    .room(Room.builder().roomId(roomVO.getRoom_type()).roomName(roomVO.getRoom_name()).build())
                                    .meal(productVO.getBreakfast().getInclude() == 1 ? Meal.builder().count(productVO.getBreakfast().getCount()).build() : Meal.builder().count(0).build())
                                    // .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                    .cancelPolicy(cancelPolicies)
                                    .maxOccupancy(0)
                                    .build()
                    );
                }));
        return respDTOList;
    }

    public List<CancelPolicy> convertCancelPolicy(PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean
                                                                 ratePlanCancellationPolicy, Date checkIn, Date checkOut, BigDecimal totalPrice,String hotelId)  {
        if(ratePlanCancellationPolicy.getSupport_cancel().equals("no")){
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        if(ratePlanCancellationPolicy.getNon_refundable().equals("yes")){
            return List.of(CancelPolicy.builder().cancelType(0).build());
        } else {
            if(CollectionUtils.isEmpty(ratePlanCancellationPolicy.getDetails())){
                return List.of(CancelPolicy.builder().cancelType(0).build());
            }
            filterExcludeCXP(ratePlanCancellationPolicy);
            Collections.sort(ratePlanCancellationPolicy.getDetails(), (map1, map2) -> map1.getDatetime().compareTo(map2.getDatetime()));
            PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean policyOne
                    = ratePlanCancellationPolicy.getDetails().get(0);
            int diffHour = DateUtil.diffHour(formatDate(policyOne.getDatetime()), solveCheckIn(checkIn));

            if(diffHour <= 24){
                RefundType type = cancelType(totalPrice, new BigDecimal(policyOne.getAmount_penalty()), policyOne.getFee_type(),policyOne.getFee_type_value(),checkIn,checkOut);
                if(type.equals(RefundType.NO_CANCEL)){
                    return List.of(CancelPolicy.builder().cancelType(0).build());
                }
                return List.of(CancelPolicy.builder()
                    .cancelType(1)
                        .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone(), hotelId, SupplierSourceEnum.AICHOTELS.getCode()))
                    .before(25)
                    .type(type)
                    .value(Double.valueOf(policyOne.getFee_type_value())).build());
            } else {
                List<CancelPolicy> cancelPolicyList = Lists.newArrayList();
                List<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> policyList
                        = ratePlanCancellationPolicy.getDetails();
                PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean first = policyList.get(0);
                RefundType type = cancelType(totalPrice, new BigDecimal(first.getAmount_penalty()), first.getFee_type(),first.getFee_type_value(),checkIn,checkOut);

                CancelPolicy cancelPolicyFirst = CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone(), hotelId, SupplierSourceEnum.AICHOTELS.getCode()))
                        .before(DateUtil.diffHour(formatDate(first.getDatetime()), solveCheckIn(checkIn)))
                        .type(RefundType.NO_DEDUCTION)
                        .value(0.0).build();
                cancelPolicyList.add(cancelPolicyFirst);
                if(type.equals(RefundType.NO_CANCEL)){
                    return cancelPolicyList;
                }
                if(policyList.size() == 1){
                    RefundType type1 = cancelType(totalPrice,new BigDecimal(first.getAmount_penalty()),first.getFee_type(),first.getFee_type_value(),checkIn,checkOut);
                    if(!type1.equals(RefundType.NO_CANCEL)) {
                        CancelPolicy cancelPolicyLast = CancelPolicy.builder()
                                .cancelType(1)
                                .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone(), hotelId, SupplierSourceEnum.AICHOTELS.getCode()))
                                .before(25)
                                .type(type1)
                                .value(Double.valueOf(first.getFee_type_value())).build();
                        cancelPolicyList.add(cancelPolicyLast);
                    }
                } else {
                    for (int i = 1; i < policyList.size(); i++) {
                        RefundType type1 = cancelType(totalPrice, new BigDecimal(policyList.get(i-1).getAmount_penalty()), policyList.get(i-1).getFee_type(),policyList.get(i-1).getFee_type_value(),checkIn,checkOut);
                        if(type1.equals(RefundType.NO_CANCEL)){
                            return cancelPolicyList;
                        }
                        int before = DateUtil.diffHour(formatDate(policyList.get(i).getDatetime()), solveCheckIn(checkIn));
                        CancelPolicy cancelPolicy = CancelPolicy.builder()
                                .cancelType(1)
                                .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone(), hotelId, SupplierSourceEnum.AICHOTELS.getCode()))
                                .before(before <= 24 ? 25 : before)
                                .type(type1)
                                .value(Double.valueOf(policyList.get(i-1).getFee_type_value())).build();
                        cancelPolicyList.add(cancelPolicy);
                        if(before <= 24){
                            return cancelPolicyList;
                        }
                    }
                    RefundType lastType = cancelType(totalPrice, new BigDecimal(policyList.get(policyList.size() - 1).getAmount_penalty()), policyList.get(policyList.size() - 1).getFee_type(), policyList.get(policyList.size() - 1).getFee_type_value(), checkIn, checkOut);
                    if(!lastType.equals(RefundType.NO_CANCEL)){
                        int lastBefore = DateUtil.diffHour(formatDate(policyList.get(policyList.size()-1).getDatetime()), solveCheckIn(checkIn));
                        if(!(lastBefore <= 25)){
                            CancelPolicy lastCancelPolicy = CancelPolicy.builder()
                                    .cancelType(1)
                                    .timeZone("GMT" + getTimeZone(ratePlanCancellationPolicy.getTimezone(), hotelId, SupplierSourceEnum.AICHOTELS.getCode()))
                                    .before(25)
                                    .type(lastType)
                                    .value(Double.valueOf(policyList.get(policyList.size()-1).getFee_type_value())).build();
                            cancelPolicyList.add(lastCancelPolicy);
                        }
                    }
                }
                return cancelPolicyList;
            }
        }
    }

    private void filterExcludeCXP(PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean ratePlanCancellationPolicy) {
        Iterator<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> iterator = ratePlanCancellationPolicy.getDetails().iterator();
        while (iterator.hasNext()){
            PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean next = iterator.next();
            if(!next.getPolicy_code().equals("CXP")){
                iterator.remove();
            }
        }
    }

    private void filterExcludeCXP(AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean ratePlanCancellationPolicy) {
        Iterator<AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> iterator = ratePlanCancellationPolicy.getDetails().iterator();
        while (iterator.hasNext()){
            AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean next = iterator.next();
            if(!next.getPolicy_code().equals("CXP")){
                iterator.remove();
            }
        }
    }

    public List<CancelPolicy> convertCancelPolicy(AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean ratePlanCancellationPolicy,
                                                         Date checkIn, Date checkOut, BigDecimal totalPrice, String hotelId)  {
        PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean preBookCancellationPolicy = new PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean();
        BeanUtils.copyProperties(ratePlanCancellationPolicy,preBookCancellationPolicy);
        List<AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> details = ratePlanCancellationPolicy.getDetails();
        List<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean> copyDetails = new ArrayList<>();
        for (AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean d : details) {
            PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean detailsBean = new PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.CancellationInformationBean.DetailsBean();
            BeanUtils.copyProperties(d,detailsBean);
            copyDetails.add(detailsBean);
        }
        preBookCancellationPolicy.setDetails(copyDetails);
        return convertCancelPolicy(preBookCancellationPolicy,checkIn,checkOut,totalPrice, hotelId);
    }

    public String getTimeZone(String timeZone, String hotelId, Integer supplierId) {
        if(StringUtils.isBlank(timeZone)){
            SupplierHotelInfoRequest supplierHotelRequest = new SupplierHotelInfoRequest(hotelId, supplierId);
            BaseResult<GetCityInfoBySupplierHotelIdResponse> result = hotelBaseIntlClient.getCityInfoBySupplierHotelId(supplierHotelRequest);
            return didaTravelProductConvertUtil.getTimeZoneNew(result.getData().getCityName(), result.getData().getCountryName());
        }
        String[] partRight = timeZone.split(" ");
        String[] zone = partRight[1].split(":");
        String hour = zone[0].substring(3, zone[0].length());
        return hour;
    }

    public RefundType cancelType(BigDecimal totalPrice,BigDecimal amount,String feeType,String feeTypeValue,Date checkIn,Date checkOut) {
        if(feeType.equals("percentage")) {
            return RefundType.DEDUCT_BY_PERCENT;
        } else if(feeType.equals("nights")){
            if(feeTypeValue.equals(DateUtil.diff(checkIn, checkOut) + "")){
                return RefundType.NO_CANCEL;
            }
            return RefundType.DEDUCT_DAY_NIGHT;
        } else if(feeType.equals("price")) {
            if(totalPrice.subtract(amount).compareTo(BigDecimal.ZERO) == 0) {
                return RefundType.NO_CANCEL;
            }
            return RefundType.DEDUCT_BY_AMOUNT;
        } else {
            return RefundType.NO_CANCEL;
        }
    }

    public Date solveCheckIn(Date checkIn){
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

    public Date formatDate(String datetime) {
        // 定义日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        // 解析 LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.parse(datetime, formatter);
        // 转换为 Date
        Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        return date;
    }

    public List<PriceInfo> buildPriceInfos(List<AvailabilityResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean> ratesBeans) {
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

//    public List<PriceInfo> buildPriceInfos(String checkIn, String checkOut, double price) {
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

    public List<PriceInfo> buildPriceInfosCheck(List<PreBookResponse.RoomListBean.RatesAndCancellationPoliciesBean.RatesBean> ratesBeans, String checkIn, String checkOut) {
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
