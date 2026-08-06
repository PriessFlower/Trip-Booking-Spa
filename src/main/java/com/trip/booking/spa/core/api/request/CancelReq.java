package com.trip.booking.spa.core.api.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CancelReq {

    @NonNull
    private Integer supplierId;//供应商ID
    @NonNull
    private String supplierOrderId;//供应商产品Id
    @NonNull
    private String orderId;//自有订单Id

}
