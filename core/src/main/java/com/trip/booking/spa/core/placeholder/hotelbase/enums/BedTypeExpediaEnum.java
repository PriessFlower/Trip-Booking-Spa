/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.core.placeholder.hotelbase.enums;

public enum BedTypeExpediaEnum {
    OTHER("Other", "\u5176\u4ed6"),
    FULL_BED("FullBed", "\u53cc\u4eba\u5e8a"),
    TWIN_BED("TwinBed", "\u5355\u4eba\u5e8a"),
    QUEEN_BED("QueenBed", "\u5927\u5e8a"),
    SOFA_BED("SofaBed", "\u6c99\u53d1\u5e8a"),
    KING_BED("KingBed", "\u7279\u5927\u5e8a"),
    TWIN_XL_BED("TwinXLBed", "\u5927\u53f7\u5355\u4eba\u5e8a"),
    BUNK_BED("BunkBed", "\u53cc\u5c42\u5e8a"),
    FUTON("Futon", "\u65e5\u5f0f\u5e8a"),
    MURPHY_BED("MurphyBed", "\u58c1\u67dc\u5e8a"),
    TRUNDLE_BED("TrundleBed", "\u5b50\u6bcd\u5e8a"),
    WATER_BED("WaterBed", "\u6c34\u5e8a");

    private String value;
    private String desc;

    private BedTypeExpediaEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDesc() {
        return this.desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
