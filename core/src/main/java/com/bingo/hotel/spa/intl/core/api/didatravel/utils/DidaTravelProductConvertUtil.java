package com.bingo.hotel.spa.intl.core.api.didatravel.utils;

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
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.mapper.InitTimeZoneMapper;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
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
public class DidaTravelProductConvertUtil {

    @Autowired
    private HotelBaseIntlClient hotelBaseIntlClient;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private InitTimeZoneMapper initTimeZoneMapper;

    public List<ProductRespDTO> convertRatePlanVO(DidaTravelResponse didaTravelResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        for (DidaTravelResponse.HotelType hotelType : didaTravelResponse.getSuccess().getPriceDetails().getHotelList()) {
            SupplierHotelInfoRequest supplierHotelRequest = new SupplierHotelInfoRequest(hotelType.getHotelID().toString(), SupplierSourceEnum.DIDATRAVEL.getCode());
            BaseResult<GetCityInfoBySupplierHotelIdResponse> result = hotelBaseIntlClient.getCityInfoBySupplierHotelId(supplierHotelRequest);
            String timeZone = getTimeZone(result.getData().getCityName(), result.getData().getCountryName());
            for (DidaTravelResponse.HotelTypeRatePlan ratePlan : hotelType.getRatePlanList()) {
                ProductInfo productInfo = ProductInfo.builder()
                        .inventory(ratePlan.getRoomOccupancy().getRoomNum())
                        .productStatus(1)
                        .productName(ratePlan.getRatePlanName())
                        .build();

                Room room = Room.builder()
                        .roomId(String.valueOf(ratePlan.getRoomTypeID()))
                        .roomName(ratePlan.getRoomName())
                        .build();


                List<CancelPolicy> cancelPolicies
                        = convertCancelPolicy(ratePlan.getRatePlanCancellationPolicyList(),
                        didaTravelResponse.getSuccess().getPriceDetails().getCheckInDate(), ratePlan.getTotalPrice(), timeZone);

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
                        .cancelPolicy(cancelPolicies)
//                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                        .maxOccupancy(null == ratePlan.getMaxOccupancy() ? 0 : ratePlan.getMaxOccupancy())
                        .build();
                respDTOList.add(build);
            }
        }
        return respDTOList;
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

    public List<CancelPolicy> convertCancelPolicy(List<DidaTravelResponse.CancellationPolicyListTypeCancellationPolicy>
                                                          policyList, Date checkIn, BigDecimal totalPrice, String timeZone) {

        // 如果道旅给的取消规则是null，则表示不可取消
        if (CollectionUtils.isEmpty(policyList) || StringUtils.isBlank(timeZone)) {
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        // 根据取消时间从小到大排序
        Collections.sort(policyList, (map1, map2) -> map1.getFromDate().compareTo(map2.getFromDate()));

        // 取出第一条数据，如果第一条数据checkIn - formDate <= 24小时,则直接改为25
        DidaTravelResponse.CancellationPolicyListTypeCancellationPolicy policyOne = policyList.get(0);
        int day = DateFormatUtils.diff(new Date(), policyOne.getFromDate());
        if (day <= 0) {
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        String firstDate = convertToOffsetTime(policyOne.getFromDate(), timeZone);
        String fisrtCheckIn = convertToOffsetTime(solveCheckIn(checkIn), timeZone);
        int hour = DateUtil.diffHour(DateFormatUtils.parse4y2M2d2h2m2s(firstDate), DateFormatUtils.parse4y2M2d2h2m2s(fisrtCheckIn));
        if (hour <= 24) {
            BigDecimal amount = policyOne.getAmount();
            RefundType type = getRefundType(totalPrice, amount);
            if (type.equals(RefundType.NO_CANCEL)) {
                return List.of(CancelPolicy.builder().cancelType(0).build());
            }
            return List.of(CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone("GMT" + timeZone)
                    .before(25)
                    .type(type)
                    .value(amount.doubleValue()).build());
        } else {
            List<CancelPolicy> cancelPolicyList = Lists.newArrayList();
            DidaTravelResponse.CancellationPolicyListTypeCancellationPolicy firstPolicy = policyList.get(0);
            RefundType firstType = getRefundType(totalPrice, firstPolicy.getAmount());
            if (firstType.equals(RefundType.NO_CANCEL)) {
                return List.of(CancelPolicy.builder().cancelType(0).build());
            }
            CancelPolicy cancelPolicyFirst = CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone("GMT" + timeZone)
                    .before(DateUtil.diffHour(firstPolicy.getFromDate(), solveCheckIn(checkIn)))
                    .type(RefundType.NO_DEDUCTION)
                    .value(0.0).build();
            cancelPolicyList.add(cancelPolicyFirst);
//            if(policyList.size() == 1){
//                RefundType type1 = getRefundType(totalPrice, firstPolicy.getAmount());
//                if(!type1.equals(RefundType.NO_CANCEL)) {
//                    CancelPolicy cancelPolicyLast = CancelPolicy.builder()
//                            .cancelType(1)
//                            .timeZone("GMT" + timeZone)
//                            .before(25)
//                            .type(type1)
//                            .value(firstPolicy.getAmount().doubleValue()).build();
//                    cancelPolicyList.add(cancelPolicyLast);
//                }
//            } else {
//                for (int i = 1; i < policyList.size(); i++) {
//                    RefundType type1 = getRefundType(totalPrice, policyList.get(i-1).getAmount());
//                    if(type1.equals(RefundType.NO_CANCEL)){
//                        return cancelPolicyList;
//                    }
//                    int before = DateUtil.diffHour(policyList.get(i).getFromDate(), solveCheckIn(checkIn));
//                    CancelPolicy cancelPolicy = CancelPolicy.builder()
//                            .cancelType(1)
//                            .timeZone("GMT" + timeZone)
//                            .before(before <= 24 ? 25 : before)
//                            .type(type1)
//                            .value(policyList.get(i-1).getAmount().doubleValue()).build();
//                    cancelPolicyList.add(cancelPolicy);
//                    if(before <= 24){
//                        return cancelPolicyList;
//                    }
//                }
//                RefundType lastType = getRefundType(totalPrice,policyList.get(policyList.size()-1).getAmount());
//                if(!lastType.equals(RefundType.NO_CANCEL)){
//                    int lastBefore = DateUtil.diffHour(policyList.get(policyList.size() -1).getFromDate(), solveCheckIn(checkIn));
//                    if(!(lastBefore <= 25)){
//                        CancelPolicy lastCancelPolicy = CancelPolicy.builder()
//                                .cancelType(1)
//                                .timeZone("GMT" + timeZone)
//                                .before(25)
//                                .type(lastType)
//                                .value(policyList.get(policyList.size()-1).getAmount().doubleValue()).build();
//                        cancelPolicyList.add(lastCancelPolicy);
//                    }
//                }
//            }
            return cancelPolicyList;
        }
    }

    public RefundType getRefundType(BigDecimal totalPrice, BigDecimal amount) {
        if (totalPrice.subtract(amount).compareTo(BigDecimal.ZERO) == 0) {
            return RefundType.NO_DEDUCTION;
        }
        return RefundType.DEDUCT_BY_AMOUNT;
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

    public List<PriceInfo> buildPriceInfos(List<DidaTravelResponse.HotelTypeRatePlanPriceInfo> hotelTypeRatePlanPriceInfo) {
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
}

