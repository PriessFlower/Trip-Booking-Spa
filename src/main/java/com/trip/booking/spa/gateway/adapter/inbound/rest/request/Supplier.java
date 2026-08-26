package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class Supplier {

    private Integer supplierId;//供应商ID

    private String sHotelId;//供应商酒店列表

    private String sProductId;//供应商产品Id

}
