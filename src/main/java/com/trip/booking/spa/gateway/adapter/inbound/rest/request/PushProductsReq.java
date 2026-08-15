package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PushProductsReq {
    @NonNull
    private Integer distributorId;

    private String hotelId;

    private List<ProductRespDTO> pushProductsDTO;
}
