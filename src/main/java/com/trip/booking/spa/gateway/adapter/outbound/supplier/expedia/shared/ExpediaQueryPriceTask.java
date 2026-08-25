package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.application.pricing.RefreshTaskRow;
import lombok.Data;

import java.util.Date;

/**
 * @description:待查价缓存的实体
 * @author: dick_w
 * @date: 2025/3/17 14:30
 * @param:
 * @return:
 **/
@Data
public class ExpediaQueryPriceTask implements RefreshTaskRow {

    /**
     * 主键
     */
    private Long id;

    /**
     * 四海通sh_id
     */
    private String shId;

    /**
     * 入住日期
     */
    private int delayCheckIn;

    /**
     * 离店日期
     */
    private int delayCheckOut;

    /**
     * 查询数量
     */
    private int queryCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 上次更新时间
     */
    private Date lastTime;

    /**
     * 酒店等级
     */
    private int priorityLevelNumber;

    /**
     * 临时升级
     */
    private int temporaryUpgrade;

    /**
     * 临时升级截止时间
     */
    private Date upgradeDeadline;
}
