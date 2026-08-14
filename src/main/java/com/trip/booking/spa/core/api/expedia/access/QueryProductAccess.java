package com.trip.booking.spa.core.api.expedia.access;

import com.trip.booking.spa.core.api.common.access.BaseHttpAccess;
import com.trip.booking.spa.core.api.common.asynchttp.IParser;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.asynchttp.SupplierApiConstants;
import com.trip.booking.spa.core.api.common.enums.MonitorNameEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierDataTypeEnum;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.exception.ParseException;
import com.trip.booking.spa.core.api.expedia.bean.request.QueryPriceRequest;
import com.trip.booking.spa.core.api.expedia.bean.response.HotelStaticInfo;
import com.trip.booking.spa.core.api.expedia.bean.response.QueryPriceResponse;
import com.trip.booking.spa.core.monitor.Monitor;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.trip.booking.spa.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
public class QueryProductAccess extends BaseHttpAccess<QueryPriceRequest, QueryPriceResponse> {
    private String host;

    private String language;

    private String authorization;

    private String customerIp;

    private String customerSessionId;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/v3/properties/availability";

    private static int QPS = 500;


    public QueryProductAccess(String host, String language, String authorization, String customerIp, String customerSessionId, DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, 0);
        this.host = host;
        this.language = language;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
        this.redisRateLimiter = redisRateLimiter;
    }


    @Override
    protected ResponseResult<QueryPriceResponse> request(String url, QueryPriceRequest request, IParser<QueryPriceResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Customer-Session-Id", customerSessionId);
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("property_id", request.getProperty_id());
        body.put("language", language);
        body.put("checkin", request.getCheckin());
        body.put("checkout", request.getCheckout());
        body.put("country_code", "CN");
        body.put("currency", request.getCurrency());
        StringBuilder occupancyStr = new StringBuilder();
        request.getOccupancies().forEach(occupancy -> {
            if (StringUtils.isNotBlank(occupancyStr)) {
                occupancyStr.append("&");
            }
            occupancyStr.append("occupancy=").append(occupancy);
        });
        body.put("", occupancyStr.toString());
        body.put("rate_plan_count", request.getRate_plan_count());
        body.put("sales_channel", request.getSales_channel());
        body.put("sales_environment", request.getSales_environment());
        body.put("billing_terms", request.getBilling_terms());
        body.put("payment_terms", request.getPayment_terms());
        body.put("partner_point_of_sale", request.getPartner_point_of_sale());
        long start = System.currentTimeMillis();
        String result = HttpUtils.doGet(url, headers, body);
        Monitor.recordOne("EXPEDIA_ORIGINAL_QUERY",
                System.currentTimeMillis() - start);
        List<QueryPriceResponse.HotelPrice> hotelPrices = JsonUtils.decodeJson(result, new TypeReference<List<QueryPriceResponse.HotelPrice>>() {
        });
        QueryPriceResponse queryPriceResponse = new QueryPriceResponse();
        queryPriceResponse.setHotelPrices(hotelPrices);
        return new ResponseResult<>(queryPriceResponse);
    }

    @Override
    protected void beforeAccess(QueryPriceRequest request) {
        // 限流已统一上移至 BaseHttpAccess.access()（RateLimitManager），此处仅保留业务前置钩子
    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH;
    }

    @Override
    protected QueryPriceResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, QueryPriceResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
