package com.trip.booking.spa.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * expedia床型相关信息包含验价地址
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedCheckInfo {
    private String bedId;
    private String bedType; //床型描述
    private String checkHref; //验价地址
}
