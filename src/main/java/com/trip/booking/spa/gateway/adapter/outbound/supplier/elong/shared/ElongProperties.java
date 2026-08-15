package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

/**
 * 艺龙接入配置的唯一持有者。
 *
 * <p>凭证（user/appKey/secret）一律经环境变量注入（PROJECT.md §3.5.1），
 * 变量名登记在 {@code .env.example}。cursor 仓曾把三项明文写进 application.yml
 * （其 464-470 行），SPA 侧禁止重演。
 *
 * <p>凭证缺失不拦启动：艺龙只是多家供应商之一，缺谁的凭证只应停谁的链路。
 * 调用方以 {@link #isConfigured()} 为闸，未配置时如实回报并落日志（§6.2.1）。
 */
@Slf4j
@Component
public class ElongProperties implements InitializingBean {

    /** 艺龙生产网关主机名；测试网关为 api-test.elong.com，二者凭证相同 */
    private static final String PRODUCTION_HOST = "api.elong.com";

    @Value("${supplier.elong.user:}")
    private String user;

    @Value("${supplier.elong.app-key:}")
    private String appKey;

    @Value("${supplier.elong.secret:}")
    private String secret;

    /** REST 网关完整前缀；兜底取测试端点为安全侧（§3.3.3） */
    @Value("${supplier.elong.url-host:https://api-test.elong.com/rest}")
    private String urlHost;

    /** data 信封的 Version；艺龙订单接口族版本，查价/验价同用 */
    @Value("${supplier.elong.version:1.62}")
    private String version;

    /**
     * 下单闸口——安全护栏（PROJECT.md §3.2.3），固定在 application.yml，禁止移入 Nacos。
     *
     * <p><b>艺龙与 Expedia 的关键差异：没有沙箱。</b>api-test 网关已死（2026-08-15 实测
     * 全服务 403），唯一可用端点即生产网关——本开关打开后每一笔下单都是真实订单、
     * 真实费用。Expedia 靠"测试端点下单免费"验收下单链路，艺龙做不到，e2e 只能以
     * "免费取消窗口产品下真单 + 立即取消"的方式进行，且必须人工确认后执行。
     *
     * <p>闸口三项声明（§3.8.5）：
     * <ul>
     *   <li><b>误开的后果</b>：验收未完成即可能产生真实订单与真实费用（预付、我方
     *       授信账户扣款）</li>
     *   <li><b>误关的后果</b>：艺龙下单一律确定失败（供应商侧无任何动作），上游
     *       可安全改单其他供应商——不丢单、不资损</li>
     *   <li><b>生效执行面</b>：全部承载 /client/spa/booking 流量的节点（所有 profile），
     *       仅艺龙链路；查价/验价/查单/取消不读本开关——取消刻意不设闸：
     *       已存在的真单必须永远可撤</li>
     * </ul>
     */
    @Value("${elong.booking-enabled:false}")
    private boolean bookingEnabled;

    /**
     * 下单 CustomerIPAddress 的兜底值。艺龙以此做恶意订单风控（必填，缺失报 H001012），
     * 而渠道流量常无终端 IP。默认值为 cursor 生产实证过闸的出口 IP
     * （2026-08-04 单 26080422481151778fd3953b 教训）；SPA 生产切换出口后可经
     * 环境变量改为腾讯云出口。
     */
    @Value("${elong.customer-ip-fallback:47.92.28.195}")
    private String customerIpFallback;

    /**
     * 下单 Contact.Email。生产被艺龙接受的报文 3/3 均携带固定客服邮箱（联系人邮箱
     * 接收供应商通知，应指向运营团队而非旅客）；上游契约不含旅客邮箱，此处配置化。
     */
    @Value("${elong.booking-contact-email:customer_service@mail.haowan2000.com}")
    private String bookingContactEmail;

    /**
     * resolve 管线开关（docs/product-identity.md §3）：验价时报价码（GoodsUniqId）
     * 已不在现货，是否允许按 productKey 在当前现货中自动换票。默认 false 为安全侧
     * 兜底（§3.3.3）。运维可调，权威取值在 Nacos，键名归 supplier 域（§3.7.2）。
     *
     * <p>闸口三项声明（PROJECT.md §3.8.5）：
     * <ul>
     *   <li><b>误开的后果</b>：容差门（R-3.3）失效场景下可能按更高价自动成交；
     *       正当关闭场景是发现资损异常时不发版止血</li>
     *   <li><b>误关的后果</b>：报价码死的验价一律 RATE_DEAD，旅客被迫重新查价重选——
     *       艺龙报价码为会话级易腐（SupplierIdentityProfile.ELONG），误关即大量
     *       旧列表点击直接死，不丢单、不资损，仅体验退化</li>
     *   <li><b>生效执行面</b>：全部承载 /client/spa/check 流量的节点（所有 profile），
     *       仅艺龙链路；查价链路不读本开关</li>
     * </ul>
     */
    @Value("${supplier.elong.resolve-enabled:false}")
    private boolean resolveEnabled;

    /**
     * resolve 换票的价格容差（R-3.3）：新价 ≤ 展示价 ×(1+本值) 才许自动换票，
     * 超出宁可 RATE_DEAD——防静默涨价成交。取值域 [0, 0.2]，与 Expedia 同规。
     */
    @Value("${supplier.elong.resolve-price-tolerance:0.02}")
    private double resolvePriceTolerance;

    @Override
    public void afterPropertiesSet() {
        if (resolvePriceTolerance < 0 || resolvePriceTolerance > 0.2) {
            throw new IllegalStateException(
                    "supplier.elong.resolve-price-tolerance must be between 0 and 0.2, but was " + resolvePriceTolerance);
        }
        // 记明端点、凭证态与下单闸，便于从启动日志确认当前姿态。
        // 艺龙无沙箱：bookingEnabled=true 即真单真费用，启动日志必须可查
        log.info("艺龙接入配置: urlHost={}, productionEndpoint={}, credentialsConfigured={}, bookingEnabled={}",
                urlHost, urlHost != null && urlHost.contains(PRODUCTION_HOST), isConfigured(), bookingEnabled);
    }

    /** 凭证是否齐备；未配置时艺龙链路应如实回报不可用，而非带着空签名去打供应商 */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(user) && StringUtils.isNotBlank(appKey) && StringUtils.isNotBlank(secret);
    }

    public String getUser() {
        return user;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getSecret() {
        return secret;
    }

    public String getUrlHost() {
        return urlHost;
    }

    public String getVersion() {
        return version;
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public String getCustomerIpFallback() {
        return customerIpFallback;
    }

    public String getBookingContactEmail() {
        return bookingContactEmail;
    }

    public boolean isResolveEnabled() {
        return resolveEnabled;
    }

    public double getResolvePriceTolerance() {
        return resolvePriceTolerance;
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 绑定 */
    public void setUser(String user) {
        this.user = user;
    }

    /** 仅供测试构造场景使用 */
    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    /** 仅供测试构造场景使用 */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /** 仅供测试构造场景使用 */
    public void setUrlHost(String urlHost) {
        this.urlHost = urlHost;
    }

    /** 仅供测试构造场景使用 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 仅供测试构造场景使用 */
    public void setResolveEnabled(boolean resolveEnabled) {
        this.resolveEnabled = resolveEnabled;
    }

    /** 仅供测试构造场景使用 */
    public void setResolvePriceTolerance(double resolvePriceTolerance) {
        this.resolvePriceTolerance = resolvePriceTolerance;
    }
}
