package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量查价请求体（官方 hotel.oversea.batch.goods.rp，2026-09-14 查阅）。
 *
 * <p><b>没有间数字段</b>——报价档一律按单间报，间数只在下单前校验（order.check）里才有。
 * 这决定了本仓的总价口径，见 {@code MeituanPriceServiceImpl} 的 B4 说明。
 *
 * <p>{@code hotelIds} 是列表，即本家<b>支持合批</b>。本仓目前仍逐店一次：美团可卖清单
 * 2026-09-14 实测只有 847 家（poi.list 全量翻页去重后），逐店刷跑得起，没有为省配额而
 * 牺牲"一行任务 = 一次调用"这个可归因性的必要。清单显著变大时再议。
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeituanBatchGoodsRequest {

    private List<Long> hotelIds;

    /** yyyy-MM-dd */
    private String checkinDate;

    private String checkoutDate;

    /** <b>每间房</b>成人数 */
    private Integer numberOfAdults;

    /** <b>每间房</b>儿童数 */
    private Integer numberOfChildren;

    /** 儿童年龄，英文逗号分隔；儿童数 > 0 时必填 */
    private String childrenAges;

    /** ISO 4217。响应不带币种，全靠这里声明 */
    private String currencyCode;

    /** 入住人国籍（两位 ISO 码）。筛的是可售集合，不是价格——见 MeituanProperties 同名字段 */
    private String clientNationality;
}
