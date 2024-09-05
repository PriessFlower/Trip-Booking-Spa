package com.bingo.hotel.spa.intl.core.api.huitravel;

import com.bingo.hotel.spa.intl.core.util.Md5Utils;
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
        long timestamp = System.currentTimeMillis();
        String appKey = "bsgl";
        String secretKey = "C340EB9BF816799573229D66B6F11555";
        System.out.println(timestamp);
        System.out.println(Md5Utils.md5Hex(Md5Utils.md5Hex(appKey + secretKey) + timestamp));
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