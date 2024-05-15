package com.bingo.hotel.spa.intl.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 床型类型.
 *
 * @author : hanJH
 * @version : 1.0 2024/05/15
 * @since : 1.0
 **/
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class BedTypeList {
    private Integer ID;

    private Integer DefaultOccupancy;

    private String Name;

    private String Name_CN;
}
