package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 餐食。
 *
 * <p><b>判据取 {@code ohMealTypeEnum}，不取 {@code count}</b>：官方两处文档对 {@code count=-1}
 * 的说法自相矛盾——批量查价页写"-1 表示无早"，下单前校验页写"-1 表示不确定份数"。
 * 2026-09-14 生产实测 2,926 条产品，{@code ohMealTypeEnum} 干净地二分且与 count 完全一致：
 * {@code NO_BREAKFAST} 1,759 条（count 恒 -1、desc 恒"无早"），{@code BREAKFAST} 1,167 条
 * （count ∈ {1,2,3,4}、desc 恒"早餐"），没有第三种取值、也没有一条越界。
 * 故枚举是可靠的判据，而 -1 的歧义只在没有枚举时才需要面对（那时一律 UNKNOWN）。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeituanMealType {

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("desc")
    private String desc;

    /** {@code NO_BREAKFAST} / {@code BREAKFAST}。官方参数表未列，线上一直在发 */
    @JsonProperty("ohMealTypeEnum")
    private String ohMealTypeEnum;
}
