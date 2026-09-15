package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanQueryPriceTask;
import com.trip.booking.spa.gateway.application.pricing.QueueHotelCount;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 美团查价预热任务队列的读写口，与 {@link ElongQueryPriceTaskMapper} 同构。
 *
 * <p><b>只读任务坐标，不碰价格</b>：刷价产物走 Redis（PriceCacheService），本表只记
 * "该刷哪家、上次何时刷的"。易腐报价码（goodsId）一律不入库（R-2.1）。
 */
@Repository
public interface MeituanQueryPriceTaskMapper {

    /**
     * 按优先级取一批待刷任务，最久未刷的优先。
     *
     * @param temporaryUpgrade 1=连同临时升级的一并取；0=只取本优先级且未升级的
     */
    List<MeituanQueryPriceTask> getQueryPriceTaskList(@Param("priorityLevelNumber") int priorityLevelNumber,
                                                   @Param("temporaryUpgrade") int temporaryUpgrade,
                                                   @Param("batchSize") int batchSize);

    /** 刷完一家后累加次数并记录时间；下一轮据 last_time 排序自然轮转 */
    int updateAddCount(MeituanQueryPriceTask meituanQueryPriceTask);

    /** 调档（模板偏移算法产出）：无货=业务档+10，有货 -10 回原档。失败不调（调用方保证） */
    int updatePriority(@Param("id") Long id, @Param("priority") int priority);

    /**
     * 清单覆盖面：清单里共多少家店、其中多少家当前有货。口径写在 XML 里，六家逐字同构。
     * 消费方是看板「清单里还有多少家出得了货」（O-5.1）。
     */
    QueueHotelCount countQueueHotels();
}
