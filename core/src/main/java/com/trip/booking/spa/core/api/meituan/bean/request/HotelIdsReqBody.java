package com.trip.booking.spa.core.api.meituan.bean.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Builder
@Getter
@Slf4j
public class HotelIdsReqBody {

    @NonNull
    private Long maxId;
    @NonNull
    private Integer pageSize;

    public static boolean checkBody(@NonNull Long maxId, @NonNull Integer pageSize) {
        if (maxId < 0) {
            log.error("HotelIdsReqBody maxId is error");
            return false;
        }
        if (pageSize < 0) {
            log.error("HotelIdsReqBody pageSize is error");
            return false;
        }
        return true;
    }

}
