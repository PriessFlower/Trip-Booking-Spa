package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 道旅接入配置的唯一持有者。
 *
 * <p>凭证（clientId/licenseKey）一律经环境变量注入（PROJECT.md §3.5.1），变量名登记在
 * {@code .env.example}。cursor 仓把两项明文写进 application.yml，SPA 侧不重演。
 *
 * <p>凭证缺失不拦启动：道旅只是多家供应商之一，缺谁的凭证只应停谁的链路。调用方以
 * {@link #isConfigured()} 为闸，未配置时如实回报并落日志（§6.2.1）。
 *
 * <p><b>道旅没有沙箱</b>：官方 pricesearch 文档「关于 DidaApiTestID 账号的详细说明」
 * （2026-09-08 查阅）写明该测试账号已废除，要测试须请客户经理另开专属测试账号。故
 * {@code url-host} 的兜底即生产端点——不存在"先在沙箱验"的中间态，与艺龙同形。
 */
@Slf4j
@Component
public class DidaProperties implements InitializingBean, ResolveProperties {

    @Value("${supplier.dida.client-id:}")
    private String clientId;

    @Value("${supplier.dida.license-key:}")
    private String licenseKey;

    /** 接口主机名；路径由各 Access 自带（道旅按接口分路径，不是单一入口） */
    @Value("${supplier.dida.url-host:https://api.didatravel.com}")
    private String urlHost;

    /**
     * 报价币种（ISO 4217）。它同时决定退改罚金 {@code Amount} 的币种——官方
     * price-search「RatePlanCancellationPolicyList」只给数值不给币种，其币种即本次报价币种。
     * 改它会让全部 productKey 之外的金额口径同步改变，属运维可调（Nacos）。
     */
    @Value("${supplier.dida.currency:CNY}")
    private String currency;

    /**
     * 客人国籍（ISO 3166-1 alpha-2），pricesearch 必填。
     *
     * <p>官方 price-search「关于 Nationality 字段的详细说明」（2026-09-08 查阅）：
     * 「请在 Nationality 中填入客人输入的真实国籍，以免后续到店出现争议单」。上游契约不带
     * 国籍字段，故此处配置化，取我方客源国 CN 为默认；<b>不</b>照 cursor 的做法填酒店所在国
     * ——那是拿酒店国冒充客人国籍，正是文档点名要避免的争议单来源。
     */
    @Value("${supplier.dida.nationality:CN}")
    private String nationality;

    /**
     * resolve 管线开关（docs/product-identity.md §3）：验价时 RatePlanID 已不在现货，
     * 是否允许按 productKey 在当前现货中自动换票。默认 false 为安全侧兜底（§3.3.3）。
     *
     * <p>闸口三项声明（PROJECT.md §3.8.5）：
     * <ul>
     *   <li><b>误开的后果</b>：容差门（R-3.3）失效场景下可能按更高价自动成交；
     *       正当关闭场景是发现资损异常时不发版止血</li>
     *   <li><b>误关的后果</b>：报价码死的验价一律 RATE_DEAD。道旅报价码腐得极快
     *       （3 秒即换代，见 SupplierIdentityProfile.DIDA），误关即绝大多数"旧列表点击"
     *       直接死，不丢单、不资损，仅体验退化</li>
     *   <li><b>生效执行面</b>：全部承载 /client/spa/check 流量的节点（所有 profile），
     *       仅道旅链路；查价链路不读本开关</li>
     * </ul>
     */
    @Value("${supplier.dida.resolve-enabled:false}")
    private boolean resolveEnabled;

    /** resolve 换票的价格容差（R-3.3），口径与另两家同规，取值域 [0, 0.2] */
    @Value("${supplier.dida.resolve-price-tolerance:0.02}")
    private double resolvePriceTolerance;

    /** resolve 换票容差的绝对帽（分），与比例容差取严（issue #59）。兜底 20 元从严 */
    @Value("${supplier.dida.resolve-price-cap-cents:2000}")
    private int resolvePriceCapCents;

    /**
     * 下单安全护栏（PROJECT.md §3.2.3/§3.8）：false 即拒绝下单，供应商侧不发生任何动作
     * （模板回 FAILED 非 UNKNOWN）。闸口由 AbstractBookingSyncSupportService 统一执行。
     *
     * <p>三项声明（§3.8.5）：
     * <ul>
     *   <li><b>误开的后果</b>：向道旅提交真实订单并占用信用额度。<b>道旅没有沙箱</b>（测试账号
     *       DidaApiTestID 已废除），唯一可用端点即生产，故不存在"先在沙箱验一遍"的中间态：开即真单</li>
     *   <li><b>误关的后果</b>：道旅下单全部确定失败，上游可直接终结订单退款；查价/验价/查单/取消不受影响</li>
     *   <li><b>生效执行面</b>：所有承载 /booking 端点的节点，与 profile 无关</li>
     * </ul>
     * 键名前缀 {@code dida.*} 表示它固定在 application.yml、改它必须发版（§3.7.2.1），禁止移入 Nacos。
     */
    @Value("${dida.booking-enabled:false}")
    private boolean bookingEnabled;

    /**
     * 下单 Contact.Email。联系人邮箱接收供应商通知，应指向运营团队而非旅客；上游契约不含旅客邮箱。
     * 官方未标 Phone/Email 必填（cursor 生产账号只带 Name 的请求也成单了），故空即不发。
     */
    @Value("${supplier.dida.booking-contact-email:}")
    private String bookingContactEmail;

    @Override
    public void afterPropertiesSet() {
        if (resolvePriceTolerance < 0 || resolvePriceTolerance > 0.2) {
            throw new IllegalStateException(
                    "supplier.dida.resolve-price-tolerance must be between 0 and 0.2, but was " + resolvePriceTolerance);
        }
        if (resolvePriceCapCents < 0 || resolvePriceCapCents > 100000) {
            throw new IllegalStateException(
                    "supplier.dida.resolve-price-cap-cents must be between 0 and 100000, but was " + resolvePriceCapCents);
        }
        // 道旅无沙箱：bookingEnabled=true 即真单真额度，启动日志必须可查
        log.info("道旅接入配置: urlHost={}, credentialsConfigured={}, currency={}, nationality={}, bookingEnabled={}",
                urlHost, isConfigured(), currency, nationality, bookingEnabled);
    }

    /** 凭证是否齐备；未配置时道旅链路应如实回报不可用，而非带着空凭据去打供应商 */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(clientId) && StringUtils.isNotBlank(licenseKey);
    }

    public String getClientId() {
        return clientId;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public String getUrlHost() {
        return urlHost;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNationality() {
        return nationality;
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public String getBookingContactEmail() {
        return bookingContactEmail;
    }

    @Override
    public boolean isResolveEnabled() {
        return resolveEnabled;
    }

    @Override
    public double getResolvePriceTolerance() {
        return resolvePriceTolerance;
    }

    @Override
    public int getResolvePriceCapCents() {
        return resolvePriceCapCents;
    }

    /** 仅供测试构造场景使用；运行期取值由 @Value 绑定 */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /** 仅供测试构造场景使用 */
    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
    }

    /** 仅供测试构造场景使用 */
    public void setUrlHost(String urlHost) {
        this.urlHost = urlHost;
    }

    /** 仅供测试构造场景使用 */
    public void setResolveEnabled(boolean resolveEnabled) {
        this.resolveEnabled = resolveEnabled;
    }

    /** 仅供测试构造场景使用 */
    public void setBookingEnabled(boolean bookingEnabled) {
        this.bookingEnabled = bookingEnabled;
    }

    /** 仅供测试构造场景使用 */
    public void setBookingContactEmail(String bookingContactEmail) {
        this.bookingContactEmail = bookingContactEmail;
    }
}
