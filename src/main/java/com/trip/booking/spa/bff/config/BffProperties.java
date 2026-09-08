package com.trip.booking.spa.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * expdia 验收前端（TripBooking B2C 演示站）专用配置。
 *
 * <p>本包是为对齐 expdia 前端验收清单而建的独立 BFF 层，与 core 的供应商网关互不影响；
 * 只读引用 core 的 Expedia 凭证与签名工具，不改动 core 代码。
 *
 * <p>联系人为固定值：经与 Expedia 沟通前的过渡方案（TR3/TR4/TR5），旅客真实姓名
 * 仅存本地订单记录，不出境。邮箱一经使用必须永久稳定——按 affiliate_reference_id
 * 反查行程要求提供下单时的原始邮箱。
 */
@Component
@ConfigurationProperties(prefix = "bff")
public class BffProperties {

    /** 验收证据（请求/响应日志）落盘目录 */
    private String evidenceDir = "logs/bff-evidence";

    /** 搜索页单次报价的酒店数上限（availability 单次最多 250 个 property_id） */
    private int searchLimit = 25;

    /** 前端展示语言，与 expedia_property_content 已摄取语言一致 */
    private String language = "zh-CN";

    /** 报价与下单币种 */
    private String currency = "CNY";

    /** 旅客销售市场（POS），Shopping 请求 country_code（TR2） */
    private String countryCode = "CN";

    private final Contact contact = new Contact();

    private final Suggest suggest = new Suggest();

    /**
     * 搜索框联想走 trip-booking-agg 的检索接口（ES）。
     *
     * <p>agg 与 spa 同机（trip-offline），spa 容器是 {@code --network host}，所以默认
     * {@code 127.0.0.1}，不经任何网络设备。agg 不可用时上层退回本地 LIKE 查询。
     */
    public static class Suggest {

        private String baseUrl = "http://127.0.0.1:18080";

        /**
         * 只要这家供应商卖得了的店。本 BFF 的定价链走 Expedia Rapid，
         * 不过滤就会搜出只挂了 elong / huizhi 的店——点得进去却报不出价。
         */
        private String supplier = "expedia";

        /** 联想是敲一个字就发一次的交互，超时必须短，宁可退回旧路径 */
        private int timeoutMs = 800;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSupplier() {
            return supplier;
        }

        public void setSupplier(String supplier) {
            this.supplier = supplier;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Contact {
        private String email = "bff-sandbox@tripbooks.org";
        private String givenName = "Tao";
        private String familyName = "Zhou";
        private String phoneCountryCode = "86";
        private String phoneNumber = "13800000000";
        private String addressLine1 = "88 Zhongshan East Road";
        private String city = "Shanghai";
        private String stateProvinceCode = "SH";
        private String postalCode = "200002";
        private String addressCountryCode = "CN";

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getGivenName() {
            return givenName;
        }

        public void setGivenName(String givenName) {
            this.givenName = givenName;
        }

        public String getFamilyName() {
            return familyName;
        }

        public void setFamilyName(String familyName) {
            this.familyName = familyName;
        }

        public String getPhoneCountryCode() {
            return phoneCountryCode;
        }

        public void setPhoneCountryCode(String phoneCountryCode) {
            this.phoneCountryCode = phoneCountryCode;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getAddressLine1() {
            return addressLine1;
        }

        public void setAddressLine1(String addressLine1) {
            this.addressLine1 = addressLine1;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getStateProvinceCode() {
            return stateProvinceCode;
        }

        public void setStateProvinceCode(String stateProvinceCode) {
            this.stateProvinceCode = stateProvinceCode;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getAddressCountryCode() {
            return addressCountryCode;
        }

        public void setAddressCountryCode(String addressCountryCode) {
            this.addressCountryCode = addressCountryCode;
        }
    }

    public String getEvidenceDir() {
        return evidenceDir;
    }

    public void setEvidenceDir(String evidenceDir) {
        this.evidenceDir = evidenceDir;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public Contact getContact() {
        return contact;
    }

    public Suggest getSuggest() {
        return suggest;
    }
}
