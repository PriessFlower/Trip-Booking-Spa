package com.trip.booking.spa.cli.seq;

import com.trip.booking.spa.cli.dto.ProductRespDTO;
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
