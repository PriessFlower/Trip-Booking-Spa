package com.trip.booking.spa.core.api.fastpay.access;

import com.alibaba.fastjson.JSON;
import com.trip.booking.spa.core.api.common.access.HttpUtils;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
public class GetTokenAccess {

    public String request(String url, String username, String password, String clientId, String clientSecret) {
        Map<String, String> header = Maps.newHashMap();
        header.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Map<String, String> body = Maps.newHashMap();
        body.put("grant_type", "password");
        body.put("username", username);
        body.put("password", password);
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("version", "1");
        String hotelList = null;
        try {
            hotelList = HttpUtils.doPost(url, header, body);
            if (StringUtils.isBlank(hotelList) || null == JSON.parseObject(hotelList).get("access_token")) {
                return "";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = JSON.parseObject(hotelList).get("access_token").toString();
        String type = JSON.parseObject(hotelList).get("token_type").toString();
        return type + " " + token;
    }

    public static void main(String[] args) {
        Map<String, String> header = Maps.newHashMap();
        header.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Map<String, String> body = Maps.newHashMap();
        body.put("grant_type", "password");
        body.put("username", "APIBaosheng");
        body.put("password", "A~FjN68p");
        body.put("client_id", "Baosheng.fph.com");
        body.put("client_secret", "NRF5yadWnV9gX1qs9uIWdn/d4qf1qQieixKTcjuCDck=");
        body.put("version", "1");
        String hotelList = null;
        try {
            hotelList = HttpUtils.doPost("https://avail-baosheng.fastpayhotels.net/security/token", header, body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = JSON.parseObject(hotelList).get("access_token").toString();
        String type = JSON.parseObject(hotelList).get("token_type").toString();
        System.out.println(type + " " + token);
    }

}
