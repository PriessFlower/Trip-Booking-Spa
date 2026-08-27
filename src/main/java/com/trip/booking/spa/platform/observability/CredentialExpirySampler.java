package com.trip.booking.spa.platform.observability;

import com.trip.booking.spa.gateway.domain.supplier.CredentialExpiry;
import com.trip.booking.spa.gateway.domain.supplier.CredentialRenewal;
import com.trip.booking.spa.gateway.domain.supplier.SupplierIdentityProfile;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商凭据剩余天数 → {@code supplier_credential_days_left} gauge。
 *
 * <p>动机：cursor 的飞猪 session（90 天、只能人工重授权）过期后被当「集成死」查了
 * 两个月——到期是确定会发生的事，靠人脑和日历记必然重演。凭据续期档位由各家在
 * {@link SupplierIdentityProfile} 申报，到期时间由申报 HUMAN_ONLY 的家以
 * {@link CredentialExpiry} bean 供给，本类只做两件事：
 *
 * <ul>
 *   <li><b>启动校验</b>：申报了 {@link CredentialRenewal#HUMAN_ONLY} 却没有对应的
 *       {@link CredentialExpiry} bean，直接拒绝启动——承诺了监控却不给数据来源，
 *       与「配置写了却绑不上」同病（cursor 的 refresh_token 两头断即此），必须硬失败</li>
 *   <li><b>周期采样</b>：每小时把剩余天数推成 gauge（可为负=已过期），
 *       告警规则在 Prometheus 侧（deploy/monitoring/prometheus/rules/spa.yml）</li>
 * </ul>
 *
 * <p>STATELESS 的家不出现在此指标里：无会话即无到期，缺席就是正确状态。
 */
@Slf4j
@Component
public class CredentialExpirySampler {

    private final ObjectProvider<CredentialExpiry> expiries;

    /** 测试经 ReflectionTestUtils 替换为固定时钟 */
    private Clock clock = Clock.systemUTC();

    public CredentialExpirySampler(ObjectProvider<CredentialExpiry> expiries) {
        this.expiries = expiries;
    }

    @PostConstruct
    void declarationsAreBacked() {
        Set<SupplierSourceEnum> provided = expiries.stream()
                .map(CredentialExpiry::supplier).collect(Collectors.toSet());
        Map<SupplierSourceEnum, CredentialRenewal> declared = EnumSet.allOf(SupplierIdentityProfile.class)
                .stream().collect(Collectors.toMap(SupplierIdentityProfile::supplier,
                        SupplierIdentityProfile::credentialRenewal));
        requireExpirySources(declared, provided);
    }

    /** 拆成纯函数以便直测：现网还没有 HUMAN_ONLY 的家，走枚举无法测到抛出分支 */
    static void requireExpirySources(Map<SupplierSourceEnum, CredentialRenewal> declared,
                                     Set<SupplierSourceEnum> provided) {
        for (Map.Entry<SupplierSourceEnum, CredentialRenewal> e : declared.entrySet()) {
            if (e.getValue() == CredentialRenewal.HUMAN_ONLY && !provided.contains(e.getKey())) {
                throw new IllegalStateException("供应商 " + e.getKey()
                        + " 申报了凭据 HUMAN_ONLY（人工续期）却未提供 CredentialExpiry bean——"
                        + "到期监控没有数据来源，等到期就是又一次『被当集成死查两个月』。"
                        + "实现 CredentialExpiry 并注册为 bean（见 CredentialRenewal 的纪律）");
            }
        }
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 3_600_000)
    public void sample() {
        expiries.forEach(expiry -> {
            if (expiry.expiresAt() == null) {
                // 授权信息未配置（接入期正常态）：跳过不出指标——假到期时间比没数更糟。
                // 日志保持可见，接入验收以「指标出数」为准
                log.warn("[credential] 供应商 {} 已申报凭据会过期，但授权日期未配置，"
                        + "到期监控暂无数据来源", expiry.supplier());
                return;
            }
            SupplierIdentityProfile profile = SupplierIdentityProfile.forCode(expiry.supplier().getCode());
            long daysLeft = Duration.between(clock.instant(), expiry.expiresAt()).toDays();
            Map<String, Object> tags = MetricTags.of(expiry.supplier());
            tags.put(MetricTags.RENEWAL, profile.credentialRenewal().tagValue());
            Monitor.recordValue(MetricNames.SUPPLIER_CREDENTIAL_DAYS_LEFT, tags, (int) daysLeft);
        });
    }
}
