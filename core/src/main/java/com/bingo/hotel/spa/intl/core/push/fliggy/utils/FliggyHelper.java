package com.bingo.hotel.spa.intl.core.push.fliggy.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bingo.hotel.spa.intl.core.util.ResourceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class FliggyHelper {
    public static final Map<String, Long> cityCodeMap = new HashMap();

    private static void initCityCodeMap() {
        JSONArray config = JSONArray.parseArray(ResourceUtil.readText("fliggyCityCodeList.json"));
        for (int i = 0; i < config.size(); i++) {
            JSONObject cityConfig = config.getJSONObject(i);
            cityCodeMap.put(cityConfig.getString("cityName"), cityConfig.getLong("cityId"));
        }
    }

    @PostConstruct
    public void init() {
        // 执行初始化逻辑
        log.info("启动时加载飞猪城市信息开始");
        initCityCodeMap();
        log.info("启动时加载飞猪城市信息结束");
    }
}
