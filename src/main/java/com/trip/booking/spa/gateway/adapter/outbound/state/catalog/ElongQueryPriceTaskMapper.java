package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongQueryPriceTask;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 艺龙查价预热任务队列的读写口，与 {@link ExpediaQueryPriceTaskMapper} 同构。
 *
 * <p><b>只读任务坐标，不碰价格</b>：刷价产物走 Redis（CachePriceService），
 * 本表只记"该刷哪家、上次何时刷的"。易腐令牌（GoodsUniqId、马甲）一律不入库（R-2.1）。
 */
@Repository
public interface ElongQueryPriceTaskMapper {

    /**
     * 按优先级取一批待刷任务，最久未刷的优先。
     *
     * @param temporaryUpgrade 1=连同临时升级的一并取；0=只取本优先级且未升级的
     */
    List<ElongQueryPriceTask> getQueryPriceTaskList(@Param("priorityLevelNumber") int priorityLevelNumber,
                                                    @Param("temporaryUpgrade") int temporaryUpgrade,
                                                    @Param("batchSize") int batchSize);

    /** 刷完一家后累加次数并记录时间；下一轮据 update_time 排序自然轮转 */
    int updateAddCount(ElongQueryPriceTask elongQueryPriceTask);
}
