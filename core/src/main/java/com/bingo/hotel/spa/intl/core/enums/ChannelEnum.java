package com.bingo.hotel.spa.intl.core.enums;

public enum ChannelEnum {

    ATS(1, "ATS"),
    PEOPLE(2, "People");

    private int value;
    private String desc;

    ChannelEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
