package com.trip.booking.spa.gateway.domain.order;

import java.util.Objects;

/**
 * 查单指令：②③层的入参，不是对外 JSON（那是 ① 的 OrderQueryReq，由 OrderQueryMapping 翻译）。
 *
 * <p>坐标语义与取消一致：我方单号 {@link #orderId()} 是唯一必填坐标，供应商单号可选。
 * 理由在查单这里尤其硬——查单最要紧的用途是下单回报 {@code UNKNOWN} 后的确证，
 * 而那种场景下上游<b>恰恰没有</b>供应商单号：正因为拿不到响应才需要查单。
 */
public final class OrderQueryCommand {

    private final int supplierId;
    private final String orderId;
    private final String supplierOrderId;

    private OrderQueryCommand(int supplierId, String orderId, String supplierOrderId) {
        this.supplierId = supplierId;
        this.orderId = Objects.requireNonNull(orderId, "我方单号是查单的唯一必填坐标");
        this.supplierOrderId = supplierOrderId;
    }

    public static OrderQueryCommand of(int supplierId, String orderId, String supplierOrderId) {
        return new OrderQueryCommand(supplierId, orderId, supplierOrderId);
    }

    public int supplierId() {
        return supplierId;
    }

    public String orderId() {
        return orderId;
    }

    /** 可为 null，见类注释 */
    public String supplierOrderId() {
        return supplierOrderId;
    }
}
