package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Expedia 查价缓存 mapper。 */
@Repository
public interface ExpediaQueryPriceTaskMapper {

    /**
     * @description:查询池子数据
     * @author: dick_w
     * @date: 2025/3/17 14:30
     * @param: [priorityLevelNumber, temporaryUpgrade]
     * @return: java.util.List<com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask>
     **/
    List<ExpediaQueryPriceTask> getQueryPriceTaskList(@Param("priorityLevelNumber") int priorityLevelNumber,
                                            @Param("temporaryUpgrade") int temporaryUpgrade,
                                            @Param("batchSize") int batchSize);

    /**
     * @description:更新查询次数
     * @author: dick_w
     * @date: 2025/3/17 14:30
     * @param: [expediaQueryPriceTask]
     * @return: int
     **/
    int updateAddCount(ExpediaQueryPriceTask expediaQueryPriceTask);

    /** 调档（模板偏移算法产出）：无货=业务档+10,有货 -10 回原档。失败不调（调用方保证） */
    int updatePriority(@Param("id") Long id, @Param("priority") int priority);
}
