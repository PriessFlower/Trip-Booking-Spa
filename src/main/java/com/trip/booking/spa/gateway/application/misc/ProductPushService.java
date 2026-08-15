package com.trip.booking.spa.gateway.application.misc;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;

import java.util.List;

public interface ProductPushService {
    void pushPriceAndInventory(List<ProductRespDTO> productRespDTOS);
}
