package com.trip.booking.spa.gateway.adapter.inbound.rest.dto;

import com.trip.booking.spa.gateway.domain.product.RefundType;
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

    /**
     * 该段是否<b>确定</b>罚金≥全款：比例≥100%（含艺龙 CutType=4 全额房费），或定额≥总价。
     * 定额两种载体都认：{@code amount}=分（飞猪），{@code value}=元（艺龙 AmountRmb）。
     * 判不出金额语义的形态（首晚/按晚/未知型）一律 false——不确定不许说成确定（R-1.6）。
     */
    public boolean deductsFullPrice(Integer totalCents) {
        if (type == RefundType.DEDUCT_BY_PERCENT) {
            return value != null && value >= 100D;
        }
        if (type == RefundType.DEDUCT_BY_AMOUNT) {
            Integer cents = amount != null ? amount
                    : value == null ? null : com.trip.booking.spa.gateway.domain.shared.Money
                            .toCents(java.math.BigDecimal.valueOf(value));
            return totalCents != null && totalCents > 0 && cents != null && cents >= totalCents;
        }
        return false;
    }
}
