/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.apache.commons.lang3.StringUtils
 */
package com.trip.booking.spa.core.placeholder.hotelbase.enums;

import org.apache.commons.lang3.StringUtils;

public enum BedTypeAllEnum {
    LARGE_BED(1, "\u5927\u5e8a"),
    TWIN_BED(2, "\u53cc\u5e8a"),
    SINGLE_BED(3, "\u5355\u4eba\u5e8a"),
    WATER_BED(4, "\u6c34\u5e8a"),
    WATER_BED_LARGE(5, "\u6c34\u5e8a-\u5927\u5e8a"),
    WATER_BED_TWIN(6, "\u6c34\u5e8a-\u53cc\u5e8a"),
    WATER_BED_SINGLE(7, "\u6c34\u5e8a-\u5355\u4eba\u5e8a"),
    EXTRA_LARGE_BED(8, "\u7279\u5927\u5e8a"),
    TATAMI(9, "\u69bb\u69bb\u7c73"),
    ROUND_BED(10, "\u5706\u5e8a"),
    BUNK_BED(11, "\u4e0a\u4e0b\u5e8a"),
    KANG_BED(12, "\u7095"),
    TENT_BED(13, "\u5e10\u7bf7"),
    HEART_SHAPED_BED(14, "\u5fc3\u578b\u5e8a"),
    SOFA_BED(15, "\u6c99\u53d1\u5e8a"),
    SOFA(15, "\u6c99\u53d1"),
    TRUNDLE_BED(16, "\u5b50\u6bcd\u5e8a"),
    SLEEPING_BAG(17, "\u7761\u888b"),
    SPACE_CAPSULE(18, "\u592a\u7a7a\u8231"),
    CAPSULE_BED(18, "\u80f6\u56ca\u5e8a"),
    DORMITORY_BED(19, "\u5bbf\u820d\u5e8a\u4f4d"),
    MULTIPLE_SINGLE_BEDS(20, "\u591a\u5f20\u5355\u4eba\u5e8a"),
    MULTIPLE_LARGE_BEDS(21, "\u591a\u5f20\u5927\u5e8a"),
    MULTIPLE_BUNK_BEDS(22, "\u591a\u5f20\u4e0a\u4e0b\u94fa"),
    DOUBLE_DECKER_BED(23, "\u53cc\u5c42\u5e8a"),
    JAPANESE_FUTON(24, "\u65e5\u5f0f\u5e8a\u57ab"),
    DOUBLE_BED(25, "\u53cc\u4eba\u5e8a"),
    SINGLE_SOFA_BED(26, "\u5355\u4eba\u6c99\u53d1\u5e8a"),
    DOUBLE_SOFA_BED(27, "\u53cc\u4eba\u6c99\u53d1\u5e8a"),
    CHILD_BED(28, "\u513f\u7ae5\u5e8a"),
    CHILD_BUNK_BED(29, "\u513f\u7ae5\u53cc\u5c42\u5e8a"),
    SUPER_LARGE_BED(30, "\u8d85\u5927\u5e8a"),
    TWO_SUPER_LARGE_BEDS(31, "\u4e24\u5f20\u8d85\u5927\u5e8a"),
    THREE_SUPER_LARGE_BEDS(32, "3\u5f20\u8d85\u5927\u5e8a"),
    THREE_EXTRA_LARGE_BEDS(33, "3\u5f20\u7279\u5927\u5e8a"),
    MULTIPLE_SUPER_LARGE_BEDS(34, "\u591a\u5f20\u8d85\u5927\u5e8a"),
    MULTIPLE_EXTRA_LARGE_BEDS(35, "\u591a\u5f20\u7279\u5927\u5e8a"),
    MULTIPLE_DOUBLE_BEDS(36, "\u591a\u5f20\u53cc\u4eba\u5e8a"),
    EXTRA_LARGE_MURPHY_BED(37, "\u7279\u5927\u58c1\u5e8a"),
    LARGE_MURPHY_BED(38, "\u5927\u58c1\u5e8a"),
    DOUBLE_MURPHY_BED(39, "\u53cc\u4eba\u58c1\u5e8a"),
    SINGLE_MURPHY_BED(40, "\u5355\u4eba\u58c1\u5e8a"),
    JAPANESE_FUTON_EXTRA_LARGE(41, "\u65e5\u5f0f\u5e8a\u57ab\uff08\u7279\u5927\u5e8a\uff09"),
    JAPANESE_FUTON_LARGE(42, "\u65e5\u5f0f\u5e8a\u57ab\uff08\u5927\u5e8a\uff09"),
    JAPANESE_FUTON_DOUBLE(43, "\u65e5\u5f0f\u5e8a\u57ab\uff08\u53cc\u4eba\u5e8a\uff09"),
    JAPANESE_FUTON_SINGLE(44, "\u65e5\u5f0f\u5e8a\u57ab\uff08\u5355\u4eba\u5e8a\uff09"),
    JAPANESE_FUTON_MULTIPLE_SINGLE(45, "\u65e5\u5f0f\u5e8a\u57ab\uff08\u591a\u5f20\u5355\u4eba\u5e8a\uff09"),
    JAPANESE_FUTON_TWIN(46, "\u65e5\u5f0f\u5e8a\u57ab\uff08\u53cc\u5e8a\uff09"),
    EXTRA_LARGE_BUNK_BED(47, "\u7279\u5927\u5e8a\uff08\u53cc\u5c42\uff09"),
    LARGE_BUNK_BED(48, "\u5927\u5e8a\uff08\u53cc\u5c42\uff09"),
    SINGLE_BUNK_BED(49, "\u5355\u4eba\u5e8a\uff08\u53cc\u5c42\uff09"),
    DOUBLE_BUNK_BED(50, "\u53cc\u4eba\u5e8a\uff08\u53cc\u5c42\uff09"),
    DRAWER_SOFA_BED(51, "\u6c99\u53d1\u5e8a\uff08\u62bd\u5c49\u5f0f\uff09"),
    BUNK_BED_LARGE_AND_SINGLE(52, "\u53cc\u5c42\u5e8a\uff08\u5927\u5e8a\u548c\u5355\u4eba\u5e8a\uff09"),
    MULTIPLE_SINGLE_BUNK_BEDS(53, "\u591a\u5f20\u5355\u4eba\u5e8a\uff08\u53cc\u5c42\uff09"),
    ROUND_WATER_BED(54, "\u5706\u5f62\u6c34\u5e8a"),
    SQUARE_WATER_BED(55, "\u65b9\u5f62\u6c34\u5e8a"),
    SMALL_DOUBLE_BED(56, "\u5c0f\u578b\u53cc\u4eba\u5e8a"),
    FlOOR_BED(57, "\u5730\u9762\u5e8a\u94fa"),
    FOLD_BED(58, "\u6298\u53e0\u5e8a"),
    FUTON_CUSHION(59, "\u84b2\u56e2"),
    HEATED_BED(60, "\u6696\u7095"),
    MIXED_DORMITORY_BED(61, "\u6df7\u5408\u5bbf\u820d\u5e8a"),
    MIDDLE_BED(62, "\u4e2d\u5e8a"),
    OTHER(0, "\u5176\u4ed6");

    private Integer value;
    private String desc;

    private BedTypeAllEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer getValue() {
        return this.value;
    }

    public void setValue(String Integer2) {
        this.value = this.value;
    }

    public String getDesc() {
        return this.desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public static Integer getValueByDesc(String desc) {
        if (StringUtils.isBlank((CharSequence)desc)) {
            return OTHER.getValue();
        }
        for (BedTypeAllEnum item : BedTypeAllEnum.values()) {
            if (!item.getDesc().equals(desc)) continue;
            return item.getValue();
        }
        return OTHER.getValue();
    }

    public static BedTypeAllEnum getBedTypeByDesc(String desc) {
        if (StringUtils.isBlank((CharSequence)desc)) {
            return OTHER;
        }
        for (BedTypeAllEnum item : BedTypeAllEnum.values()) {
            if (!item.getDesc().equals(desc)) continue;
            return item;
        }
        return OTHER;
    }
}
