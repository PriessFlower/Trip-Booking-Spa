package com.trip.booking.spa.gateway.adapter.inbound.rest.controller;

import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉住多供应商查价的分态合并取<b>安全侧</b>：只要还有一家没问出结果，
 * 整体就不能对上游说「都没有」。
 *
 * <p>误判代价不对称：把「没问出来」说成「都没有」，上游会据此劝退旅客，
 * 而房其实还在；反过来只是让上游多重试一次。
 */
class PricingOutcomeMergeTest {

    /** 有一家有货即有货 */
    @Test
    void anyAvailableWins() {
        assertEquals(PricingOutcome.AVAILABLE, SpaController.mergeOutcomes(
                List.of(PricingOutcome.NO_INVENTORY, PricingOutcome.AVAILABLE)));
        assertEquals(PricingOutcome.AVAILABLE, SpaController.mergeOutcomes(
                List.of(PricingOutcome.INDETERMINATE, PricingOutcome.AVAILABLE)));
    }

    /** 无货 + 没问出来 = 没问出来，不得塌成无货 */
    @Test
    void oneIndeterminateBlocksNoInventory() {
        assertEquals(PricingOutcome.INDETERMINATE, SpaController.mergeOutcomes(
                List.of(PricingOutcome.NO_INVENTORY, PricingOutcome.INDETERMINATE)));
    }

    /** 全部明确无货才是无货 */
    @Test
    void allNoInventoryIsNoInventory() {
        assertEquals(PricingOutcome.NO_INVENTORY, SpaController.mergeOutcomes(
                List.of(PricingOutcome.NO_INVENTORY, PricingOutcome.NO_INVENTORY)));
    }

    /** 一个供应商都没有：空请求不构成「确实没有」的证据 */
    @Test
    void emptyRequestIsIndeterminate() {
        assertEquals(PricingOutcome.INDETERMINATE, SpaController.mergeOutcomes(List.of()));
    }
}
