package com.trip.booking.spa.gateway.adapter.outbound.state.catalog;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.persistence.ExpediaPropertySnapshotRow;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
public interface ExpediaPropertySnapshotMapper {

    int upsert(ExpediaPropertySnapshotRow row);

    int markInactive(String propertyId, Timestamp fetchedAt);

    /**
     * 批量取已存指纹，供摄取时判断内容是否变化。
     *
     * <p>只查 property_id 与 raw_sha256 两列，不碰 raw_json／normalized_json，
     * 故一批 250 家的开销约几十 KB，远小于它能省下的写入量（每家约 94 KB）。
     *
     * @return 每行含 propertyId 与 rawSha256
     */
    List<Map<String, Object>> selectHashes(@Param("language") String language,
                                           @Param("propertyIds") Collection<String> propertyIds);
}
