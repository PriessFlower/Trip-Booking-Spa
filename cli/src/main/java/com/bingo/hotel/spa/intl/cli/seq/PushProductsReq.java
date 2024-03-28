package com.bingo.hotel.spa.intl.cli.seq;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
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
