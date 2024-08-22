package com.bingo.hotel.spa.intl.core.api.expedia.utils;

import org.springframework.beans.factory.annotation.Value;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Timestamp;
import java.util.Date;
/**
 * expedia交互工具类.
 *
 * @author : hanJH
 * @version : 1.0 2024/08/15
 * @since : 1.0
 **/
public class ExpediaUtils {

    @Value("${expedia.apiKey}")
    private String expediaApiKey = "6urn54ijk30uttcqm64c3it6gf";

    @Value("${expedia.sharedSecret}")
    private String expediaSharedSecret = "708a6tdaqvm7";

    public void SignatureGeneration() {
        Date date = new java.util.Date();
        Long timestamp = (date.getTime() / 1000);
        String signature = null;
        try {
            String toBeHashed = expediaApiKey + expediaSharedSecret + timestamp;
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(toBeHashed.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            signature = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String authHeaderValue = "EAN APIKey=" + expediaApiKey + ",Signature=" + signature + ",timestamp=" + timestamp;
    }

    public static void main(String[] args) {

    }
}
