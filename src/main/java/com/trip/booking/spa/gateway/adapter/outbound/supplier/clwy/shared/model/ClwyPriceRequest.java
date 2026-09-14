package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 报价／试单请求体（官方 05-product-price，2026-09-14 查阅）。<b>同一个端点两种用法</b>：
 * 不传 {@code RatePlanId} 是报价，传了就是「预定页试单」并回 {@code RateKey}。
 *
 * <p>三个口径坑，都写在官方参数表里但极易看漏：
 * <ul>
 *   <li>{@code AdultCount}／{@code ChildCount}／{@code ChildAges} 是<b>每间房</b>的人数，不是总数</li>
 *   <li>{@code CountryCode} 客人国籍「报价、验价、下单该值必须保持一致」——不一致可能拿到另一套价</li>
 *   <li>{@code Tag}：验价若回了非 null 非 0 的 Tag，后续必须原样带回</li>
 * </ul>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClwyPriceRequest {

    @JsonProperty("HotelId")
    private String hotelId;

    /** 传了即为试单（回 RateKey），不传为报价 */
    @JsonProperty("RatePlanId")
    private String ratePlanId;

    /** yyyy-MM-dd */
    @JsonProperty("CheckInDate")
    private String checkInDate;

    @JsonProperty("CheckOutDate")
    private String checkOutDate;

    @JsonProperty("RoomCount")
    private Integer roomCount;

    /** <b>每间房</b>成人数 */
    @JsonProperty("AdultCount")
    private Integer adultCount;

    /** <b>每间房</b>儿童数 */
    @JsonProperty("ChildCount")
    private Integer childCount;

    /** <b>每间房</b>儿童年龄 */
    @JsonProperty("ChildAges")
    private List<Integer> childAges;

    /** 客人国籍（2 位 ISO 码）；报价/验价/下单必须一致 */
    @JsonProperty("CountryCode")
    private String countryCode;

    /** 验价回了非 null 非 0 就要带回，否则不传 */
    @JsonProperty("Tag")
    private Integer tag;
}
