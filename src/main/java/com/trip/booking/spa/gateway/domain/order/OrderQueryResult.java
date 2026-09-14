package com.trip.booking.spa.gateway.domain.order;

import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.booking.OrderState;

import java.util.Objects;

/**
 * 查单结果：②③层的出参。对外 JSON 的形状（订单状态那张数字码表）在 ① 的
 * OrderQueryMapping，本类只承载事实。
 *
 * <p>三态由工厂钉死（{@link #found}／{@link #notFound}／{@link #indeterminate}），
 * presence 不可能为 null——此前模板靠运行期检查"实现方漏填 presence"再兜回
 * INDETERMINATE，现在这类遗忘在构造上就不成立。
 *
 * <p>金额字段沿用上游既有取值与单位原样透传，本次只换持有方式、不动口径；
 * 取消能力已改用 {@code Money}，查单是否跟进另议（改口径要先确认上游怎么读）。
 */
public final class OrderQueryResult {

    private final OrderPresence presence;
    private final String message;
    private final String supplierOrderId;
    private final String supplierProductId;
    private final Integer totalPrice;
    private final Integer settlePrice;
    private final String createTime;
    /** 可为 null：映射不出就不取值，原文留在 {@link #supplierOrderStatus()}，见 {@link OrderState} */
    private final OrderState state;
    private final String supplierOrderStatus;
    private final String confirmationNumber;

    private OrderQueryResult(OrderPresence presence, String message, String supplierOrderId,
                             String supplierProductId, Integer totalPrice, Integer settlePrice,
                             String createTime, OrderState state, String supplierOrderStatus,
                             String confirmationNumber) {
        this.presence = Objects.requireNonNull(presence);
        this.message = message;
        this.supplierOrderId = supplierOrderId;
        this.supplierProductId = supplierProductId;
        this.totalPrice = totalPrice;
        this.settlePrice = settlePrice;
        this.createTime = createTime;
        this.state = state;
        this.supplierOrderStatus = supplierOrderStatus;
        this.confirmationNumber = confirmationNumber;
    }

    /** 查到了。state 允许为 null（识别不出），此时 supplierOrderStatus 必须带原文 */
    public static Builder found() {
        return new Builder(OrderPresence.FOUND);
    }

    /** 供应商<b>明确</b>回答没有这张单。含糊、超时、解析不了一律用 {@link #indeterminate} */
    public static OrderQueryResult notFound(String message) {
        return new Builder(OrderPresence.NOT_FOUND).message(message).build();
    }

    /** 没能确证订单在不在。上游据此重试查单，不得当作"不存在"去退款 */
    public static OrderQueryResult indeterminate(String message) {
        return new Builder(OrderPresence.INDETERMINATE).message(message).build();
    }

    public OrderPresence presence() {
        return presence;
    }

    public String message() {
        return message;
    }

    public String supplierOrderId() {
        return supplierOrderId;
    }

    public String supplierProductId() {
        return supplierProductId;
    }

    public Integer totalPrice() {
        return totalPrice;
    }

    public Integer settlePrice() {
        return settlePrice;
    }

    public String createTime() {
        return createTime;
    }

    /** 可为 null，见字段注释 */
    public OrderState state() {
        return state;
    }

    public String supplierOrderStatus() {
        return supplierOrderStatus;
    }

    public String confirmationNumber() {
        return confirmationNumber;
    }

    /** 只给 FOUND 用：字段多且各家填的子集不同，逐个具名比长参数表可读 */
    public static final class Builder {

        private final OrderPresence presence;
        private String message;
        private String supplierOrderId;
        private String supplierProductId;
        private Integer totalPrice;
        private Integer settlePrice;
        private String createTime;
        private OrderState state;
        private String supplierOrderStatus;
        private String confirmationNumber;

        private Builder(OrderPresence presence) {
            this.presence = presence;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder supplierOrderId(String supplierOrderId) {
            this.supplierOrderId = supplierOrderId;
            return this;
        }

        public Builder supplierProductId(String supplierProductId) {
            this.supplierProductId = supplierProductId;
            return this;
        }

        public Builder totalPrice(Integer totalPrice) {
            this.totalPrice = totalPrice;
            return this;
        }

        public Builder settlePrice(Integer settlePrice) {
            this.settlePrice = settlePrice;
            return this;
        }

        public Builder createTime(String createTime) {
            this.createTime = createTime;
            return this;
        }

        public Builder state(OrderState state) {
            this.state = state;
            return this;
        }

        public Builder supplierOrderStatus(String supplierOrderStatus) {
            this.supplierOrderStatus = supplierOrderStatus;
            return this;
        }

        public Builder confirmationNumber(String confirmationNumber) {
            this.confirmationNumber = confirmationNumber;
            return this;
        }

        public OrderQueryResult build() {
            return new OrderQueryResult(presence, message, supplierOrderId, supplierProductId,
                    totalPrice, settlePrice, createTime, state, supplierOrderStatus, confirmationNumber);
        }
    }
}
