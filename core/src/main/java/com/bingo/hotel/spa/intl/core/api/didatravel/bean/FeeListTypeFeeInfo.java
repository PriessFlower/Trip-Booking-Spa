package com.bingo.hotel.spa.intl.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 税费信息
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
public class FeeListTypeFeeInfo {
    private BigDecimal Amount;
    private String FeeTypeName;
    private String Currency;
}
