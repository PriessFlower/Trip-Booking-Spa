package com.trip.booking.spa.cli.enums;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * @author zhe.hao
 * @since 2023-10-25
 * @version 1.0
 */
public enum AdapterGuestType {
    ALL_GUEST(0, "所有宾客"), DOMESTIC_GUEST(1, "内宾"), CHINESE_GUEST(2, "中宾"), FOREIGN_GUEST(3, "外宾");

    public int code;
    
    public String desc;
    
    private Set<String> guestSet;
    
    private static class Holder {
        static final Map<String, AdapterGuestType> adapterGuestTypeNameMap = Maps.newConcurrentMap();
        static final Map<Integer, AdapterGuestType> adapterGuestTypeCodeMap = Maps.newConcurrentMap();
    }

    private AdapterGuestType(int code, String desc) {
        initSet();
        this.code = code;
        this.desc = desc;
        guestSet = sets[code];
        Holder.adapterGuestTypeNameMap.put(this.name(), this);
        Holder.adapterGuestTypeCodeMap.put(code, this);
    }

    public static AdapterGuestType codeOf(int code) {
        return Holder.adapterGuestTypeCodeMap.get(code);
    }
    
    public static boolean contains(String guestTypekey) {
        return Holder.adapterGuestTypeNameMap.containsKey(guestTypekey);
    }

    public Set<String> getGuestSet() {
        return guestSet;
    }

    private Set<String>[] sets;
    
    private String[] guests = { "内宾", "中宾", "外宾" };

    private void initSet() {
        sets = new Set[4];
        sets[0] = Sets.newHashSet(guests);
        sets[1] = Sets.newHashSet(guests[0]);
        sets[2] = Sets.newHashSet(guests[1]);
        sets[3] = Sets.newHashSet(guests[2]);
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}