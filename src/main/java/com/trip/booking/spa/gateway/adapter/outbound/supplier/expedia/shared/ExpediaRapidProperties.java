package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

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

    /** Expedia Rapid 生产端点主机名；下单与真实费用仅可能产生于此 */
    private static final String PRODUCTION_HOST = "api.ean.com";

    private String apiKey;
    private String sharedSecret;
    private String session = "trip-booking-spa";
    private String ownIp = "127.0.0.1";
    private String userAgent = "trip-booking-spa/0.0.1";
    /**
     * 查价单次返回的报价条数上限；Expedia 允许的最大值为 250（PDF p63 "rate_plan_count (max 250)"）。
     * 我方技术调参，不属合同车道参数——车道参数见 {@link ExpediaContractProfile}。
     * 键名归入 supplier 域而非本类的 expedia 前缀，故用 @Value 单独绑定——
     * 域为封闭枚举，不得为单个供应商新增顶层域（§3.7.2），同 {@link #staticDataEnabled}。
     */
    @Value("${supplier.expedia.rate-plan-count:250}")
    private int ratePlanCount = 250;   // 字段初值供非 Spring 构造场景（测试）；@Value 默认值供注入场景

    /**
     * resolve 管线开关（docs/product-identity.md §3）：验价时令牌已死，是否允许按
     * productKey 在当前现货中自动换票。默认 false 为安全侧兜底（§3.3.3）——关闭时
     * 行为与旧实现完全一致（RATE_DEAD）。运维可调，键名归 supplier 域（§3.7.2）。
     *
     * <p>闸口三项声明（PROJECT.md §3.8.5）：
     * <ul>
     *   <li><b>误开的后果</b>：容差门（R-3.3）失效场景下可能按更高价自动成交；正当
     *       关闭场景是发现资损异常时不发版止血</li>
     *   <li><b>误关的后果</b>：令牌死的验价一律 RATE_DEAD，旅客被迫重新查价重选——
     *       退回旧行为，不丢单、不资损，仅体验降级</li>
     *   <li><b>生效执行面</b>：全部承载 /client/spa/check 流量的节点（所有 profile），
     *       仅 Expedia 链路；建档与查价链路不读本开关</li>
     * </ul>
     */
    @Value("${supplier.expedia.resolve-enabled:false}")
    private boolean resolveEnabled;

    /**
     * resolve 换票的价格容差（R-3.3）：新价 ≤ 展示价 ×(1+本值) 才许自动换票，
     * 超出宁可 RATE_DEAD——防静默涨价成交。0.02 即 2%，取值域 [0, 0.2]。
     */
    @Value("${supplier.expedia.resolve-price-tolerance:0.02}")
    private double resolvePriceTolerance = 0.02;

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
        // @Value 绑定不经 setter，故此处校验；超出 Expedia 允许范围会被其直接拒绝
        if (ratePlanCount < 1 || ratePlanCount > 250) {
            throw new IllegalStateException(
                    "supplier.expedia.rate-plan-count must be between 1 and 250, but was " + ratePlanCount);
        }
        if (resolvePriceTolerance < 0 || resolvePriceTolerance > 0.2) {
            throw new IllegalStateException(
                    "supplier.expedia.resolve-price-tolerance must be between 0 and 0.2, but was " + resolvePriceTolerance);
        }
        URI endpoint = URI.create(url.getHost());
        boolean productionEndpoint = PRODUCTION_HOST.equalsIgnoreCase(endpoint.getHost());

        if (productionEndpoint && !productionEndpointEnabled) {
            throw new IllegalStateException(
                    "Expedia production endpoint is blocked; explicit production authorization is required");
        }
        // 下单护栏按端点区分：真实订单与真实费用只可能产生于生产端点，测试端点下单为沙箱行为
        // （不产生费用、不生成真实预订），需要放开以便验证下单链路。
        //
        // 生产端点下单在此硬拦，且有意不提供"生产下单授权"开关：按 §3.2.3，安全护栏的变更本就
        // 必须经发版与评审，"改代码才能开"即是最强形式；凭空增设一个当前无法启用的开关属过度设计。
        // Expedia 认证通过后，此处应作为一次独立的、经评审的改动放开。
        if (bookingEnabled && productionEndpoint) {
            throw new IllegalStateException(
                    "Expedia booking against the production endpoint is blocked until certification; "
                            + "booking is permitted only against the test endpoint");
        }
        if (bookingEnabled || staticDataEnabled) {
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

    public int getRatePlanCount() {
        return ratePlanCount;
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 绑定，校验见 {@link #afterPropertiesSet()} */
    public void setRatePlanCount(int ratePlanCount) {
        this.ratePlanCount = ratePlanCount;
    }

    public boolean isResolveEnabled() {
        return resolveEnabled;
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 绑定 */
    public void setResolveEnabled(boolean resolveEnabled) {
        this.resolveEnabled = resolveEnabled;
    }

    public double getResolvePriceTolerance() {
        return resolvePriceTolerance;
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 绑定，校验见 {@link #afterPropertiesSet()} */
    public void setResolvePriceTolerance(double resolvePriceTolerance) {
        this.resolvePriceTolerance = resolvePriceTolerance;
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
        /**
         * 下载 catalog 清单文件的并发连接数。
         *
         * <p>该文件在 AWS S3 us-west-2 裸源站（无 CDN），生产机到该源 RTT 约 170ms。单条 TCP 流
         * 在这种长肥管道上吞吐被拥塞窗口卡死——生产实测 21 KB/s，而入网能力有 2 MB/s，
         * 闲着七十倍。分块并行绕开该限制。
         *
         * <p>取值依据为生产实测（在生产机上跑同一套分块算法，走真实链路）：
         * 8 连接 142 KB/s，99MB 文件 11.8 分钟，较单连接的约 80 分钟提速 6.8 倍；
         * 99 块全部一次收满、gzip 校验通过、无空洞。
         *
         * <p>取 8 而不更高：每块都是一次 S3 请求，过多可能触发对端限速；且实测该值已能
         * 全程跑满、无长尾。取值域 1–32，设为 1 即退回单连接。
         */
        private int downloadConnections = 8;
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

        public int getDownloadConnections() {
            return downloadConnections;
        }

        public void setDownloadConnections(int downloadConnections) {
            if (downloadConnections < 1 || downloadConnections > 32) {
                throw new IllegalArgumentException(
                        "expedia.static-data.download-connections must be between 1 and 32");
            }
            this.downloadConnections = downloadConnections;
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
