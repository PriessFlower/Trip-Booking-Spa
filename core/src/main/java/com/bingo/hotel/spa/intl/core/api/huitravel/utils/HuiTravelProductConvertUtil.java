package com.bingo.hotel.spa.intl.core.api.huitravel.utils;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.request.SupplierHotelInfoRequest;
import com.bingo.hotel.base.intl.cli.response.GetCityInfoBySupplierHotelIdResponse;
import com.bingo.hotel.base.intl.cli.result.BaseResult;
import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.enums.RefundType;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.mapper.InitTimeZoneMapper;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.NightlyRate;
import com.bingo.hotel.spa.intl.core.api.model.DataRecord;
import com.bingo.hotel.spa.intl.core.api.service.impl.InitTimeZoneServiceImpl;
import com.bingo.hotel.spa.intl.core.redis.RedisUtils;
import com.bingo.hotel.spa.intl.core.util.DateFormatUtils;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.bingo.hotel.spa.intl.core.util.ZoneHttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static java.lang.Integer.parseInt;

@Component
@Slf4j
public class HuiTravelProductConvertUtil {
    @Autowired
    private HotelBaseIntlClient hotelBaseIntlClient;
    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private InitTimeZoneMapper initTimeZoneMapper;

    public List<ProductRespDTO> convertRatePlanVO(AvailabilityResponse availabilityResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
//        SupplierHotelInfoRequest supplierHotelRequest = new SupplierHotelInfoRequest(availabilityResponse.getResult().getPrices().get(0).getHid().toString(), SupplierSourceEnum.HUITRAVEL.getCode());
//        BaseResult<GetCityInfoBySupplierHotelIdResponse> result = hotelBaseIntlClient.getCityInfoBySupplierHotelId(supplierHotelRequest);
//        String timeZone = getTimeZone(result.getData().getCityName(), result.getData().getCountryName());
        availabilityResponse.getResult().getPrices().forEach(productVO -> respDTOList.add(ProductRespDTO.builder()
                        .productId(productVO.getRpid() + "")
//                        .currencyType(productVO.getCurrency())
                        .supplierId(SupplierSourceEnum.HUITRAVEL.getCode())
                        .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(productVO.getName()).build())
                        .totalPrice(productVO.getNightlyrate().stream()
                                .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal(100)).intValue())
                        .hotelId(productVO.getHid() + "")
//                        .cancelPolicy(convertCancelPolicy(productVO.getNew_cancel_policy(), DateUtil.getDate(productVO.getCheckin()), timeZone))
                        .priceInfos(buildPriceInfos(productVO.getNightlyrate()))
                        .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                        .maxOccupancy(productVO.getMax_occupancy())
                        .build())
        );
        return respDTOList;
    }

    public List<ProductRespDTO> convertRatePlanCheckVO(AvailabilityResponse availabilityResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        BigDecimal totalPrice = availabilityResponse.getCheckResponse().getResult().getNightlyrate().stream()
                .map(NightlyRate::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
//        SupplierHotelInfoRequest supplierHotelRequest = new SupplierHotelInfoRequest(availabilityResponse.getResult().getPrices().get(0).getHid().toString(), SupplierSourceEnum.HUITRAVEL.getCode());
//        BaseResult<GetCityInfoBySupplierHotelIdResponse> result = hotelBaseIntlClient.getCityInfoBySupplierHotelId(supplierHotelRequest);
//        String timeZone = getTimeZone(result.getData().getCityName(), result.getData().getCountryName());
        availabilityResponse.getResult().getPrices().forEach(productVO -> respDTOList.add(ProductRespDTO.builder()
                        .productId(productVO.getRpid() + "")
//                        .currencyType(productVO.getCurrency())
                        .supplierId(SupplierSourceEnum.HUITRAVEL.getCode())
                        .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(productVO.getName()).build())
                        .totalPrice(totalPrice.multiply(new BigDecimal(100)).intValue())
                        .hotelId(productVO.getHid() + "")
//                        .cancelPolicy(convertCancelPolicy(productVO.getNew_cancel_policy(), DateUtil.getDate(productVO.getCheckin()), timeZone))
                        .priceInfos(buildPriceInfos(availabilityResponse.getCheckResponse().getResult().getNightlyrate()))
                        .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                        .maxOccupancy(productVO.getMax_occupancy())
                        .build())
        );
        return respDTOList;
    }

    public List<PriceInfo> buildPriceInfos(List<NightlyRate> nightlyrates) {
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

    /*
     * 获取时区
     * */
    public String getTimeZone(String url) {
        String html = ZoneHttpUtils.sendGet("https://www.timeanddate.com" + url);
        Document doc = Jsoup.parse(html);
        // table table--left table--inner-borders-rows
        Elements tables = doc.select("table.table.table--left.table--inner-borders-rows");
        Element table = tables.get(0);
        // 获取表格的行
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Elements th = row.select("th");
            if (th.text().equals("Current Offset:")) {
                Elements td = row.select("td");
                String text = td.text();
                String[] s = text.split(" ");
                return s[1];
            }
        }
        log.error("获取时区失败, url: {}", url);
        return null;
    }

    public String getTimeZone(String cityName, String countryName) {
        String timeZone = redisUtils.hmGet(InitTimeZoneServiceImpl.TIME_ZONE_KEY_PREFIX + cityName, countryName);
        if (StringUtils.isBlank(timeZone)) {
            timeZone = initTimeZoneMapper.getCityZoneByCityName(cityName, countryName);
            if (StringUtils.isBlank(timeZone)) {
                DataRecord cityInfo = getCityInfo(cityName, countryName);
                if (cityInfo != null) {
                    timeZone = getTimeZone(cityInfo.getUrl());
                    if (StringUtils.isNotBlank(timeZone)) {
                        redisUtils.hmSet(InitTimeZoneServiceImpl.TIME_ZONE_KEY_PREFIX + cityName, countryName, timeZone);
                    }
                } else {
                    timeZone = "";
                }
            }
        }
        return timeZone;
    }

    /*
     * 根据城市名称获取城市信息
     * */
    public DataRecord getCityInfo(String cityName, String countryName) {
        countryName = sloveCountryName(countryName);
        String input = ZoneHttpUtils.sendGet("https://www.timeanddate.com/scripts/completion.php?query=" + cityName + "&xd=3&mode=ci");
        List<DataRecord> records = parseData(input);
        for (DataRecord record : records) {
            if (record.getName().contains(cityName) && record.getCountry().contains(countryName)) {
                return record;
            }
        }
        log.error("未找到城市信息: cityName:{},countryName:{}", cityName, countryName);
        return null;
    }

    private String sloveCountryName(String countryName) {
        if (countryName.equals("United States of America")) {
            return "USA";
        } else if (countryName.equals("The United Kingdom of Great Britain and Northern Ireland")) {
            return "United Kingdom";
        }
        return countryName;
    }

    public List<DataRecord> parseData(String input) {
        List<DataRecord> records = new ArrayList<>();
        String[] lines = input.split("\n");
        for (String line : lines) {
            String[] parts = line.split("\t");

            if (parts.length < 11) {
                continue;  // 如果数据不完整则跳过
            }

            DataRecord record = new DataRecord();
            record.setUrl(parts[0]);
            record.setCode(parseInt(parts[1]));
            record.setCountryCode(parts[2]);
            record.setRegionCode(parts[3]);
            record.setName(parts[4]);
            record.setProvince(parts[5]);
            record.setCountry(parts[6]);
            record.setImageUrl(parts[7]);
//            record.location = parts[8];
//            record.value = Double.parseDouble(parts[9]);
//            record.type = parts[10];

            records.add(record);
        }

        return records;
    }

    public List<CancelPolicy> convertCancelPolicy(String policyDateTime, Date checkIn, String timeZone) {

        if (StringUtils.isBlank(policyDateTime) || StringUtils.isBlank(timeZone)) {
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        Date fromDate = DateUtil.getDateTime(policyDateTime);
        String firstDate = convertToOffsetTime(fromDate, timeZone);
        String fisrtCheckIn = convertToOffsetTime(solveCheckIn(checkIn), timeZone);
        int hour = DateUtil.diffHour(DateFormatUtils.parse4y2M2d2h2m2s(firstDate), DateFormatUtils.parse4y2M2d2h2m2s(fisrtCheckIn));
        if (hour <= 24) {
            return List.of(CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone("GMT" + timeZone)
                    .before(25)
                    .type(RefundType.NO_DEDUCTION)
                    .value(0.0).build());
        } else {
            List<CancelPolicy> cancelPolicyList = Lists.newArrayList();
            CancelPolicy cancelPolicyFirst = CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone("GMT" + timeZone)
                    .before(DateUtil.diffHour(fromDate, solveCheckIn(checkIn)))
                    .type(RefundType.NO_DEDUCTION)
                    .value(0.0).build();
            cancelPolicyList.add(cancelPolicyFirst);
            return cancelPolicyList;
        }
    }

    public RefundType getRefundType(BigDecimal totalPrice, BigDecimal amount) {
        if (totalPrice.subtract(amount).compareTo(BigDecimal.ZERO) == 0) {
            return RefundType.NO_DEDUCTION;
        }
        return RefundType.DEDUCT_BY_AMOUNT;
    }

    public String convertToOffsetTime(Date date, String offset) {
        try {
            // 创建 Date 转 LocalDateTime
            Instant instant = date.toInstant();
            LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

            // 创建 GMT 偏移量的 ZoneId
            ZoneId zoneId = ZoneId.of(offset);

            // 创建 LocalDateTime 到 ZonedDateTime 的转换
            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(zoneId);

            // 格式化输出
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return zonedDateTime.format(formatter);

        } catch (DateTimeException e) {
            return "错误: " + e.getMessage();
        }
    }

    public Date solveCheckIn(Date checkIn) {
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
