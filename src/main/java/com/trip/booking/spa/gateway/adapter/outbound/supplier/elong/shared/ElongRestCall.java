package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次艺龙 REST 调用的业务部分：method + data JSON 原文。
 * 六个公共参数（user/method/timestamp/format/data/signature）由通道层在发出时组装，
 * timestamp 与签名必须同刻生成，故不在此提前持有。
 */
@Getter
@AllArgsConstructor
public class ElongRestCall {

    /** 如 hotel.detail / hotel.data.validate */
    private final String method;

    /** data 参数 JSON 原文（信封 {Version,Local,Request}），未编码 */
    private final String dataJson;
}
