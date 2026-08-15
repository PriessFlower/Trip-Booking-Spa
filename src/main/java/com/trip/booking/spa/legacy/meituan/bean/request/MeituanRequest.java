package com.trip.booking.spa.legacy.meituan.bean.request;

import com.trip.booking.spa.platform.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

@Getter
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeituanRequest<T> {

    /**
     * 【必填】平台分配给分销商的安全凭证公钥。注意：联调或测试调用接口时，使用测试账号的accesskey。
     */
    private String accesskey;
    /**
     * 【必填】接口名称
     */
    private String method;
    /**
     * 【必填】随机正整数。与timestamp联合使用以防止重放攻击
     */
    private Integer nonce;
    /**
     * 【必填】平台分配给分销商的分销业务ID
     */
    private Integer partnerId;
    /**
     * 【必填】测试标记，值为test。注意：联调或测试调用接口时，使用测试账号，且必传该字段。正式调用接口时，使用正式账号，且不允许传该字段。
     */
    private String test;
    /**
     * 【必填】10位时间戳
     */
    private Long timestamp;
    /**
     * 【必填】分销平台API版本号
     */
    private String version;
    /**
     * 【必填】用于验证此次请求合法性的签名
     */
    private String signature;
    /**
     * 【非必填】业务请求参数字段，值为业务请求参数
     */
    private String data;


    MeituanRequest(String method, String version, long timestamp, int nonce, int partnerId, String accesskey,
                   String secretKey, String test, T t) {

        this.method = method;
        this.version = version;
        this.timestamp = timestamp;
        this.nonce = nonce;
        this.partnerId = partnerId;
        this.accesskey = accesskey;
        if (StringUtils.isNotBlank(test)) {
            this.test = test;
        }
        this.data = JsonUtils.writeObject2Json(t);
        this.signature = calculateSignature(this, secretKey);

    }

    private static <T> boolean checkRequest(String method, String version, long timestamp, int nonce,
                                            int partnerId, String accesskey, String secretKey, String test, T t) {
        if (StringUtils.isBlank(method)) {
            log.error("MeituanRequest method is empty");
            return false;
        }
        if (StringUtils.isBlank(version)) {
            log.error("MeituanRequest version is empty");
            return false;
        }
        if (timestamp <= 0) {
            log.error("MeituanRequest timestamp is error");
            return false;
        }
        if (nonce <= 0) {
            log.error("MeituanRequest nonce is error");
            return false;
        }
        if (partnerId <= 0) {
            log.error("MeituanRequest nonce is partnerId");
            return false;
        }
        if (StringUtils.isBlank(accesskey)) {
            log.error("MeituanRequest accesskey is empty");
            return false;
        }
        if (StringUtils.isBlank(secretKey)) {
            log.error("MeituanRequest secretKey is empty");
            return false;
        }
        if (t == null) {
            log.error("MeituanRequest T is null");
            return false;
        }
        return true;
    }

    public static <T> MeituanRequest buildRequest(String method, String version, long timestamp, int nonce,
                                                  int partnerId, String accesskey, String secretKey,
                                                  String test, T t) {
        if (checkRequest(method, version, timestamp, nonce, partnerId, accesskey, secretKey, test, t)) {
            return new MeituanRequest(method, version, timestamp, nonce, partnerId, accesskey, secretKey,
                    test, t);
        }
        return null;
    }

    private String calculateSignature(MeituanRequest request, String secretKey) {

        // 计算签名
        String rawSignStr = "accesskey=" + request.getAccesskey()
                + "&data=" + request.getData()
                + "&method=" + request.getMethod()
                + "&nonce=" + request.getNonce()
                + "&partnerId=" + request.getPartnerId();

        if (StringUtils.isNotBlank(request.getTest())) {
            rawSignStr += "&test=" + request.getTest();
        }
        rawSignStr += "&timestamp=" + request.getTimestamp()
                + "&version=" + request.getVersion();

        return this.hmacSha1(rawSignStr, secretKey);
    }


    public String hmacSha1(String plainText, String secretKey) {
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                org.apache.commons.codec.binary.StringUtils.getBytesUtf8(secretKey), "HmacSHA1");
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(secretKeySpec);
            return Base64.encodeBase64String(mac.doFinal(
                    org.apache.commons.codec.binary.StringUtils.getBytesUtf8(plainText)));
        } catch (GeneralSecurityException var5) {
            throw new IllegalArgumentException(var5);
        }
    }


}
