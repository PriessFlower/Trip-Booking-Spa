package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 一段退改。{@code endDate} 是该段的<b>右边界</b>：在它之前取消收 {@code penalty}；
 * 最后一段之后不可取消。
 *
 * <p>时间有两份：{@code endDate} 是<b>北京时间</b>，{@code endDateLocal} 是酒店当地时间。
 * 本仓取前者——它有唯一时区可锚（Asia/Shanghai），而后者的"当地"是哪个时区报文里没给，
 * 拿它当绝对时刻就是在猜。
 *
 * <p>{@code penalty} 是<b>绝对金额</b>（分，币种同报价），不是比例，且<b>可能超过我方总价</b>：
 * 2026-09-14 生产实测 2,584 个样本里，罚金/逐日价之和 集中在 1.07~1.09，即美团是按它自己的
 * 售价算罚金，而我们拿到的是底价。折成"扣款比例"会得出 >100% 的荒唐值，故一律按定额透出。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeituanCpApply {

    /** 该段截止时间（北京时间），格式 MM/dd/yyyy HH:mm */
    @JsonProperty("endDate")
    private String endDate;

    /** 该段截止时间（酒店当地时间），同格式。本仓不用——报文没给这个"当地"是哪个时区 */
    @JsonProperty("endDateLocal")
    private String endDateLocal;

    /** 该段内取消收取的罚金（分）。官方字段表把 refound 标为已停用，故只认这个 */
    @JsonProperty("penalty")
    private Long penalty;
}
