package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 飞猪（淘宝 TOP）接入配置。凭据一律经环境变量注入（PROJECT.md §3.5.1），
 * 变量名登记在 {@code .env.example}。
 *
 * <p>契约依据 docs/fliggy/distribution-api.md；网关必须是 {@code eco.taobao.com}——
 * 旧网关 {@code gw.api.taobao.com} 已下线（2026-08-10 cursor 实测直连超时，
 * 6 月「集成死」的真凶）。
 *
 * <p>session 是 TOP OAuth 授权产物：90 天有效、<b>无自动刷新、只能人工重授权</b>。
 * {@link #sessionAuthorizedAt} 记录上次授权日期（重授权后必须同步更新此环境变量），
 * 到期监控（{@code FliggyCredentialExpiry}）以它推算剩余天数——不配则监控无数据来源，
 * 每小时一条告警日志。
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

    /** TOP 网关。默认即唯一活着的网关，可经环境变量覆盖以备再迁移 */
    private String urlHost = "https://eco.taobao.com/router/rest";

    /** 分销渠道标识，查价/验价/下单/查单/取消全链路必带 */
    private String distributor;

    /** 上次人工授权日期（yyyy-MM-dd）。重授权后必须同步改这个环境变量，否则到期监控失真 */
    private String sessionAuthorizedAt;

    /** session 有效期天数。TOP 授权响应的 expires_in（cursor 实证 90 天） */
    private int sessionTtlDays = 90;

    /** 三样缺一即不可调用；接入期未配置是正常态，调用方按「凭据未配置→确定失败」处理 */
    public boolean isConfigured() {
        return StringUtils.isNoneBlank(appKey, secret, session);
    }

    @PostConstruct
    void logStartupState() {
        log.info("飞猪接入配置: urlHost={}, credentialsConfigured={}, distributor={}, "
                        + "sessionAuthorizedAt={}, sessionTtlDays={}",
                urlHost, isConfigured(), StringUtils.defaultIfBlank(distributor, "<未配置>"),
                StringUtils.defaultIfBlank(sessionAuthorizedAt, "<未配置>"), sessionTtlDays);
    }
}
