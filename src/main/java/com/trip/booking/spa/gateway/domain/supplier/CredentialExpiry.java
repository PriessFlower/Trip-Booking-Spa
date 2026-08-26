package com.trip.booking.spa.gateway.domain.supplier;

import java.time.Instant;

/**
 * 供应商凭据的到期时间供给——申报 {@link CredentialRenewal#HUMAN_ONLY}（或自续会失败的
 * {@link CredentialRenewal#SELF_RENEWING}）的家实现本接口，作为 Spring bean 注册，
 * 到期监控（CredentialExpirySampler）自动发现并出 {@code supplier_credential_days_left}。
 *
 * <p>到期时间是运行期事实（随人工重授权而变），不能烧进申报枚举——典型实现从该家的
 * 配置读「上次授权日期 + 有效期」（如飞猪：授权日随重授权更新进环境变量）。
 *
 * <p>{@link CredentialRenewal#STATELESS} 的家不实现本接口：无会话即无到期，
 * 缺席就是正确状态。
 */
public interface CredentialExpiry {

    /** 哪家的凭据 */
    SupplierSourceEnum supplier();

    /** 到期时刻，非空——不知道到期时间的实现是没有意义的，宁可不注册 */
    Instant expiresAt();
}
