/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

public enum ExpediaContinentEnum {
    ANTARCTICA("11700", "Antarctica", "\u5357\u6781\u6d32"),
    NORTH_AMERICA("500001", "North America", "\u5317\u7f8e"),
    Europe("6022967", "Europe", "\u6b27\u6d32"),
    CARIBBEAN("6022969", "Caribbean", "\u52a0\u52d2\u6bd4\u5730\u533a"),
    ASIA("6023099", "Asia", "\u4e9a\u6d32"),
    SOUTH_AMERICA("6023117", "South America", "\u5357\u7f8e\u6d32"),
    AUSTRALIA("6023180", "Australia - New Zealand and the South Pacific", "\u6fb3\u6d32 - \u7ebd\u897f\u5170\u4e0e\u5357\u592a\u5e73\u6d0b"),
    Mexico_Central_America("6023181", "Mexico and Central America", "\u58a8\u897f\u54e5\u548c\u4e2d\u7f8e\u6d32"),
    MIDDLE_EAST("6023182", "Middle East", "\u4e2d\u4e1c"),
    AFRICA("6023185", "Africa", "\u975e\u6d32");

    private String key;
    private String desc;
    private String desc_cn;

    private ExpediaContinentEnum(String key, String desc, String desc_cn) {
        this.key = key;
        this.desc = desc;
        this.desc_cn = desc_cn;
    }

    public String getKey() {
        return this.key;
    }

    public String getDesc() {
        return this.desc;
    }

    public String getDesc_cn() {
        return this.desc_cn;
    }
}
