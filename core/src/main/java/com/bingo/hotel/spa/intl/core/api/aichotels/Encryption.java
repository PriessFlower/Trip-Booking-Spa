package com.bingo.hotel.spa.intl.core.api.aichotels;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Encryption {

    public static void main(String[] args) {
        // 打印结果
        System.out.println(getDate());
        System.out.println(generateSignature("POST", "/rate/public/search/room_availability", getDate(), "SRLxRgRXjjaPCWUbcWcwriusUNtpYe3VxRg9"));
    }

    public static String getDate() {
        Date date = new Date();
        // 创建一个SimpleDateFormat对象，并设置所需的格式
        SimpleDateFormat sdf = new SimpleDateFormat("E,d MMM Y H:m:s z", Locale.ENGLISH);
        // 设置时区为纽约时区
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        // 使用SimpleDateFormat格式化Date对象
        return sdf.format(date);
    }

    public static String generateSignature(String method, String reqUrl, String date, String secret) {
        StringBuilder sign = new StringBuilder();
        sign.append(method);
        sign.append(" ");
        sign.append(reqUrl);
        sign.append("\n");
        sign.append(date);
        byte[] sha1 = hmac_sha1(sign.toString(), secret);
        String signature;
        try {
            signature = new String(Base64.encodeBase64(sha1), "UTF-8");
            return signature;
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static byte[] hmac_sha1(String value, String key) {
        try {
            byte[] keyBytes = key.getBytes();
            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            return mac.doFinal(value.getBytes());
        } catch (Exception e) {
            return null;
        }
    }
}