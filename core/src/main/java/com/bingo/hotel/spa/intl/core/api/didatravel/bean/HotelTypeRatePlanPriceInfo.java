package com.bingo.hotel.spa.intl.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 酒店产品价格信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/11
 * @since : 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class HotelTypeRatePlanPriceInfo {
    private String StayDate;
    private BigDecimal Price;
    private Integer MealAmount;
    private Integer MealType;
    private Integer InventoryCount;
}
