package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "expedia")
public class ExpediaRapidProperties implements InitializingBean, ResolveProperties {

    /** Expedia Rapid 生产端点主机名；下单与真实费用仅可能产生于此 */
    private static final String PRODUCTION_HOST = "api.ean.com";

    private String apiKey;
    private String sharedSecret;
    private String session = "trip-booking-spa";
    private String ownIp = "127.0.0.1";
    private String userAgent = "trip-booking-spa/0.0.1";
    /**
     * 查价单次返回的报价条数上限，固定取 Expedia 允许的最大值 250
     * （PDF p63 "rate_plan_count (max 250)"）。
     *
     * <p><b>有意写死，不上配置面</b>（§3.8.1：无"不发版即调整"的正当运维场景）：
     * 比价与 resolve 换票都在查价响应的现货池里找票，调小即截断票池、
     * 降低换票命中率，而"临时调小"没有任何已知场景。上限若随 Expedia 官方
     * 变更，随发版调整此常量。
     */
    private static final int RATE_PLAN_COUNT = 250;

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

    /**
     * resolve 换票容差的绝对帽（分）：单笔自动让利的财务上限，与比例容差取严
     * （issue #59：比例门吃毛利不亏损，但绝对敞口随单价放大，帽封顶大额单）。
     * 兜底 20 元为安全侧从严（§3.3.3），运维值见 Nacos（与兜底同为 20 元，放宽须先过毛利测算）。
     */
    @Value("${supplier.expedia.resolve-price-cap-cents:2000}")
    private int resolvePriceCapCents = 2000;

    private boolean bookingEnabled;
    private boolean productionEndpointEnabled;
    private Url url = new Url();

    public void requireCredentials() {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(sharedSecret)) {
            throw new IllegalStateException(
                    "Expedia Rapid credentials are missing; set EXPEDIA_API_KEY and EXPEDIA_SHARED_SECRET");
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (resolvePriceTolerance < 0 || resolvePriceTolerance > 0.2) {
            throw new IllegalStateException(
                    "supplier.expedia.resolve-price-tolerance must be between 0 and 0.2, but was " + resolvePriceTolerance);
        }
        if (resolvePriceCapCents < 0 || resolvePriceCapCents > 100000) {
            throw new IllegalStateException(
                    "supplier.expedia.resolve-price-cap-cents must be between 0 and 100000, but was " + resolvePriceCapCents);
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
        if (bookingEnabled) {
            requireCredentials();
        }
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
        return RATE_PLAN_COUNT;
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

    public int getResolvePriceCapCents() {
        return resolvePriceCapCents;
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 绑定，校验见 {@link #afterPropertiesSet()} */
    public void setResolvePriceCapCents(int resolvePriceCapCents) {
        this.resolvePriceCapCents = resolvePriceCapCents;
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

    public static class Url {
        private String host = "https://test.ean.com";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }

}
