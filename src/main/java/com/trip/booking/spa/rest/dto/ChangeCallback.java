package com.trip.booking.spa.rest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@Slf4j
@NoArgsConstructor
public class ChangeCallback {

    /**
     * 【必填】平台分配给分销商的安全凭证公钥。注意：联调或测试调用接口时，使用测试账号的accesskey。
     */
    private String accesskey;
    /**
     * 【必填】平台分配给分销商的安全凭证私钥。
     */
    private String secretKey;
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
    /**
     * 【必填】（此参数仅适用于v2.0接口
     */
    private String customerSessionId;
    /**
     * 【必填】（此参数仅适用于v2.0接口）说明：zh_CN。必须为zh_CN。
     */
    private String language;


    public static boolean checkRequest(ChangeCallback changeCallback) {
        if (StringUtils.isBlank(changeCallback.getMethod())) {
            log.error("ChangeCallback method is empty");
            return false;
        }
        if (StringUtils.isBlank(changeCallback.getVersion())) {
            log.error("ChangeCallback version is empty");
            return false;
        }
        if (changeCallback.getTimestamp() <= 0) {
            log.error("ChangeCallback timestamp is error");
            return false;
        }
        if (changeCallback.getNonce() <= 0) {
            log.error("ChangeCallback nonce is error");
            return false;
        }
        if (changeCallback.getPartnerId() <= 0) {
            log.error("ChangeCallback nonce is partnerId");
            return false;
        }
        if (StringUtils.isBlank(changeCallback.getAccesskey())) {
            log.error("ChangeCallback accesskey is empty");
            return false;
        }
        if (StringUtils.isBlank(changeCallback.getData())) {
            log.error("ChangeCallback data is null");
            return false;
        }
        return true;
    }

}
