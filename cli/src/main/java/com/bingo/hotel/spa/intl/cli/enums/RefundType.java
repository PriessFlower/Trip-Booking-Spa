package com.bingo.hotel.spa.intl.cli.enums;

/**
 * 退款类型枚举类 - 去哪儿
 */
public enum RefundType {

    NO_DEDUCTION("NO_DEDUCTION", "不扣房费"),
    DEDUCT_BY_PERCENT("DEDUCT_BY_PERCENT","扣除房费的百分比"),
    DEDUCT_BY_AMOUNT("DEDUCT_BY_AMOUNT", "扣除固定金额"),
    DEDUCT_FIRST_NIGHT("DEDUCT_FIRST_NIGHT", "扣除首晚房费");

    private String code;
    private String description;

    RefundType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
