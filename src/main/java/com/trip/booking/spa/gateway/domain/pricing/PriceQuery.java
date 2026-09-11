package com.trip.booking.spa.gateway.domain.pricing;

import java.util.List;
import java.util.Objects;

/**
 * 查价指令：<b>一次请求 × 一家供应商</b>，②③⑤ 层的入参，不是对外 JSON
 * （那是 ① 的 PriceQuery，由 PricingMapping 翻译）。
 *
 * <p><b>为什么把供应商合进来</b>：上游一次可以问多家，服务按家并行；每家看到的应当只有
 * 自己那一份坐标。此前是 {@code (PriceQuery, Supplier)} 两个参数分开传，而 PriceQuery 里还
 * 带着<b>全部</b>供应商的列表——于是「取错家」在编译期完全不可见。2026-09-11 修的
 * Expedia 当天入住闸口就是这么错的：它取 {@code getSuppliers().get(0)}，多家并行时
 * 拿到的是别家的酒店号（#237）。合成一个之后，这类错误无从写起。
 *
 * <p>刷价路径也因此不必再伪造一个「不带 suppliers 的 PriceQuery」——那个形状是
 * {@code PriceCacheService} javadoc 记的 2026-08-20 事故之源。
 */
public final class PriceQuery {

    private final int supplierId;
    private final String supplierHotelId;
    private final String supplierProductId;

    private final String checkIn;
    private final String checkOut;
    private final int roomNum;
    private final int adultNum;
    private final int childNum;
    private final List<Integer> childAges;
    private final String currency;
    private final String language;
    private final String bedId;
    private final String priceFlag;
    private final List<String> occupancies;

    private PriceQuery(Builder b) {
        this.supplierId = b.supplierId;
        this.supplierHotelId = b.supplierHotelId;
        this.supplierProductId = b.supplierProductId;
        this.checkIn = Objects.requireNonNull(b.checkIn, "入住日期必填");
        this.checkOut = Objects.requireNonNull(b.checkOut, "离店日期必填");
        this.roomNum = b.roomNum <= 0 ? 1 : b.roomNum;
        this.adultNum = b.adultNum <= 0 ? 1 : b.adultNum;
        this.childNum = Math.max(b.childNum, 0);
        this.childAges = b.childAges == null ? List.of() : List.copyOf(b.childAges);
        this.currency = b.currency;
        this.language = b.language;
        this.bedId = b.bedId;
        this.priceFlag = b.priceFlag;
        this.occupancies = b.occupancies == null ? List.of() : List.copyOf(b.occupancies);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int supplierId() {
        return supplierId;
    }

    /** 这一次要问的那家供应商的酒店号。永远只有一个——不存在「列表里第几个」的问题 */
    public String supplierHotelId() {
        return supplierHotelId;
    }

    public String supplierProductId() {
        return supplierProductId;
    }

    public String checkIn() {
        return checkIn;
    }

    public String checkOut() {
        return checkOut;
    }

    public int roomNum() {
        return roomNum;
    }

    public int adultNum() {
        return adultNum;
    }

    public int childNum() {
        return childNum;
    }

    public List<Integer> childAges() {
        return childAges;
    }

    public String currency() {
        return currency;
    }

    public String language() {
        return language;
    }

    public String bedId() {
        return bedId;
    }

    public String priceFlag() {
        return priceFlag;
    }

    public List<String> occupancies() {
        return occupancies;
    }

    /** 同一次请求换一家供应商问：住期与人数不变，只换坐标 */
    public PriceQuery forSupplier(int supplierId, String hotelId, String productId) {
        Builder b = toBuilder();
        b.supplierId = supplierId;
        b.supplierHotelId = hotelId;
        b.supplierProductId = productId;
        return b.build();
    }

    public Builder toBuilder() {
        return new Builder()
                .supplierId(supplierId).supplierHotelId(supplierHotelId).supplierProductId(supplierProductId)
                .checkIn(checkIn).checkOut(checkOut).roomNum(roomNum)
                .adultNum(adultNum).childNum(childNum).childAges(childAges)
                .currency(currency).language(language).bedId(bedId)
                .priceFlag(priceFlag).occupancies(occupancies);
    }

    public static final class Builder {

        private int supplierId;
        private String supplierHotelId;
        private String supplierProductId;
        private String checkIn;
        private String checkOut;
        private int roomNum = 1;
        private int adultNum = 1;
        private int childNum;
        private List<Integer> childAges;
        private String currency;
        private String language;
        private String bedId;
        private String priceFlag;
        private List<String> occupancies;

        private Builder() {
        }

        public Builder supplierId(int v) {
            this.supplierId = v;
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

        public Builder checkIn(String v) {
            this.checkIn = v;
            return this;
        }

        public Builder checkOut(String v) {
            this.checkOut = v;
            return this;
        }

        public Builder roomNum(Integer v) {
            this.roomNum = v == null ? 1 : v;
            return this;
        }

        public Builder adultNum(Integer v) {
            this.adultNum = v == null ? 1 : v;
            return this;
        }

        public Builder childNum(Integer v) {
            this.childNum = v == null ? 0 : v;
            return this;
        }

        public Builder childAges(List<Integer> v) {
            this.childAges = v;
            return this;
        }

        public Builder currency(String v) {
            this.currency = v;
            return this;
        }

        public Builder language(String v) {
            this.language = v;
            return this;
        }

        public Builder bedId(String v) {
            this.bedId = v;
            return this;
        }

        public Builder priceFlag(String v) {
            this.priceFlag = v;
            return this;
        }

        public Builder occupancies(List<String> v) {
            this.occupancies = v;
            return this;
        }

        public PriceQuery build() {
            return new PriceQuery(this);
        }
    }
}
