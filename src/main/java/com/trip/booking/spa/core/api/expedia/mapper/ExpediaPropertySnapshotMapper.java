package com.trip.booking.spa.core.api.expedia.mapper;

import com.trip.booking.spa.core.api.expedia.staticdata.persistence.ExpediaPropertySnapshotRow;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public interface ExpediaPropertySnapshotMapper {

    int upsert(ExpediaPropertySnapshotRow row);

    int markInactive(String propertyId, Timestamp fetchedAt);
}
