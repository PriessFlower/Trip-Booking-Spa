package com.bingo.hotel.spa.intl.core.api.expedia.access;

import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.exception.ParseException;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.RegionsRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.RegionsInfoResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ExpediaUtils;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.HttpUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class RegionsAccess extends BaseHttpAccess<RegionsRequest, RegionsInfoResponse> {
    private String host;

    private String language;

    private String authorization;

    private String customerIp;

    private String customerSessionId;

    private String regionId;

    private DistributedRateLimiter redisRateLimiter;

    private final static String PATH = "/regions/";

    private static int QPS = 30;

    public RegionsAccess(String host, String language, String authorization, String customerIp, String customerSessionId, String regionId,
                         DistributedRateLimiter redisRateLimiter) {
        super(SupplierSourceEnum.EXPEDIA, SupplierDataTypeEnum.STATIC_DATA, MonitorNameEnum.SPA_SUPPLIER_API_COUNTRY, 0);
        this.host = host;
        this.language = language;
        this.authorization = authorization;
        this.customerIp = customerIp;
        this.customerSessionId = customerSessionId;
        this.regionId = regionId;
        this.redisRateLimiter = redisRateLimiter;
    }


    @Override
    protected ResponseResult<RegionsInfoResponse> request(String url, RegionsRequest request, IParser<RegionsInfoResponse> parser) throws Exception {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("Authorization", authorization);
        headers.put("Customer-Ip", customerIp);
        headers.put("Content-Type", "application/json");
        Map<String, String> body = Maps.newHashMap();
        body.put("language", language);
        body.put("include", request.getInclude());
        if (StringUtils.isNotBlank(request.getType())) {
            body.put("type", request.getType());
        }
        ResponseResult<RegionsInfoResponse> result = HttpUtils.accessGet(url, headers, body, parser);
        return result;
    }

    @Override
    protected void beforeAccess(RegionsRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return host + PATH + regionId;
    }

    @Override
    protected RegionsInfoResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, RegionsInfoResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    public static void main(String[] args) {
        String str = "{\"id\":\"11702\",\"type\":\"country\",\"name\":\"布威岛\",\"name_full\":\"布威岛\",\"country_code\":\"BV\",\"coordinates\":{\"center_longitude\":3.355294,\"center_latitude\":-54.42098,\"bounding_polygon\":{\"type\":\"Polygon\",\"coordinates\":[[[3.227638,-54.404989],[3.306656,-54.36826],[3.422167,-54.376292],[3.49236,-54.418697],[3.436325,-54.465791],[3.304146,-54.470502],[3.25711,-54.456856],[3.227638,-54.404989]]]}},\"associations\":{\"point_of_interest\":[]},\"ancestors\":[{\"id\":\"11700\",\"type\":\"continent\"}],\"descendants\":{\"city\":[\"553248635974516106\"]},\"categories\":[\"place:administrative\",\"place:tourism\",\"administrative:country\",\"placeOfInterest:nature\",\"tourism:region\",\"tourism:placeOfInterest\"],\"tags\":[\"naturalFeature:island\",\"geo-admin:country\"]}";
        try {
            System.out.println(JsonUtils.writeObject2Json(JsonUtils.readValue(str, RegionsInfoResponse.class)));
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

//    public static void main(String[] args) {
//        Map<String, String> headers = Maps.newHashMap();
//        headers.put("Authorization", new ExpediaUtils().signGeneration());
//        headers.put("Customer-Ip", "5.5.5.5");
//        headers.put("Content-Type", "application/json");
//        Map<String, String> body = Maps.newHashMap();
//        body.put("language", "en-US");
//        body.put("include", "details");
//
//        ResponseResult<RegionsInfoResponse> result = null;
//        try {
//            result = HttpUtils.accessGet("https://test.ean.com/v3/regions/11700", headers, body, null);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        System.out.println(JsonUtils.writeObject2Json(result));
//    }
}
