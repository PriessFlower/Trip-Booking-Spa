/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelinfo.dto;

public class BedInfoDTO {
    private String bedType;
    private String bedDesc;
    private Integer bedNumber;

    public String getBedType() {
        return this.bedType;
    }

    public String getBedDesc() {
        return this.bedDesc;
    }

    public Integer getBedNumber() {
        return this.bedNumber;
    }

    public BedInfoDTO setBedType(String bedType) {
        this.bedType = bedType;
        return this;
    }

    public BedInfoDTO setBedDesc(String bedDesc) {
        this.bedDesc = bedDesc;
        return this;
    }

    public BedInfoDTO setBedNumber(Integer bedNumber) {
        this.bedNumber = bedNumber;
        return this;
    }

    public BedInfoDTO(String bedType, String bedDesc, Integer bedNumber) {
        this.bedType = bedType;
        this.bedDesc = bedDesc;
        this.bedNumber = bedNumber;
    }

    public BedInfoDTO() {
    }
}
