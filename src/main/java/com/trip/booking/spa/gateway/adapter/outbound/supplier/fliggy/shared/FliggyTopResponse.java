package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;

/**
 * TOP 响应的双层信封基类。飞猪的错误分两层，混为一层就会把「我们的卡坏了」
 * 当成「酒店没货」（cursor 被这个错认坑了两个月）：
 *
 * <ul>
 *   <li><b>平台层</b>：根节点是 {@code error_response{code, sub_code, msg, sub_msg}}——
 *       签名错、session 过期、频控。业务数据完全没有。</li>
 *   <li><b>业务层</b>：根节点是 {@code <method名>_response}，其内再有
 *       {@code is_success / error_resp_code} 等业务结论。</li>
 * </ul>
 *
 * <p>解析用 JsonNode 显式取路径而非 typed POJO + ignoreUnknown：契约漂移是静默的
 * （Spa* DTO 的教训——漏字段不报错只是恒空），在拿到首批真实报文、钉下逐字段守护
 * 测试之前，显式路径 + 空判断的失败是响亮的。首次沙箱实测后按真实报文补齐守护。
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
            payload = tree.get(rootKey());
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    /** 平台层有没有拒绝（error_response 出现即拒） */
    public boolean isPlatformError() {
        return platformCode != null;
    }

    /**
     * 是不是<b>我方凭据/配置病</b>（AUTH_CONFIG）：session 过期/非法、签名错。
     * 判据来自 cursor 生产实证（code 27 / Invalid session / invalid-sessionkey /
     * 过期的SessionKey）——官方错误码表是空白的，此表只增不猜。
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
