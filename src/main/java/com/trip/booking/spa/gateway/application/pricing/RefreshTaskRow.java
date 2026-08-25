package com.trip.booking.spa.gateway.application.pricing;

import java.util.Date;

/**
 * 一行刷价任务：一家酒店 + 一个相对日期偏移。各家供应商的任务表结构同构（F-2.1），
 * 故骨架只认这个接口，不认具体实体。
 *
 * <p>为什么是接口而不是共同父类：两家的实体分别归各自适配层（architecture.md §2 供应商语义
 * 只允许出现在适配层），共享父类会把一家的字段变更牵连到另一家。接口只约束骨架真正要读的那几项。
 */
public interface RefreshTaskRow {

    /** 供应商侧酒店标识 */
    String getShId();

    /** 入住日相对今天的偏移（天）。绝对日期在执行时才换算，故任务行永不过期（F-2.1） */
    int getDelayCheckIn();

    int getDelayCheckOut();

    /** 所属档位。借入判定要用它与当前取批档比对 */
    int getPriorityLevelNumber();

    /** 1=被验价反馈环临时升档（F-6.1） */
    int getTemporaryUpgrade();

    void setTemporaryUpgrade(int temporaryUpgrade);

    /** 升档到期时刻；null 视为已过期 */
    Date getUpgradeDeadline();
}
