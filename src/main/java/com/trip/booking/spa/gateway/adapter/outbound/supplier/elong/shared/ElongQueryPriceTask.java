package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import lombok.Data;

import java.util.Date;

/**
 * 艺龙查价预热任务行（表 {@code elong_query_price_task}）。
 *
 * <p>与 Expedia 的同名实体同构——一行 = 一次 hotel.detail 调用。艺龙不做批量聚合：
 * 官方文档规定非大陆酒店一次仅支持 1 家，混批会返回假空 {@code Rooms=[]} 被误当无房
 * （移植风险⑤，cursor 混批越南的真实教训）。
 */
@Data
public class ElongQueryPriceTask {

    private Long id;

    /** 艺龙酒店 id（HotelId） */
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
