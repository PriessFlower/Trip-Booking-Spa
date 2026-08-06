package com.trip.booking.spa.cli.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bed {

    public String bedType;
    public String bedDesc;
    public int bedCount;
}
