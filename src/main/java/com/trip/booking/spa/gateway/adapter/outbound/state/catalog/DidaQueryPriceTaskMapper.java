package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaQueryPriceTask;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 道旅查价预热任务队列的读写口，与 {@link ElongQueryPriceTaskMapper} 同构。
 *
 * <p><b>只读任务坐标，不碰价格</b>：刷价产物走 Redis（PriceCacheService），本表只记
 * "该刷哪家、上次何时刷的"。易腐报价码（RatePlanID）一律不入库（R-2.1）。
 */
@Repository
public interface DidaQueryPriceTaskMapper {

    /**
     * 按优先级取一批待刷任务，最久未刷的优先。
     *
     * @param temporaryUpgrade 1=连同临时升级的一并取；0=只取本优先级且未升级的
     */
    List<DidaQueryPriceTask> getQueryPriceTaskList(@Param("priorityLevelNumber") int priorityLevelNumber,
                                                   @Param("temporaryUpgrade") int temporaryUpgrade,
                                                   @Param("batchSize") int batchSize);

    /** 刷完一家后累加次数并记录时间；下一轮据 last_time 排序自然轮转 */
    int updateAddCount(DidaQueryPriceTask didaQueryPriceTask);

    /** 调档（模板偏移算法产出）：无货=业务档+10，有货 -10 回原档。失败不调（调用方保证） */
    int updatePriority(@Param("id") Long id, @Param("priority") int priority);
}
