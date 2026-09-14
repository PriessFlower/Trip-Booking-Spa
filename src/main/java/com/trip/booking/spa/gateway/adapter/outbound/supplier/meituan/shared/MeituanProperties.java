package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 美团境外供给接入配置的唯一持有者。
 *
 * <p>凭证（accessKey/secretKey/partnerId）一律经环境变量注入（PROJECT.md §3.5.1），
 * 变量名登记在 {@code .env.example}。凭证缺失不拦启动，只停本家链路（闸为 {@link #isConfigured()}）。
 *
 * <p><b>一个端点承所有接口</b>：URL 固定，具体调哪个接口由请求体里的 {@code method} 决定
 * （如 {@code hotel.oversea.batch.goods.rp}）。故本类只持主机地址，不持路径。
 */
@Slf4j
@Component
public class MeituanProperties implements InitializingBean, ResolveProperties {

    @Value("${supplier.meituan.access-key:}")
    private String accessKey;

    @Value("${supplier.meituan.secret-key:}")
    private String secretKey;

    /** 分销商业务 ID，进签名参数（数值型，官方参数表要求整数） */
    @Value("${supplier.meituan.partner-id:}")
    private String partnerId;

    /** 接口地址；本家所有接口共用这一个 URL */
    @Value("${supplier.meituan.url:https://fenxiao.meituan.com/opdtor/api/v2}")
    private String url;

    /**
     * 报价币种（ISO 4217）。<b>本家响应不带币种字段</b>——币种只在请求里声明，返回的
     * {@code price} 就按它计价。故这个值不是兜底而是<b>唯一出处</b>：报出去的每一分钱都以它为准，
     * 配错即全线错币种。我方与美团的结算币种为 USD。
     */
    @Value("${supplier.meituan.currency:USD}")
    private String currency;

    /**
     * 入住人国籍（ISO 3166-1 alpha-2）。
     *
     * <p><b>它筛的是可售集合，不是价格</b>——2026-09-14 生产实测（酒店 2388500，T+7）：
     * 不传与传 CN 完全一致（均 317 条产品、价格逐条相同），传 US 只剩 37 条，且这 37 条的价格
     * 与 CN 时一模一样。即：国籍决定哪些产品对这位客人开放，开放的那些价格不变。
     *
     * <p>故报价与验价必须用同一个值，否则会出现"列表里有、点进去没有"。上游契约不带国籍字段，
     * 故配置化，取我方客源国 CN。
     */
    @Value("${supplier.meituan.client-nationality:CN}")
    private String clientNationality;

    /**
     * resolve 管线开关（docs/product-identity.md §3）。默认 false 为安全侧兜底（§3.3.3）。
     *
     * <p>闸口三项声明（PROJECT.md §3.8.5）：
     * <ul>
     *   <li><b>误开的后果</b>：容差门（R-3.3）失效场景下可能按更高价自动成交</li>
     *   <li><b>误关的后果</b>：短期看比另几家轻——goodsId 在一个会话内会跨住期复用，
     *       "旧列表点击"命中原票是常态。但它按易腐申报（见 {@code SupplierIdentityProfile.MEITUAN}
     *       的取证），长周期的命中率我们还没有数据，故这个"轻"只对短周期成立</li>
     *   <li><b>生效执行面</b>：全部承载 /client/spa/check 流量的节点，仅 meituan 链路</li>
     * </ul>
     */
    @Value("${supplier.meituan.resolve-enabled:false}")
    private boolean resolveEnabled;

    /** resolve 换票的价格容差（R-3.3），口径与另几家同规，取值域 [0, 0.2] */
    @Value("${supplier.meituan.resolve-price-tolerance:0.02}")
    private double resolvePriceTolerance;

    /** resolve 换票容差的绝对帽（分），与比例容差取严（issue #59）。兜底 20 元从严 */
    @Value("${supplier.meituan.resolve-price-cap-cents:2000}")
    private int resolvePriceCapCents;

    @Override
    public void afterPropertiesSet() {
        if (resolvePriceTolerance < 0 || resolvePriceTolerance > 0.2) {
            throw new IllegalStateException(
                    "supplier.meituan.resolve-price-tolerance must be between 0 and 0.2, but was " + resolvePriceTolerance);
        }
        if (resolvePriceCapCents < 0 || resolvePriceCapCents > 100000) {
            throw new IllegalStateException(
                    "supplier.meituan.resolve-price-cap-cents must be between 0 and 100000, but was " + resolvePriceCapCents);
        }
        // 币种与国籍都有兜底默认值，空只可能是被人显式清空的。它们不是可缺省的装饰：
        // 币种是本家币种的唯一出处（响应不带），空了就会静默产出没有币种的报价；
        // 国籍决定可售集合，空了报价与验价可能各看到一套货。故宁可起不来也不许带着空值跑。
        if (StringUtils.isBlank(currency)) {
            throw new IllegalStateException("supplier.meituan.currency must not be blank："
                    + "本家响应不带币种，此值即币种的唯一出处");
        }
        if (StringUtils.isBlank(clientNationality)) {
            throw new IllegalStateException("supplier.meituan.client-nationality must not be blank："
                    + "它筛的是可售集合，报价与验价必须同值");
        }
        log.info("美团接入配置: url={}, credentialsConfigured={}, currency={}, clientNationality={}",
                url, isConfigured(), currency, clientNationality);
    }

    /** 凭证是否齐备；未配置时本家链路应如实回报不可用，而非带着空凭据去打供应商 */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)
                && StringUtils.isNotBlank(partnerId);
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public String getUrl() {
        return url;
    }

    public String getCurrency() {
        return currency;
    }

    public String getClientNationality() {
        return clientNationality;
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
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /** 仅供测试构造场景使用 */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /** 仅供测试构造场景使用 */
    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }

    /** 仅供测试构造场景使用 */
    public void setUrl(String url) {
        this.url = url;
    }

    /** 仅供测试构造场景使用 */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /** 仅供测试构造场景使用 */
    public void setClientNationality(String clientNationality) {
        this.clientNationality = clientNationality;
    }

    /** 仅供测试构造场景使用 */
    public void setResolveEnabled(boolean resolveEnabled) {
        this.resolveEnabled = resolveEnabled;
    }
}
