package com.trip.booking.spa.cli.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceInfo implements Serializable{
    private static final long serialVersionUID = 7009692878190650106L;

    /**
     * 日期
     */
    private String date;
    /**
     * 价格：总费用
     */
    private Integer price;
    /**
     * 税费 expedia专用
     */
    private Integer taxes;
    /**
     * 房价 expedia专用
     */
    private Integer roomPrice;

}
