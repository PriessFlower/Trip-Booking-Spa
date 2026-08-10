package com.trip.booking.spa.core.api.ratehawk.mapper;

import com.trip.booking.spa.core.api.ratehawk.model.RateHawkQueryPriceTask;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @BelongsProject: trip-booking-spa
 * @BelongsPackage: com.trip.booking.spa.core.api.ratehawk.mapper
 * @Author: dick_w
 * @CreateTime: 2025-03-10  17:24
 * @Description: RateHawk查价缓存mapper
 * @Version: 1.0
 */
@Repository
public interface RateHawkQueryPriceTaskMapper {

    /**
     * @description:查询池子数据
     * @author: dick_w
     * @date: 2025/3/10 17:41
     * @param: [priorityLevelNumber, temporaryUpgrade]
     * @return: java.util.List<com.trip.booking.spa.core.api.ratehawk.model.RateHawkQueryPriceTask>
     **/
    List<RateHawkQueryPriceTask> getQueryPriceTaskList(@Param("priorityLevelNumber") int priorityLevelNumber,
                                            @Param("temporaryUpgrade") int temporaryUpgrade,
                                            @Param("batchSize") int batchSize);

    /**
     * @description:更新查询次数
     * @author: dick_w
     * @date: 2025/3/10 17:40
     * @param: [rateHawkQueryPriceTask]
     * @return: int
     **/
    int updateAddCount(RateHawkQueryPriceTask rateHawkQueryPriceTask);
}
