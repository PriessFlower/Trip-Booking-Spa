package com.bingo.hotel.spa.intl.core.api.huitravel.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Head {
    private String appKey;

    private String timestamp;

    private String sign;
}
