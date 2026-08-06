package com.trip.booking.spa.core.api.dto;

import com.trip.booking.spa.core.api.dto.BedCheckInfo;
import com.trip.booking.spa.core.api.dto.BookingRule;
import com.trip.booking.spa.core.api.dto.CancelPolicy;
import com.trip.booking.spa.core.api.dto.Meal;
import com.trip.booking.spa.core.api.dto.PriceInfo;
import com.trip.booking.spa.core.api.dto.ProductInfo;
import com.trip.booking.spa.core.api.dto.Room;
import lombok.Data;

import java.util.List;

@Data
public class ProductRespCacheDTO {

    public String hotelId;
    public String productId;
    private String expediaRoomId;//expedia房型id
    public Integer supplierId;
    public String planSession;
    /**
     * 总价
     */
    private Integer totalPrice;
    /**
     * 总税费 expedia专用
     */
    private Integer totalTaxes;
    /**
     * 总房价 expedia专用
     */
    private Integer roomTotalPrice;
    /**
     * 酒店一次性收取费用 每日总价+酒店一次性收取费用=线上支付总价 expedia专用
     */
    private Integer stayPrice;
    /**
     * 线下支付金额 expedia专用
     */
    private Integer storePayPrice;
    /**
     * 线下支付金额币种
     */
    private String storePayCurrency;
    /**
     * 佣金
     */
    private Integer brokerage;
    /**
     * 外币币种
     */
    private String currencyType;
    /**
     * 产品基本信息
     */
    public ProductInfo productInfo;
    /**
     * 总价
     */
    public Room room;
    /**
     * 外币币种
     */
    public String currency;
    /**
     * 规则
     */
    public List<BookingRule> bookingRule;
    /**
     * 餐食
     */
    public Meal meal;
    /**
     * 取消政策
     */
    public List<CancelPolicy> cancelPolicy;
//    /**
//     * 价格
//     */
//    public List<PriceInfo> priceInfos;
    /**
     * 最大入住人数
     */
    private Integer maxOccupancy;
    /**
     * hotel_package-打包价 hotel_only-零售价
     */
    private String priceFlag;
    /**
     * 专属分销标识，可能是高佣金 true 是   false 否
     */
    private boolean distribution;
    /**
     * 床型选择信息
     */
    private List<BedCheckInfo> bedCheckInfos;

}
