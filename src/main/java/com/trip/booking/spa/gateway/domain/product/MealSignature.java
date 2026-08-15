package com.trip.booking.spa.gateway.domain.product;

/**
 * productKey 的餐食成分：早/午/晚三个布尔位的规范形（docs/product-identity.md R-1.1）。
 *
 * <p>不用 NONE/BREAKFAST/HALF_BOARD 这类命名分类，因为分类必然有"OTHER"兜底位，
 * 而把"只含午餐"和"只含晚餐"折进同一个 OTHER，resolve 换票时就可能拿晚餐票顶替
 * 午餐票——那是"卖错"，违反键成分元规则（R-1.6：赌错只许少卖，不许卖错）。
 * 三位布尔无损，且份数随占用人数走、占用已单独进键，故位级即够。
 *
 * <p>UNKNOWN 合法但不进目录（R-5.4），语义同 {@link CancelClass#UNKNOWN}。
 */
public final class MealSignature {

    private static final MealSignature UNKNOWN = new MealSignature(false, false, false, false);

    private final boolean known;
    private final boolean breakfast;
    private final boolean lunch;
    private final boolean dinner;

    private MealSignature(boolean known, boolean breakfast, boolean lunch, boolean dinner) {
        this.known = known;
        this.breakfast = breakfast;
        this.lunch = lunch;
        this.dinner = dinner;
    }

    public static MealSignature known(boolean breakfast, boolean lunch, boolean dinner) {
        return new MealSignature(true, breakfast, lunch, dinner);
    }

    public static MealSignature unknown() {
        return UNKNOWN;
    }

    public boolean isKnown() {
        return known;
    }

    /** 键用规范串，如 {@code B1L0D0}；未知恒为 {@code UNKNOWN} */
    public String canonical() {
        if (!known) {
            return "UNKNOWN";
        }
        return "B" + (breakfast ? 1 : 0) + "L" + (lunch ? 1 : 0) + "D" + (dinner ? 1 : 0);
    }
}
