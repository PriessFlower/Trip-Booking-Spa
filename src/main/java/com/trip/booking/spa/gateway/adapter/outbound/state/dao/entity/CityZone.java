package com.trip.booking.spa.gateway.adapter.outbound.state.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author xrt
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "id")
public class CityZone {
    private int id;
    private String cityName;
    private String countryName;
    private String timezone;
}
