package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.cli.dto.ProductRespDTO;

import java.util.List;

public interface ProductPushService {
    void pushPriceAndInventory(List<ProductRespDTO> productRespDTOS);
}
