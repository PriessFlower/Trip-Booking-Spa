package com.trip.booking.spa.gateway.domain.supplier;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * 在产供应商。**只登记在产的家**——未接入的供应商不在此预留条目，预研结论在
 * docs/product-identity.md §4（R-4.6）。
 *
 * <p>2026-08-21 删去七家遗留供应商（travelConnect/aicHotels/didatravel/huitravel/
 * FastpayHotels/ratehawk/meituan）。删前核过生产库：`supplier_hotel_base`、
 * `supplier_product_base`、`supplier_room_base` 三表的 supplier_id 只出现 10005 与
 * 10010，遗留编码零行，故 {@link #getEnum(int)} 不会因此返回 null。
 *
 * <p>{@code code} 是持久化编码（落库、跨系统传递），{@link #name()} 是观测标签值
 * （docs/observability.md O-2.3：合法值只有本枚举各 name）。
 *
 * <p>编码顺延本仓自己的体系（10005/10010/10015…），<b>不沿用 cursor 的 codeInt</b>——
 * 那边 huizhiB2c 与 tourmind 撞号（都是 10），且编码曾漂移（meituan 6→53）。
 * 两系统间的对应关系由上游在调用时映射，不进本枚举。
 */
public enum SupplierSourceEnum {
    EXPEDIA(10005, "expedia"),
    ELONG(10010, "elong"),
    /** 接入中（2026-08-26 开工）：能力矩阵登记与放量以实现落地为准，编码先行是建档/缓存键需要 */
    FLIGGY(10015, "fliggy"),
    ;

    SupplierSourceEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private int code;

    public int getCode() {
        return code;
    }

    private String desc;

    public String getDesc() {
        return desc;
    }

    private static final Map<String, SupplierSourceEnum> enumMap = Maps.newHashMap();

    static {
        for (SupplierSourceEnum e : values()) {
            enumMap.put(e.name(), e);
        }
    }

    public static SupplierSourceEnum valueOfName(String name) {
        return enumMap.get(name);
    }

    public static SupplierSourceEnum getEnum(int code) {
        for (SupplierSourceEnum sourceEnum : SupplierSourceEnum.values()) {
            if (sourceEnum.code == code) {
                return sourceEnum;
            }
        }
        return null;
    }
}
