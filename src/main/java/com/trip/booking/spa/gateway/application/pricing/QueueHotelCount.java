package com.trip.booking.spa.gateway.application.pricing;

import lombok.Data;

/**
 * 刷价清单的两个店数：清单里共多少家、其中当前还有货的多少家（F-2.6 的度量）。
 *
 * <p>为什么按<b>家</b>不按行：行数只说明我们在刷多少次，覆盖面要看有多少家店真出得了货。
 * 一家店在清单里有多个住期行（T+0~30），只要任一住期在业务档就算这家有货。
 *
 * <p>取值口径写在各家 Mapper XML 的同一条 SQL 里（六家逐字同构），本类只搬运。
 */
@Data
public class QueueHotelCount {

    /** 清单里的酒店数（COUNT DISTINCT sh_id），即分母 */
    private int hotels;

    /** 其中当前有货的酒店数，即分子 */
    private int onsaleHotels;
}
