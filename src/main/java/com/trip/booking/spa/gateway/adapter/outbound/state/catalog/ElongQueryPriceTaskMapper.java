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

    /**
     * 批次4 反馈环(F-6):验价事件把该酒店全部日期行临时升档 24h。
     * 高频档取批以 OR 借入这些行(取批处判 upgrade_deadline 未过期),让刷价额度
     * 自动流向有真实需求的酒店;到期后自然退出借入,无需显式降档任务。
     */
    int upgradeByShId(@org.apache.ibatis.annotations.Param("shId") String shId);
}
