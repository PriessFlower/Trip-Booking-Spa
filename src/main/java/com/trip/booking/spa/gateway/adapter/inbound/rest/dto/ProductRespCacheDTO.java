package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BedCheckInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRule;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import lombok.Data;

import java.util.List;

@Data
public class ProductRespCacheDTO {

    public String hotelId;
    public String productId;
    /**
     * 卖法等价类键（R-1.1，跨次稳定）。缓存必须原样保存——它是对上游的不透明句柄
     * （gateway-boundary B1）与 resolve 换票的检索键。2026-08-18 发现本 DTO 缺此字段，
     * 刷价写缓存时 productKey 被静默丢弃、缓存读出的产品 productKey=null，直接阻塞
     * cursor 走缓存比价的对接。写读两侧均为 BeanUtils.copyProperties 按名复制,
     * 补上字段即自动透传；旧缓存条目反序列化为 null，随刷价周期自然覆盖。
     */
    private String productKey;
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
