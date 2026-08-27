package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 飞猪（淘宝 TOP）接入配置。凭据一律经 FLIGGY_* 环境变量注入（§3.5.1，名单在
 * {@code .env.example}）；契约与网关事实见 docs/fliggy/distribution-api.md §1。
 * session 90 天且只能人工重授权，重授权后 {@link #sessionAuthorizedAt} 必须同批更新，
 * 否则到期监控失真。
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "supplier.fliggy")
public class FliggyProperties {

    /** TOP 应用 appKey */
    private String appKey;

    /** TOP 应用 secret，签名用 */
    private String secret;

    /** OAuth session（access_token）。过期表现为全线 error_response（如 code 27） */
    private String session;

    /**
     * TOP 网关。无兜底（§3.3.3：禁止以生产实际值作兜底）——飞猪没有已知的沙箱网关，
     * 漏配就该表现为「凭据未配置」而不是悄悄打生产。取值见 .env.example
     * （生产=https://eco.taobao.com/router/rest，gw.api 已死）。
     */
    private String urlHost;

    /** 分销渠道标识，查价/验价/下单/查单/取消全链路必带 */
    private String distributor;

    /** 上次人工授权日期（yyyy-MM-dd）。重授权后必须同步改这个环境变量，否则到期监控失真 */
    private String sessionAuthorizedAt;

    /** session 有效期天数。TOP 授权响应的 expires_in（cursor 实证 90 天） */
    private int sessionTtlDays = 90;

    /** 四样缺一即不可调用（网关无兜底）；接入期未配置是正常态，调用方按「凭据未配置→确定失败」处理 */
    public boolean isConfigured() {
        return StringUtils.isNoneBlank(appKey, secret, session, urlHost);
    }

    @PostConstruct
    void logStartupState() {
        log.info("飞猪接入配置: urlHost={}, credentialsConfigured={}, distributor={}, "
                        + "sessionAuthorizedAt={}, sessionTtlDays={}",
                urlHost, isConfigured(), StringUtils.defaultIfBlank(distributor, "<未配置>"),
                StringUtils.defaultIfBlank(sessionAuthorizedAt, "<未配置>"), sessionTtlDays);
    }
}
