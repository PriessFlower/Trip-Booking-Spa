package com.trip.booking.spa.gateway.domain.pricing;

import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;

import java.util.List;
import java.util.Objects;

/**
 * 验价指令：②③ 层的入参，不是对外 JSON（那是 ① 的 CheckPriceCommand，由 CheckPriceMapping 翻译）。
 *
 * <p>与 {@link PriceQuery} 一样只带<b>一家</b>供应商的坐标：验价本就是对着一个具体产品问的。
 *
 * <p>{@link #seenPrice()} 可空——它是上游展示给旅客的那个价，用于判断验后是否变价。
 * 曾经必填（Lombok {@code @NonNull} → 反序列化即抛 → HTTP 400），把整条链路卡死过，
 * 详见 ① 的 CheckPriceCommand 字段注释。
 */
public final class CheckPriceCommand {

    private final int supplierId;
    private final String supplierHotelId;
    private final String supplierProductId;
    private final String productKey;
    private final VerifyLevel verifyLevel;
    private final String checkIn;
    private final String checkOut;
    private final int roomNum;
    private final Integer seenPrice;
    private final Integer adultCount;
    private final int childNum;
    private final List<Integer> childAges;
    private final String priceFlag;
    private final String language;
    private final String bedId;
    private final String currency;

    private CheckPriceCommand(Builder b) {
        this.supplierId = b.supplierId;
        this.supplierHotelId = b.supplierHotelId;
        this.supplierProductId = b.supplierProductId;
        this.productKey = b.productKey;
        this.verifyLevel = b.verifyLevel;
        this.checkIn = Objects.requireNonNull(b.checkIn, "入住日期必填");
        this.checkOut = Objects.requireNonNull(b.checkOut, "离店日期必填");
        this.roomNum = b.roomNum <= 0 ? 1 : b.roomNum;
        this.seenPrice = b.seenPrice;
        this.adultCount = b.adultCount;
        this.childNum = b.childNum == null ? 0 : b.childNum;
        this.childAges = b.childAges == null ? List.of() : List.copyOf(b.childAges);
        this.priceFlag = b.priceFlag;
        this.language = b.language;
        this.bedId = b.bedId;
        this.currency = b.currency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int supplierId() {
        return supplierId;
    }

    public String supplierHotelId() {
        return supplierHotelId;
    }

    public String supplierProductId() {
        return supplierProductId;
    }

    public String productKey() {
        return productKey;
    }

    public VerifyLevel verifyLevel() {
        return verifyLevel;
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

    /** 可空：上游展示给旅客的价，用于判断验后是否变价 */
    public Integer seenPrice() {
        return seenPrice;
    }

    public Integer adultCount() {
        return adultCount;
    }

    /** 归一为 0：缺儿童数不该让调用方各自判 null（与 {@link #roomNum()} 同规） */
    public int childNum() {
        return childNum;
    }

    public List<Integer> childAges() {
        return childAges;
    }

    public String priceFlag() {
        return priceFlag;
    }

    public String language() {
        return language;
    }

    public String bedId() {
        return bedId;
    }

    public String currency() {
        return currency;
    }

    /** 换一张票再验（resolve 换票后重打）：只换产品码，其余不动 */
    public CheckPriceCommand withSupplierProductId(String newProductId) {
        return toBuilder().supplierProductId(newProductId).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .supplierId(supplierId).supplierHotelId(supplierHotelId)
                .supplierProductId(supplierProductId).productKey(productKey)
                .verifyLevel(verifyLevel).checkIn(checkIn).checkOut(checkOut).roomNum(roomNum)
                .seenPrice(seenPrice).adultCount(adultCount).childNum(childNum).childAges(childAges)
                .priceFlag(priceFlag).language(language).bedId(bedId).currency(currency);
    }

    public static final class Builder {

        private int supplierId;
        private String supplierHotelId;
        private String supplierProductId;
        private String productKey;
        private VerifyLevel verifyLevel;
        private String checkIn;
        private String checkOut;
        private int roomNum = 1;
        private Integer seenPrice;
        private Integer adultCount;
        private Integer childNum;
        private List<Integer> childAges;
        private String priceFlag;
        private String language;
        private String bedId;
        private String currency;

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

        public Builder productKey(String v) {
            this.productKey = v;
            return this;
        }

        public Builder verifyLevel(VerifyLevel v) {
            this.verifyLevel = v;
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

        public Builder seenPrice(Integer v) {
            this.seenPrice = v;
            return this;
        }

        public Builder adultCount(Integer v) {
            this.adultCount = v;
            return this;
        }

        public Builder childNum(Integer v) {
            this.childNum = v;
            return this;
        }

        public Builder childAges(List<Integer> v) {
            this.childAges = v;
            return this;
        }

        public Builder priceFlag(String v) {
            this.priceFlag = v;
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

        public Builder currency(String v) {
            this.currency = v;
            return this;
        }

        public CheckPriceCommand build() {
            return new CheckPriceCommand(this);
        }
    }
}
