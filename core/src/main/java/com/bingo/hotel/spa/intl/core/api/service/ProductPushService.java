package com.bingo.hotel.spa.intl.core.api.service;

import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;

import java.util.List;

public interface ProductPushService {
    void pushPriceAndInventory(List<ProductRespDTO> productRespDTOS);
}
