package com.trip.booking.spa.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 查询床型类型枚举.
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
public class GetBedTypeListRSSuccess {
    private List<BedTypeList> BedTypes;

}
