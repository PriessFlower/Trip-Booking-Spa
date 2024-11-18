package com.bingo.hotel.spa.intl.cli.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meal {
    public Integer count;//早餐数

    public Integer lunchCount;//午餐数

    public Integer dinnerCount;//晚餐数

    public String mealDesc;//餐食描述
}
