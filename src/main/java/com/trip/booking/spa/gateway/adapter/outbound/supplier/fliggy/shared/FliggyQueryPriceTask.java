package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.trip.booking.spa.gateway.application.pricing.RefreshTaskRow;
import lombok.Data;

import java.util.Date;

/**
 * 飞猪查价预热任务行（表 {@code fliggy_query_price_task}），与两家同名实体同构。
 * 一行 = 一次 ari.availability 调用（飞猪按单店查询）。
 */
@Data
public class FliggyQueryPriceTask implements RefreshTaskRow {

    private Long id;

    /** 飞猪酒店 id（shid） */
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

    /** 优先级。飞猪起步单档 0，字段与两家同构以便日后分档 */
    private int priorityLevelNumber;

    /** 临时提升优先级：0 否 1 是（验价即刷反馈环用，起步未接） */
    private int temporaryUpgrade;

    /** 临时优先级截止时间 */
    private Date upgradeDeadline;
}
