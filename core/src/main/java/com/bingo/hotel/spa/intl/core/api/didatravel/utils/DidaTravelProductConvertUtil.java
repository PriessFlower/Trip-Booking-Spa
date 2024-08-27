package com.bingo.hotel.spa.intl.core.api.didatravel.utils;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.request.SupplierHotelInfoRequest;
import com.bingo.hotel.base.intl.cli.request.SupplierHotelRequest;
import com.bingo.hotel.base.intl.cli.response.HotelMappingResponse;
import com.bingo.hotel.base.intl.cli.result.BaseResult;
import com.bingo.hotel.spa.intl.cli.dto.*;
import com.bingo.hotel.spa.intl.cli.enums.RefundType;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.test.DataRecord;
import com.bingo.hotel.spa.intl.core.test.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.DateFormatUtils;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import org.apache.commons.compress.utils.Lists;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.parseInt;

@Component
public class DidaTravelProductConvertUtil {

    @Autowired
    private HotelBaseIntlClient hotelBaseIntlClient;

    public List<ProductRespDTO> convertRatePlanVO(DidaTravelResponse didaTravelResponse) {
        List<ProductRespDTO> respDTOList = Lists.newArrayList();
        didaTravelResponse.getSuccess().getPriceDetails().getHotelList().forEach(hotelType -> {
            SupplierHotelInfoRequest supplierHotelRequest = new SupplierHotelInfoRequest(hotelType.getHotelID().toString(), SupplierSourceEnum.DIDATRAVEL.getCode());
            BaseResult<HotelMappingResponse> result = hotelBaseIntlClient.queryHotelMappingBySupplierHotelId(supplierHotelRequest);

            DataRecord cityInfo = getCityInfo(result.getData().getCityName());//TODO: 这里应该是根据城市获取时区，现在默认是Bali
            String timeZone = getTimeZone(cityInfo.getUrl());
            ZoneOffset offset = ZoneOffset.ofHours(parseInt(timeZone));
            // 获取当前时间在该偏移量下
            OffsetDateTime currentTime = OffsetDateTime.now(offset);

            // 格式化输出
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
            String formattedTime = currentTime.format(formatter);

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

                ratePlan.getRatePlanCancellationPolicyList().sort((map1, map2) -> {
                    Date date1 = map1.getFromDate();
                    Date date2 = map2.getFromDate();
                    return date1.compareTo(date2);
                });
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
                        .maxOccupancy(null == ratePlan.getMaxOccupancy() ? 0 : ratePlan.getMaxOccupancy())
                        .build();
                respDTOList.add(build);
            });
        });
        return respDTOList;
    }

    public List<CancelPolicy> convertCancelPolicy(List<DidaTravelResponse.CancellationPolicyListTypeCancellationPolicy>
                                                                 ratePlanCancellationPolicyList, Date checkIn, BigDecimal totalPrice, String timeZone)  {

        // 如果道旅给的取消规则是null，则表示不可取消
        if(CollectionUtils.isEmpty(ratePlanCancellationPolicyList)){
            return List.of(CancelPolicy.builder().cancelType(0).build());
        }
        // 根据取消时间从小到大排序
        Collections.sort(ratePlanCancellationPolicyList, (map1, map2) -> map1.getFromDate().compareTo(map2.getFromDate()));

        // 取出第一条数据，如果第一条数据checkIn - formDate <= 24小时,则直接改为25
        DidaTravelResponse.CancellationPolicyListTypeCancellationPolicy policyOne = ratePlanCancellationPolicyList.get(0);
        int hour = DateUtil.diffHour(policyOne.getFromDate(), solveCheckIn(checkIn));
        if(hour <= 24){
            BigDecimal amount = policyOne.getAmount();

            return List.of(CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone("GMT" + timeZone)
                    .before(25)
                    .type(totalPrice.subtract(amount).equals(0) ? RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                    .value(amount.doubleValue()).build());
        } else {
            List<CancelPolicy> cancelPolicyList = Lists.newArrayList();
            for (DidaTravelResponse.CancellationPolicyListTypeCancellationPolicy policy : ratePlanCancellationPolicyList) {
                BigDecimal amount = policy.getAmount();
                CancelPolicy cancelPolicy = CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone("GMT" + timeZone)
                        .before(DateUtil.diffHour( policy.getFromDate(), solveCheckIn(checkIn)))
                        .type(totalPrice.subtract(amount).equals(0) ? RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                        .value(amount.doubleValue()).build();
                cancelPolicyList.add(cancelPolicy);
            }
            return cancelPolicyList;
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

    public static void main(String[] args) throws ParseException {
//        String checkIn = "2024-08-24 00:00:00";
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        Date parse = sdf.parse(checkIn);
//        String fromDate = "2024-08-24 00:00:00";
//        Date parse1 = sdf.parse(fromDate);
//        System.out.println(parse.before(parse1));
//        DataRecord medan = getCityInfo("Bali");
//        String timeZone = getTimeZone(medan.getUrl());

    }
    public List<PriceInfo> buildPriceInfos(List<DidaTravelResponse.HotelTypeRatePlanPriceInfo> hotelTypeRatePlanPriceInfo ) {
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
        String html = HttpUtils.sendGet("https://www.timeanddate.com"+ url);
        Document doc = Jsoup.parse(html);
        // table table--left table--inner-borders-rows
        Elements tables = doc.select("table.table.table--left.table--inner-borders-rows");
        Element table = tables.get(0);
        // 获取表格的行
        Elements rows = table.select("tr");
        Element tr = rows.get(1);
        Element td = tr.selectFirst("td");
        String text = td.text();
        String[] s = text.split(" ");
        System.out.println(s[1]);
        return s[1];
    }

    /*
    * 根据城市名称获取城市信息
    * */
    public DataRecord getCityInfo(String cityName) {
        long startTime = System.currentTimeMillis();
        String input = HttpUtils.sendGet("https://www.timeanddate.com/scripts/completion.php?query=" + cityName + "&xd=3&mode=ci");
        List<DataRecord> records = parseData(input);
        for (DataRecord record : records) {
            if(record.getName().contains(cityName)) {
                return record;
            }
        }
        long endTime = System.currentTimeMillis();
        System.out.println("time = " + (endTime - startTime));
        return null;
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

