package com.trip.booking.spa.cli.dto;

import com.trip.booking.spa.cli.enums.RefundType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelPolicy {

    // 取消规则选项分类: 0-不可取消; 1-可以取消;
    private Integer cancelType;

    // 提前取消天数 0表是当天
    private Integer moveUpCancelDays;

    // 提前取消的时间值。格式为"HH:mm:ss"
    // 例如: cancelType=1，moveUpCancelDays=0，moveUpCancelHour="18:00:00",表示入住日18:00前可以取消；
    private String moveUpCancelHour;

    // 如果罚款是固定收费，则存在，单位：分。例如 3000表示收取30人民币 （境外可能需要根据汇率进行换算）
    private Integer amount;

    // 如果罚款是基于晚上的，比如预订3晚，则收取前2晚罚金
    private Integer nights;

    //如果罚款是百分比，则存在，例如 10 表示收取整单的10%
    private Object percent;

    // 取消规则的时区
    private String timeZone;

    // 规则有效期 表示用户距离入住日当天24:00前多少小时可以取消订单，数值表示小时数且必须大于24；
    private int before;

    // 退款类型
    private RefundType type;

    // 当退款类型为扣除固定金额、扣除房费的百分比时，必填。
    private Double value;
}
