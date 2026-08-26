package com.trip.booking.spa.gateway.domain.cancellation;

import java.util.Objects;

/**
 * 取消指令：②③层的入参，不是对外 JSON（那是 ① 的 CancelReq，由 CancelMapping 翻译）。
 *
 * <p>坐标语义与对外契约一致：我方单号 {@link #orderId()} 是唯一必填坐标；
 * 供应商单号可选——最需要取消的场景恰是下单结果不确定时，那时上游没有供应商单号。
 * 各家实现按自己的接口形态取用（艺龙缺供应商单号时先反查，Expedia 全程用我方单号）。
 */
public final class CancelCommand {

    private final int supplierId;
    private final String orderId;
    private final String supplierOrderId;

    private CancelCommand(int supplierId, String orderId, String supplierOrderId) {
        this.supplierId = supplierId;
        this.orderId = Objects.requireNonNull(orderId, "我方单号是取消的唯一必填坐标");
        this.supplierOrderId = supplierOrderId;
    }

    public static CancelCommand of(int supplierId, String orderId, String supplierOrderId) {
        return new CancelCommand(supplierId, orderId, supplierOrderId);
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
