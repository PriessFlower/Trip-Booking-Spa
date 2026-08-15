package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaQueryPriceTask;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @BelongsProject: trip-booking-spa
 * @BelongsPackage: com.trip.booking.spa.core.api.ratehawk.mapper
 * @Author: dick_w
 * @CreateTime: 2025-03-17  14:28
 * @Description: expedia查价缓存mapper
 * @Version: 1.0
 */
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
}
