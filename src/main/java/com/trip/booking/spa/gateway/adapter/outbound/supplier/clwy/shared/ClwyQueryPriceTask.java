package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import com.trip.booking.spa.gateway.application.pricing.RefreshTaskRow;
import lombok.Data;

import java.util.Date;

/**
 * 差旅无忧查价预热任务行（表 {@code clwy_query_price_task}）。
 *
 * <p>与另四家的同名实体同构：一行 = 一次 GetPrice 报价档调用（不传 RatePlanId，拿整店现货）。
 * 本家的报价档天然是<b>整店</b>口径，一次就把该店该住期的全部房型/价格计划带回来，
 * 故也没有"要不要合批"这个问题——批的单位就是店。
 */
@Data
public class ClwyQueryPriceTask implements RefreshTaskRow {

    private Long id;

    /** 差旅无忧酒店 id（HotelId，数字串） */
    private String shId;

    /** 入住日期偏移（天，相对今天） */
    private int delayCheckIn;

    /** 离店日期偏移（天） */
    private int delayCheckOut;

    /** 已查价次数 */
    private int queryCount;

    private Date createTime;

    private Date updateTime;

    /** 最近一次查价时间；为空表示从未刷过 */
    private Date lastTime;

    /** 优先级，数值即分层 */
    private int priorityLevelNumber;

    /** 临时提升优先级：0 否 1 是 */
    private int temporaryUpgrade;

    /** 临时优先级截止时间 */
    private Date upgradeDeadline;
}
