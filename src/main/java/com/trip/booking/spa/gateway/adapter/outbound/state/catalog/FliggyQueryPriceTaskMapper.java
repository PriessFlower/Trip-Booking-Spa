package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyQueryPriceTask;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 飞猪查价预热任务队列的读写口，与艺龙/Expedia 同构。
 * 只读任务坐标，不碰价格；易腐票据（rate_key/create_key）一律不入库（R-2.1）。
 */
@Repository
public interface FliggyQueryPriceTaskMapper {

    /** 按优先级取一批待刷任务，最久未刷的优先（排序键 last_time，见艺龙 XML 同名注释） */
    List<FliggyQueryPriceTask> getQueryPriceTaskList(@Param("priorityLevelNumber") int priorityLevelNumber,
                                                     @Param("temporaryUpgrade") int temporaryUpgrade,
                                                     @Param("batchSize") int batchSize);

    /** 刷完一家后累加次数并记录时间 */
    int updateAddCount(FliggyQueryPriceTask task);

    /**
     * 调档（最后一次结果即档位）：无货→慢档(1)长周期探测回归，刷出有货→回快档(0)。
     * 失败不调（调用方保证）。
     */
    int updatePriority(@Param("id") Long id, @Param("priority") int priority);
}
