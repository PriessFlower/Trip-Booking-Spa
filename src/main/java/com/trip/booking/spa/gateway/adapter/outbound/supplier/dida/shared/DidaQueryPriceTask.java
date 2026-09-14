package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.application.pricing.RefreshTaskRow;
import lombok.Data;

import java.util.Date;

/**
 * 道旅查价预热任务行（表 {@code dida_query_price_task}）。
 *
 * <p>与另两家的同名实体同构：一行 = 一次 pricesearch 调用。<b>不做批量聚合</b>——
 * 多店 + 实时报价的组合会被道旅降级（2026-09-08 实测：逐店实时 280 条 vs 10 家一批实时
 * 105 条，4/10 家整家消失），判据见 {@code DidaPriceSearchRequest} 的注释。
 */
@Data
public class DidaQueryPriceTask implements RefreshTaskRow {

    private Long id;

    /** 道旅酒店 id（HotelID，数字串） */
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
