package com.bingo.hotel.spa.intl.core.api.common.enums;

public enum ProductTypeEnum {
    /**
     * 房间类型 1:全日房,2:钟点房,3:全部
     */
    DAY(1),
    CLOCK(2),
    ALL(3),
    ;

    private int value;

    ProductTypeEnum(int value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }

    public static ProductTypeEnum getEnum(int value) {
        for (ProductTypeEnum typeEnum : ProductTypeEnum.values()) {
            if (typeEnum.value == value) {
                return typeEnum;
            }
        }
        return null;
    }

}
