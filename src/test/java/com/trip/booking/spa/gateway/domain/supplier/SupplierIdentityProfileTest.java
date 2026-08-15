package com.trip.booking.spa.gateway.domain.supplier;

import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住腐性申报三行制（docs/product-identity.md R-4.1/R-4.2）。
 */
class SupplierIdentityProfileTest {

    /** 仓内每一家供应商都必须有申报——新加供应商忘了申报，这条会拦下 */
    @Test
    void everySupplierIsDeclared() {
        for (SupplierSourceEnum supplier : SupplierSourceEnum.values()) {
            SupplierIdentityProfile profile = SupplierIdentityProfile.forCode(supplier.getCode());
            assertEquals(supplier, profile.supplier());
        }
    }

    /** 未申报的供应商编码必须炸，不给保守默认值——R-4.1 是接入前置门，不是运行时兜底 */
    @Test
    void undeclaredSupplierThrows() {
        assertThrows(IllegalStateException.class, () -> SupplierIdentityProfile.forCode(99999));
    }

    /** 易腐必有 TTL 上限（R-2.2 的执行依据）；稳定则不设上限 */
    @Test
    void perishableHasTtlCapAndStableHasNone() {
        for (SupplierIdentityProfile profile : SupplierIdentityProfile.values()) {
            if (profile.quoteCodeStability() == SupplierIdentityProfile.QuoteCodeStability.PERISHABLE) {
                assertNotNull(profile.tokenTtlCap(), profile + " 申报易腐却没有 TTL 上限");
            } else {
                assertNull(profile.tokenTtlCap(), profile + " 申报稳定不应设 TTL 上限");
            }
        }
    }

    /** 有证据升级前，room_id 未核验的家不得进房型级目录（R-4.3） */
    @Test
    void onlyVerifiedRoomIdEntersRoomLevelCatalog() {
        assertTrue(SupplierIdentityProfile.EXPEDIA.catalogEligibleAtRoomLevel());
        assertTrue(SupplierIdentityProfile.MEITUAN.catalogEligibleAtRoomLevel());
        // ratehawk 没有房型 ID——现货级降级的活样本
        assertEquals(SupplierIdentityProfile.RoomIdStability.ABSENT,
                SupplierIdentityProfile.RATEHAWK.roomIdStability());
        assertTrue(!SupplierIdentityProfile.RATEHAWK.catalogEligibleAtRoomLevel());
        assertTrue(!SupplierIdentityProfile.FASTPAYHOTELS.catalogEligibleAtRoomLevel());
    }

    /** 当前的稳定名单只有拿得出证据的两家（Expedia 沙箱实测、美团供应商文档） */
    @Test
    void stableQuoteCodeListIsEvidenceBacked() {
        for (SupplierIdentityProfile profile : SupplierIdentityProfile.values()) {
            boolean stable = profile.quoteCodeStability() == SupplierIdentityProfile.QuoteCodeStability.STABLE;
            boolean evidenced = profile == SupplierIdentityProfile.EXPEDIA || profile == SupplierIdentityProfile.MEITUAN;
            assertEquals(evidenced, stable, profile + " 的稳定申报与证据清单不一致（R-4.2：无证据一律按易腐）");
        }
    }
}
