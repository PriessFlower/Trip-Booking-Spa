package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * hotel.detail（查价）请求体（信封的 Request 部分）。
 *
 * <p><b>逐店纪律</b>：协议上 HotelIds 允许逗号拼接最多 10 家，但混批下艺龙对部分
 * 酒店返回 Code=0 且 Rooms=[] 的<b>假空</b>（cursor 越南酒店实测），会被误当真实
 * 无房清价。故本请求只承载单店，构造入口不提供多店拼接。
 */
@Getter
@Setter
@Builder
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElongHotelDetailRequest {

    /** yyyy-MM-dd */
    private String arrivalDate;

    /** yyyy-MM-dd */
    private String departureDate;

    /** 单店（逐店纪律，见类 javadoc） */
    private String hotelIds;

    /** 只做预付产品 */
    @Builder.Default
    private String paymentType = "Prepay";

    @Builder.Default
    private String invoiceMode = "Elong";

    /**
     * 让艺龙为本次会话签发促销马甲（littleMajiaId）——国际/港澳台验价、下单必需。
     * 马甲是会话级易腐凭证（SupplierIdentityProfile.ELONG），只随本次响应流转，
     * 绝不落库（R-2.1）。
     */
    @Builder.Default
    private Boolean saveMajiaId = true;

    /** 响应内容选项，cursor 主路取值 "1,2,4,12,13"（房型/产品/价格/餐食/取消规则） */
    @Builder.Default
    private String options = "1,2,4,12,13";

    private Integer numberOfAdults;

    private List<Integer> childAges;

    private Integer numberOfRooms;

    /** 客人国籍，影响可售与价格 */
    @Builder.Default
    private List<String> nat = List.of("CN");

    @Builder.Default
    private Integer isNeedAdditionalTaxText = 1;
}
