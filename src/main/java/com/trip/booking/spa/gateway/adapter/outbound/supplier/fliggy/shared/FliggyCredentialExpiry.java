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
 * 飞猪 session 的到期时间供给：上次人工授权日期 + 有效期天数。
 *
 * <p>这是 {@code SupplierIdentityProfile.FLIGGY} 申报 HUMAN_ONLY 的兑现（启动校验
 * 要求本 bean 存在）。cursor 的教训：session 过期无人知晓，被当「供应商集成死」
 * 查了两个月——到期是确定会发生的事，必须有剩余天数指标（<14 天告警）。
 *
 * <p>到期时刻按授权日 00:00 UTC + 天数计——比真实到期时刻（授权当天的某个时分）
 * 早最多一天，方向刻意保守：宁可早一天喊人，不可晚一天断线。
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
