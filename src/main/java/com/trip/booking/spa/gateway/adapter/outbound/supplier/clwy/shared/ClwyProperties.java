package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;

/**
 * 差旅无忧（clwy，xiangdo 开放平台）接入配置的唯一持有者。
 *
 * <p>凭证（wid/apiKey）一律经环境变量注入（PROJECT.md §3.5.1），变量名登记在 {@code .env.example}。
 *
 * <p>凭证缺失不拦启动：本家只是多家供应商之一，缺谁的凭证只应停谁的链路。调用方以
 * {@link #isConfigured()} 为闸，未配置时如实回报并落日志（§6.2.1）。
 *
 * <p><b>没有沙箱</b>：官方文档（http://doc.open.xiangdo.cn/btob/，2026-09-14 查阅）只给一个
 * 生产域名，未提供测试环境，故 {@code url-host} 的兜底即生产端点——与艺龙、道旅同形。
 */
@Slf4j
@Component
public class ClwyProperties implements InitializingBean, ResolveProperties {

    @Value("${supplier.clwy.wid:}")
    private String wid;

    @Value("${supplier.clwy.api-key:}")
    private String apiKey;

    /** 接口主机名；路径由各 Access 自带 */
    @Value("${supplier.clwy.url-host:https://availability.xiangdo.cn}")
    private String urlHost;

    /**
     * 报价币种（ISO 4217）。<b>只作校验基准与兜底，不作断言</b>：clwy 的每日价自带 Currency，
     * 以报文为准；本值用于「报文没给币种」时的兜底与不一致时的告警。cursor 侧配的是 CNY。
     */
    @Value("${supplier.clwy.currency:CNY}")
    private String currency;

    /**
     * 客人国籍（ISO 3166-1 alpha-2）。官方 05-product-price：{@code CountryCode}「报价、验价、
     * 下单该值必须保持一致」。上游契约不带国籍字段，故配置化，取我方客源国 CN。
     */
    @Value("${supplier.clwy.country-code:CN}")
    private String countryCode;

    /**
     * resolve 管线开关（docs/product-identity.md §3）。默认 false 为安全侧兜底（§3.3.3）。
     *
     * <p>闸口三项声明（PROJECT.md §3.8.5）：
     * <ul>
     *   <li><b>误开的后果</b>：容差门（R-3.3）失效场景下可能按更高价自动成交</li>
     *   <li><b>误关的后果</b>：本家报价码是<b>分代轮换</b>（cursor 取证：60 天 58 次重放仅 4 次成功、
     *       93% 撞 500 No Availability），误关即绝大多数「旧列表点击」直接死。不资损，但可订率塌方
     *       ——本家比另三家更依赖 resolve</li>
     *   <li><b>生效执行面</b>：全部承载 /client/spa/check 流量的节点，仅 clwy 链路</li>
     * </ul>
     */
    @Value("${supplier.clwy.resolve-enabled:false}")
    private boolean resolveEnabled;

    /** resolve 换票的价格容差（R-3.3），口径与另三家同规，取值域 [0, 0.2] */
    @Value("${supplier.clwy.resolve-price-tolerance:0.02}")
    private double resolvePriceTolerance;

    /** resolve 换票容差的绝对帽（分），与比例容差取严（issue #59）。兜底 20 元从严 */
    @Value("${supplier.clwy.resolve-price-cap-cents:2000}")
    private int resolvePriceCapCents;

    @Override
    public void afterPropertiesSet() {
        if (resolvePriceTolerance < 0 || resolvePriceTolerance > 0.2) {
            throw new IllegalStateException(
                    "supplier.clwy.resolve-price-tolerance must be between 0 and 0.2, but was " + resolvePriceTolerance);
        }
        if (resolvePriceCapCents < 0 || resolvePriceCapCents > 100000) {
            throw new IllegalStateException(
                    "supplier.clwy.resolve-price-cap-cents must be between 0 and 100000, but was " + resolvePriceCapCents);
        }
        log.info("差旅无忧接入配置: urlHost={}, credentialsConfigured={}, currency={}, countryCode={}",
                urlHost, isConfigured(), currency, countryCode);
    }

    /** 凭证是否齐备；未配置时本家链路应如实回报不可用，而非带着空凭据去打供应商 */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(wid) && StringUtils.isNotBlank(apiKey);
    }

    public String getWid() {
        return wid;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getUrlHost() {
        return urlHost;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCountryCode() {
        return countryCode;
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
    public void setWid(String wid) {
        this.wid = wid;
    }

    /** 仅供测试构造场景使用 */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /** 仅供测试构造场景使用 */
    public void setUrlHost(String urlHost) {
        this.urlHost = urlHost;
    }

    /** 仅供测试构造场景使用 */
    public void setResolveEnabled(boolean resolveEnabled) {
        this.resolveEnabled = resolveEnabled;
    }
}
