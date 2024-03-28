package com.bingo.hotel.spa.intl.core.api.common.enums;

public enum CoordinateTypeEnum {
    /**
     * 坐标类型
     */
    GAODE("GAODE"),
    BAIDU("BAIDU"),
    MAPBAR("MAPBAR"),
    GOOGLE("GOOGLE"),
    ;

    private String value;

    CoordinateTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CoordinateTypeEnum getEnum(String value) {
        for (CoordinateTypeEnum typeEnum : CoordinateTypeEnum.values()) {
            if (typeEnum.value.equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }

}
