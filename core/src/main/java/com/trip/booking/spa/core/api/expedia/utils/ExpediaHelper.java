package com.trip.booking.spa.core.api.expedia.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.trip.booking.spa.core.util.ResourceUtil;
import com.google.common.collect.Lists;

import java.util.List;

public class ExpediaHelper {
    public static final List<String> hotelIdList = Lists.newArrayList();

    private static void initCityCodeMap() {
        JSONArray config = JSONArray.parseArray(ResourceUtil.readText("expediaHotelList.json"));
        for (int i = 0; i < config.size(); i++) {
            String hotelIds = config.getString(i);
            hotelIdList.add(hotelIds);
        }
    }

    static {
        initCityCodeMap();
    }
}
