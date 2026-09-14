package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import com.trip.booking.spa.gateway.application.pricing.RefreshTaskRow;
import lombok.Data;

import java.util.Date;

/**
 * 美团查价预热任务行（表 {@code meituan_query_price_task}）。
 *
 * <p>与另五家的同名实体同构：一行 = 一次 batch.goods.rp 调用。
 *
 * <p>本家的接口<b>支持合批</b>（hotelIds 是列表），但仍逐店一次：刷价清单按"高德出单酒店"
 * 播种只有 537 家，逐店刷跑得起，没有为省配额而牺牲"一行任务 = 一次调用"这个可归因性的必要。
 * 清单显著变大时再议。
 */
@Data
public class MeituanQueryPriceTask implements RefreshTaskRow {

    private Long id;

    /** 美团酒店 id（hotelId，数字串） */
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
