package com.bingo.hotel.spa.intl.cli.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    public String roomId;
    public String roomName;
    /**
     * 吸烟偏好信息：
     * 0-吸烟房；
     * 2-无烟房；
     * 3-均可；
     */
    public Integer smokingPreferences;
    public List<List<Bed>> bedGroups;
    /**
     * 是否有窗：
     * 0-有窗；
     * 1-部分有窗；
     * 2-无窗；
     */
    public Integer window;
    /**
     * 是否允许加床：
     * 0-不可；
     * 1-可以；
     */
    public Integer extraBed;
}
