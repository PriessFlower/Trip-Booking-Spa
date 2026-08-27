package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;

/**
 * TOP 响应的双层信封基类：平台层 {@code error_response}（签名/session/频控）与业务层
 * {@code is_success} 必须分开判——混为一层会把我方凭据病当成供应商无货。
 * 解析用 JsonNode 显式取路径而非 typed POJO+ignoreUnknown：形状错要响亮，不许恒空。
 * 真实形状由 {@code FliggyRealPayloadTest} 钉住。
 */
public abstract class FliggyTopResponse implements BaseResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 业务根节点（{@code <method>_response}）；平台错误时为 null */
    protected JsonNode payload;

    private String platformCode;
    private String platformSubCode;
    private String platformMsg;

    /** 各响应类声明自己的业务根键名 */
    protected abstract String rootKey();

    protected void init(String raw) {
        try {
            JsonNode tree = MAPPER.readTree(raw);
            JsonNode error = tree.get("error_response");
            if (error != null) {
                platformCode = text(error, "code");
                platformSubCode = text(error, "sub_code");
                platformMsg = text(error, "msg") + "/" + text(error, "sub_msg");
                return;
            }
            // simplify=true 时 <method>_response 包裹层也被去掉,顶层直接是业务体(真实报文
            // 实证,与文档示例不同)。两形态都认:有包裹取包裹,无包裹取顶层
            payload = tree.has(rootKey()) ? tree.get(rootKey()) : tree;
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    /** 平台层有没有拒绝（error_response 出现即拒） */
    public boolean isPlatformError() {
        return platformCode != null;
    }

    /**
     * 是不是<b>我方凭据/配置病</b>（AUTH_CONFIG）。判据是生产实证码
     * （官方码表空白），此表只增不猜。
     */
    public boolean isCredentialFailure() {
        if (!isPlatformError()) {
            return false;
        }
        String all = platformCode + "/" + platformSubCode + "/" + platformMsg;
        return "27".equals(platformCode)
                || all.contains("Invalid session") || all.contains("invalid-sessionkey")
                || all.contains("SessionKey");
    }

    /**
     * 平台频控。TOP 官方 code 7 = App Call Limited；sub_code 含 flow-limit 的形态
     * 未经实证，先按保守方向（判错=少报频控，只会低估余量不会误伤调用）。
     */
    public boolean isPlatformThrottled() {
        if (!isPlatformError()) {
            return false;
        }
        return "7".equals(platformCode)
                || (platformSubCode != null && platformSubCode.contains("flow-limit"));
    }

    /** 平台层的错误摘要，进日志用；无平台错误返回 null */
    public String platformError() {
        return isPlatformError() ? platformCode + "/" + platformSubCode + "/" + platformMsg : null;
    }

    /**
     * 进 {@code supplier_io_error_code} 分布的码：平台层取 {@code code[:sub_code]}
     * （sub_code 才是可辨语义，如 invalid-sessionkey）；业务层由子类覆写补充。
     */
    public String metricErrorCode() {
        if (!isPlatformError()) {
            return null;
        }
        return platformSubCode == null ? platformCode : platformCode + ":" + platformSubCode;
    }

    @Override
    public boolean isSucc() {
        return !isPlatformError() && payload != null;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    protected static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
