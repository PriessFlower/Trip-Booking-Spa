package com.bingo.hotel.spa.intl.core.api.didatravel.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 酒店产品中房型信息.
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
public class RoomOccupancyType {
    private Integer ChildCount;
    private Integer AdultCount;
    private Integer RoomNum;
    private List<Integer> ChildAgeDetails;
}
