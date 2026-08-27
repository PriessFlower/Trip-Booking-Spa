package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.trip.booking.spa.gateway.domain.supplier.CredentialExpiry;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * 飞猪 session 的到期供给（HUMAN_ONLY 申报的兑现，启动校验要求本 bean 在场）。
 * 到期时刻按授权日 00:00 UTC + 天数计，比真实到期早最多一天——宁可早喊人，不可晚断线。
 */
@Slf4j
@Component
public class FliggyCredentialExpiry implements CredentialExpiry {

    private final FliggyProperties properties;

    public FliggyCredentialExpiry(FliggyProperties properties) {
        this.properties = properties;
    }

    @Override
    public SupplierSourceEnum supplier() {
        return SupplierSourceEnum.FLIGGY;
    }

    @Override
    public Instant expiresAt() {
        String authorizedAt = properties.getSessionAuthorizedAt();
        if (StringUtils.isBlank(authorizedAt)) {
            return null; // 接入期正常态：采样器跳过并打告警日志，不出假数
        }
        try {
            return LocalDate.parse(authorizedAt)
                    .plusDays(properties.getSessionTtlDays())
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            // 配错格式=监控失真，比未配置更危险（看起来配了）：按未配置对待并点名
            log.error("[credential] FLIGGY_SESSION_AUTHORIZED_AT 不是 yyyy-MM-dd: {}", authorizedAt, e);
            return null;
        }
    }
}
