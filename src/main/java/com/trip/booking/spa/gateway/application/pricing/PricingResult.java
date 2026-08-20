package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;

import java.util.List;

/**
 * 查价结果：分态 + 产品列表。
 *
 * <p>产品列表单独一个字段而不是「空列表即无货」，是因为空列表本身表达不了成因——
 * 那正是 {@link PricingOutcome} 存在的理由。
 *
 * @param outcome  分态，永不为 null
 * @param products 产品列表，永不为 null（非 {@link PricingOutcome#AVAILABLE} 时为空列表）
 */
public record PricingResult(PricingOutcome outcome, List<ProductRespDTO> products) {

    /** 查到可售产品。传入空列表会被纠正为 {@link PricingOutcome#NO_INVENTORY}——分态不能与事实矛盾 */
    public static PricingResult available(List<ProductRespDTO> products) {
        if (products == null || products.isEmpty()) {
            return noInventory();
        }
        return new PricingResult(PricingOutcome.AVAILABLE, products);
    }

    /** 供应商明确回答无可售产品 */
    public static PricingResult noInventory() {
        return new PricingResult(PricingOutcome.NO_INVENTORY, List.of());
    }

    /** 未能得出结论。所有兜底路径都应落到这里 */
    public static PricingResult indeterminate() {
        return new PricingResult(PricingOutcome.INDETERMINATE, List.of());
    }

    /**
     * 按产品列表判分态：非空即 {@link PricingOutcome#AVAILABLE}，空即
     * {@link PricingOutcome#NO_INVENTORY}。
     *
     * <p><b>只允许适配层（③）调用</b>：调用它等于替供应商断言「确实没有」，
     * 而这个断言只有读得懂供应商响应的那一层才有资格下。契约层的兜底用
     * {@link #indeterminate()}。
     */
    public static PricingResult of(List<ProductRespDTO> products) {
        return products == null || products.isEmpty() ? noInventory() : available(products);
    }

    public boolean isAvailable() {
        return outcome == PricingOutcome.AVAILABLE;
    }
}
