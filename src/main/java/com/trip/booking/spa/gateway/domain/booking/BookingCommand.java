package com.trip.booking.spa.gateway.domain.booking;

import java.util.Objects;

/**
 * 下单指令：②③层的入参，不是对外 JSON（那是 ① 的 BookingReq，由 BookingMapping 翻译）。
 *
 * <p><b>{@link #offerId()} 是下单的必要输入</b>：句柄背后是供应商内部的下单凭据，由网关
 * 自持。不重新验价取凭据，是因为重新验价会得到新报价，可能与调用方已向旅客展示的价格
 * 不一致——凭据必须来自展示给旅客的那一次验价。
 */
public final class BookingCommand {

    private final int supplierId;
    private final String orderId;
    private final String offerId;
    private final String supplierHotelId;
    private final String supplierProductId;
    private final String personName;
    private final String contactName;
    private final String contactPhone;
    private final String checkIn;
    private final String checkOut;
    private final Integer roomNum;
    private final Integer totalPrice;
    private final Integer settlePrice;

    private BookingCommand(Builder b) {
        this.supplierId = b.supplierId;
        this.orderId = Objects.requireNonNull(b.orderId, "我方单号必填：它是幂等与对账的坐标");
        this.offerId = b.offerId;
        this.supplierHotelId = b.supplierHotelId;
        this.supplierProductId = b.supplierProductId;
        this.personName = b.personName;
        this.contactName = b.contactName;
        this.contactPhone = b.contactPhone;
        this.checkIn = b.checkIn;
        this.checkOut = b.checkOut;
        this.roomNum = b.roomNum;
        this.totalPrice = b.totalPrice;
        this.settlePrice = b.settlePrice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int supplierId() {
        return supplierId;
    }

    public String orderId() {
        return orderId;
    }

    /** 验价签发的报价句柄。适配层凭它取回自家下单凭据，禁止解析其形态 */
    public String offerId() {
        return offerId;
    }

    public String supplierHotelId() {
        return supplierHotelId;
    }

    public String supplierProductId() {
        return supplierProductId;
    }

    public String personName() {
        return personName;
    }

    public String contactName() {
        return contactName;
    }

    public String contactPhone() {
        return contactPhone;
    }

    public String checkIn() {
        return checkIn;
    }

    public String checkOut() {
        return checkOut;
    }

    public Integer roomNum() {
        return roomNum;
    }

    public Integer totalPrice() {
        return totalPrice;
    }

    public Integer settlePrice() {
        return settlePrice;
    }

    public static final class Builder {

        private int supplierId;
        private String orderId;
        private String offerId;
        private String supplierHotelId;
        private String supplierProductId;
        private String personName;
        private String contactName;
        private String contactPhone;
        private String checkIn;
        private String checkOut;
        private Integer roomNum;
        private Integer totalPrice;
        private Integer settlePrice;

        private Builder() {
        }

        public Builder supplierId(int v) {
            this.supplierId = v;
            return this;
        }

        public Builder orderId(String v) {
            this.orderId = v;
            return this;
        }

        public Builder offerId(String v) {
            this.offerId = v;
            return this;
        }

        public Builder supplierHotelId(String v) {
            this.supplierHotelId = v;
            return this;
        }

        public Builder supplierProductId(String v) {
            this.supplierProductId = v;
            return this;
        }

        public Builder personName(String v) {
            this.personName = v;
            return this;
        }

        public Builder contactName(String v) {
            this.contactName = v;
            return this;
        }

        public Builder contactPhone(String v) {
            this.contactPhone = v;
            return this;
        }

        public Builder checkIn(String v) {
            this.checkIn = v;
            return this;
        }

        public Builder checkOut(String v) {
            this.checkOut = v;
            return this;
        }

        public Builder roomNum(Integer v) {
            this.roomNum = v;
            return this;
        }

        public Builder totalPrice(Integer v) {
            this.totalPrice = v;
            return this;
        }

        public Builder settlePrice(Integer v) {
            this.settlePrice = v;
            return this;
        }

        public BookingCommand build() {
            return new BookingCommand(this);
        }
    }
}
