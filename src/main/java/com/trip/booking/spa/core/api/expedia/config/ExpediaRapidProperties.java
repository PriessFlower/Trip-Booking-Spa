package com.trip.booking.spa.core.api.expedia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "expedia")
public class ExpediaRapidProperties implements InitializingBean {

    /**
     * 静态数据摄取总开关，运维可调，权威取值由 Nacos 下发（PROJECT.md §3.2.2）。
     * 键名归入 supplier 域而非本类的 expedia 前缀，故用 @Value 单独绑定——
     * 域为封闭枚举，不得为单个供应商新增顶层域（§3.7.2）。
     * 默认 false 为安全侧兜底（§3.3.3）；本类未加 @RefreshScope，改后需重启容器方生效。
     */
    @Value("${supplier.expedia.static-data-enabled:false}")
    private boolean staticDataEnabled;

    private String apiKey;
    private String sharedSecret;
    private String session = "trip-booking-spa";
    private String ownIp = "127.0.0.1";
    private String userAgent = "trip-booking-spa/0.0.1";
    private boolean bookingEnabled;
    private boolean productionEndpointEnabled;
    private Url url = new Url();
    private StaticData staticData = new StaticData();

    public void requireCredentials() {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(sharedSecret)) {
            throw new IllegalStateException(
                    "Expedia Rapid credentials are missing; set EXPEDIA_API_KEY and EXPEDIA_SHARED_SECRET");
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (bookingEnabled) {
            throw new IllegalStateException("Expedia booking is disabled until certification and explicit authorization");
        }
        URI endpoint = URI.create(url.getHost());
        if ("api.ean.com".equalsIgnoreCase(endpoint.getHost()) && !productionEndpointEnabled) {
            throw new IllegalStateException(
                    "Expedia production endpoint is blocked; explicit production authorization is required");
        }
        if (staticDataEnabled) {
            requireCredentials();
        }
    }

    /** 静态数据摄取是否启用；唯一读取入口，取值见 {@link #staticDataEnabled} */
    public boolean isStaticDataEnabled() {
        return staticDataEnabled;
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 从 Nacos 绑定 */
    public void setStaticDataEnabled(boolean staticDataEnabled) {
        this.staticDataEnabled = staticDataEnabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getOwnIp() {
        return ownIp;
    }

    public void setOwnIp(String ownIp) {
        this.ownIp = ownIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public void setBookingEnabled(boolean bookingEnabled) {
        this.bookingEnabled = bookingEnabled;
    }

    public boolean isProductionEndpointEnabled() {
        return productionEndpointEnabled;
    }

    public void setProductionEndpointEnabled(boolean productionEndpointEnabled) {
        this.productionEndpointEnabled = productionEndpointEnabled;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public StaticData getStaticData() {
        return staticData;
    }

    public void setStaticData(StaticData staticData) {
        this.staticData = staticData;
    }

    public static class Url {
        private String host = "https://test.ean.com";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }

    public static class StaticData {
        private int batchSize = 250;
        private String language = "en-US";
        /**
         * 摄取的语言列表；不指定语言时按此列表逐语言拉取
         */
        private java.util.List<String> languages = new java.util.ArrayList<>(java.util.List.of("en-US", "zh-CN"));
        private String supplySource = "expedia";
        private String mappingVersion = "expedia-content-v2";


        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            if (batchSize < 1 || batchSize > 250) {
                throw new IllegalArgumentException("expedia.static-data.batch-size must be between 1 and 250");
            }
            this.batchSize = batchSize;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public java.util.List<String> getLanguages() { return languages; }

        public void setLanguages(java.util.List<String> languages) { this.languages = languages; }

        public String getSupplySource() {
            return supplySource;
        }

        public void setSupplySource(String supplySource) {
            this.supplySource = supplySource;
        }

        public String getMappingVersion() {
            return mappingVersion;
        }

        public void setMappingVersion(String mappingVersion) {
            this.mappingVersion = mappingVersion;
        }
    }
}
