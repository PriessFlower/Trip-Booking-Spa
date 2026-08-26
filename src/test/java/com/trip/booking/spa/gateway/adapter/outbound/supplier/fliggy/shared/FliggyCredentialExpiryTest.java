package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 到期推算：授权日 00:00 UTC + 有效期天数——比真实到期最多早一天，方向刻意保守
 * （宁可早喊人，不可晚断线）。未配置/配错格式一律 null（采样器跳过并告警日志），
 * 绝不出假到期时间。
 */
class FliggyCredentialExpiryTest {

    private static FliggyCredentialExpiry withAuthorizedAt(String date) {
        FliggyProperties properties = new FliggyProperties();
        properties.setSessionAuthorizedAt(date);
        return new FliggyCredentialExpiry(properties);
    }

    @Test
    @DisplayName("授权 2026-08-10 + 90 天 → 2026-11-08T00:00Z（cursor 实证的那次授权）")
    void expiryIsAuthorizedDatePlusTtl() {
        assertEquals(Instant.parse("2026-11-08T00:00:00Z"),
                withAuthorizedAt("2026-08-10").expiresAt());
        assertEquals(SupplierSourceEnum.FLIGGY, withAuthorizedAt("2026-08-10").supplier());
    }

    @Test
    @DisplayName("未配置 → null；配错格式 → null（假数比没数更糟）")
    void missingOrMalformedYieldsNull() {
        assertNull(withAuthorizedAt(null).expiresAt());
        assertNull(withAuthorizedAt("  ").expiresAt());
        assertNull(withAuthorizedAt("08/10/2026").expiresAt());
    }
}
