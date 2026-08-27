package com.trip.booking.spa.bff.offer;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 报价句柄缓存：Rapid 返回的 price_check / book / cancel 等 href 只存服务端，
 * 前端只拿不透明 token（对齐 gateway-boundary B1 的句柄纪律）。
 *
 * <p>book token 在验价成功时即预先绑定唯一 affiliate_reference_id（TR1）：
 * 同一 token 重复提交下单，复用同一参考 ID，供应商侧幂等去重。
 *
 * <p>内存实现，重启即失效——验收演示链路可接受；正式接入需换持久化。
 */
@Component
public class OfferCache {

    /** 报价 token 有效期，与 Rapid 报价的可验价窗口对齐 */
    private static final long TTL_MILLIS = 30 * 60 * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_TS =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    public static class RateOffer {
        public String priceCheckHref;
        public String paymentOptionsHref;
        public String depositPoliciesHref;
        public String propertyId;
        public String roomId;
        public String rateId;
        public String bedGroupId;
        public String bedDescription;
        /** 该房价的 bed_groups 数量 > 1，即旅客确实在多床型之间做过选择 */
        public boolean bedChoice;
        public String checkin;
        public String checkout;
        /** Rapid occupancy 原文，如 2-7,11；每间一项 */
        public java.util.List<String> occupancies;
        public boolean refundable;
        public String merchantOfRecord;
        /** Rapid cancel_penalties / nonrefundable_date_ranges 原文（JSON 串），随订单落库供确认页/凭证展示 */
        public String cancelPenaltiesJson;
        public String nonrefundableDateRangesJson;
        long expiresAt;
    }

    public static class BookOffer {
        public String bookHref;
        /** 预绑定的唯一 affiliate_reference_id */
        public String orderId;
        public RateOffer rate;
        /** 验价响应的 occupancy_pricing 原文：确认页/凭证的价格快照（CP1/ER6 金额与 API 一致） */
        public String pricingJson;
        /** Payment Options 原文（BP8/BP10 收款方与付款处理国家） */
        public String paymentOptionsJson;
        long expiresAt;
    }

    private final Map<String, RateOffer> rateOffers = new ConcurrentHashMap<>();
    private final Map<String, BookOffer> bookOffers = new ConcurrentHashMap<>();

    public String putRate(RateOffer offer) {
        evictExpired();
        offer.expiresAt = System.currentTimeMillis() + TTL_MILLIS;
        String token = UUID.randomUUID().toString();
        rateOffers.put(token, offer);
        return token;
    }

    public RateOffer getRate(String token) {
        RateOffer offer = token == null ? null : rateOffers.get(token);
        return offer == null || offer.expiresAt < System.currentTimeMillis() ? null : offer;
    }

    public String putBook(String bookHref, RateOffer rate) {
        evictExpired();
        BookOffer offer = new BookOffer();
        offer.bookHref = bookHref;
        offer.rate = rate;
        offer.orderId = newOrderId();
        offer.expiresAt = System.currentTimeMillis() + TTL_MILLIS;
        String token = UUID.randomUUID().toString();
        bookOffers.put(token, offer);
        return token;
    }

    public BookOffer getBook(String token) {
        BookOffer offer = token == null ? null : bookOffers.get(token);
        return offer == null || offer.expiresAt < System.currentTimeMillis() ? null : offer;
    }

    /** TB + UTC 时间戳 + 4 位随机数：全局唯一、人可读 */
    private String newOrderId() {
        return "TB" + ORDER_TS.format(Instant.now()) + String.format("%04d", RANDOM.nextInt(10000));
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, RateOffer>> it = rateOffers.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().expiresAt < now) {
                it.remove();
            }
        }
        for (Iterator<Map.Entry<String, BookOffer>> it = bookOffers.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().expiresAt < now) {
                it.remove();
            }
        }
    }
}
