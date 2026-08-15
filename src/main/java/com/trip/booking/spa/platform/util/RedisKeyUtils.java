package com.trip.booking.spa.platform.util;

import com.google.common.base.Joiner;

public class RedisKeyUtils {

    public static final Joiner JOINER = Joiner.on(":").skipNulls();

    public static final String PRODUCT_TYPE = "P";

    public static final String PRICE = "price";

    public static final String PRODUCT = "product";

    public static final String DOWN_HOTEL = "DOWN";

    public static String buildProductKey(String source, String product, String checkinDate, String checkoutDate) {
        return JOINER.join(PRODUCT_TYPE, product, source, checkinDate, checkoutDate);
    }

    public static String buildPriceKey(String hotelId,String checkIn){
        return JOINER.join(PRICE,hotelId,checkIn);
    }

    public static String buildPriceInfoKey(String hotelId,String productId){
        return JOINER.join(PRODUCT,hotelId,productId);
    }

    public static String buildDownHotelKey(String hotelId,String checkIn){
        return JOINER.join(DOWN_HOTEL,hotelId,checkIn);
    }

}
