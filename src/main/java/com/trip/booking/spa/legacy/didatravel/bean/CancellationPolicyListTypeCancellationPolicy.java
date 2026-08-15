package com.trip.booking.spa.legacy.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 产品取消政策.
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
public class CancellationPolicyListTypeCancellationPolicy {
    private BigDecimal Amount;
    private String FromDate;
}
