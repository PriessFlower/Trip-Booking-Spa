package com.trip.booking.spa.gateway.domain.supplier;

/**
 * 供应商凭据的续期档位——接入申报的一部分（{@link SupplierIdentityProfile}），
 * 回答「这家的鉴权凭据会不会过期、过期了谁来救」。
 *
 * <p>为什么这是申报项而不是实现细节：cursor 的飞猪 session（OAuth，90 天）过期后，
 * 全线 TOP 调用报 error_response，被当成「供应商集成死了/没货」查了两个月
 * （2026-06 ~ 08-10）——病根不是没人会修，是<b>系统里没有任何地方表达过
 * 「这家的凭据是会过期的」</b>，于是没有到期监控、没有告警、排障方向全错。
 *
 * <p>纪律：
 * <ul>
 *   <li>申报 {@link #HUMAN_ONLY} 的家<b>必须</b>同时提供 {@link CredentialExpiry}
 *       ——启动即校验（CredentialExpirySampler），申报了却不供到期时间，
 *       等于承诺了监控却不给数据，直接拒绝启动</li>
 *   <li>凭据过期在三态分类里属 AUTH_CONFIG（我方配置病）：永不拉黑、永不判无货、
 *       必须告警到人</li>
 * </ul>
 */
public enum CredentialRenewal {

    /** 每请求现签（MD5/SHA 摘要），无会话、无到期。Expedia、艺龙均属此档 */
    STATELESS,

    /** 有会话但可程序自续（如 clwy 的 JWT：30 分钟，401 即重取）。自续失败仍须告警 */
    SELF_RENEWING,

    /**
     * 只能人工重授权（如飞猪 OAuth session：90 天，无自动刷新）。
     * 到期是<b>确定会发生的事</b>，不是意外——必须有剩余天数指标与提前告警。
     */
    HUMAN_ONLY;

    /** 标签值一律小写，与 Prometheus 惯例一致 */
    public String tagValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
