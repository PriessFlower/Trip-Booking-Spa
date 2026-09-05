package com.trip.booking.spa.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * bff 包私有的 trip-booking-agg 检索通道。
 *
 * <p>为什么要走它：搜索框联想原本是 {@code expedia_property_content.name LIKE '%关键词%'}，
 * 中文名整串扫表、没有分词，「素坤逸希尔顿」这类跨词输入一条都搜不出来。agg 那边
 * 索引了 93,930 家 canonical 酒店（IK 中文分词 + cjk 二元组），93,930 家全量实测下
 * 「素坤逸」精确命中 206 家。
 *
 * <p>agg 与 spa 同机（trip-offline），spa 容器是 {@code --network host}，所以默认走
 * {@code 127.0.0.1:18080}，不经任何网络设备。
 *
 * <p>⚠️ 必须带 {@code supplier=expedia}：agg 的库里有大量只挂了 elong / huizhi 的店，
 * 而本 BFF 整条定价链走的是 Expedia Rapid。不过滤就会搜出<b>点得进去却报不出价</b>的店，
 * 比搜不到更糟。
 */
@Slf4j
@Component
public class AggSearchGateway {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BffProperties bffProperties;
    private final HttpClient httpClient;

    public AggSearchGateway(BffProperties bffProperties) {
        this.bffProperties = bffProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(bffProperties.getSuggest().getTimeoutMs()))
                .build();
    }

    /**
     * 按关键词联想酒店。
     *
     * @return 命中列表；agg 不可用时返回 {@code null}，由调用方决定退回旧路径。
     *         这里不抛异常——搜索框是验收站的门面，宁可给一份差一点的结果，也不能整个报错。
     */
    public List<Hotel> suggestHotels(String keyword, String language, int limit) {
        BffProperties.Suggest cfg = bffProperties.getSuggest();
        String url = cfg.getBaseUrl() + "/api/search/hotels"
                + "?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&lang=" + URLEncoder.encode(language, StandardCharsets.UTF_8)
                + "&supplier=" + URLEncoder.encode(cfg.getSupplier(), StandardCharsets.UTF_8)
                + "&size=" + limit;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("[bff-suggest] agg 返回 {}，退回本地 LIKE 查询", response.statusCode());
                return null;
            }
            return parse(response.body(), cfg.getSupplier());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("[bff-suggest] agg 调用失败（{}），退回本地 LIKE 查询", e.toString());
            return null;
        }
    }

    /** 只留下拿得到 Expedia property_id 的命中——拿不到的，下游定价链根本走不通。 */
    static List<Hotel> parse(String body, String supplier) throws Exception {
        List<Hotel> hotels = new ArrayList<>();
        for (JsonNode hit : MAPPER.readTree(body)) {
            String propertyId = null;
            for (JsonNode ref : hit.path("supplierRefs")) {
                if (supplier.equals(ref.path("code").asText())) {
                    propertyId = ref.path("hotelId").asText(null);
                    break;
                }
            }
            if (propertyId == null) {
                continue;
            }
            hotels.add(new Hotel(propertyId,
                    hit.path("name").asText(null),
                    hit.path("cityName").asText(null),
                    hit.path("countryCode").asText(null),
                    hit.hasNonNull("starRating") ? hit.path("starRating").asDouble() : null));
        }
        return hotels;
    }

    /** 与旧的 {@code suggestProperties} 返回的列一一对应，好让上层不用改形状。 */
    public record Hotel(String propertyId, String name, String city, String countryCode, Double starRating) {
    }
}
