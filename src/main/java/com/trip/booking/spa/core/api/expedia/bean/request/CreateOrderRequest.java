package com.trip.booking.spa.core.api.expedia.bean.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Expedia Rapid 下单请求体。
 *
 * <p>字段名与 Rapid 契约一致（下划线风格），故不改成驼峰，以便与 Expedia 文档逐字对照。
 *
 * <p><b>请求发往哪里</b>：不拼 {@code /v3/itineraries}，而是把验价响应中
 * {@code links.book.href} 整串作为路径拼在 host 之后。该 href 自带 token，
 * 承载了本次报价的全部上下文。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    /**
     * 我方业务单号，Expedia 按此字段去重，并支持凭它反查订单。
     * <p>这是本服务幂等与「超时后确证」的基石：下单结果不确定时，可用同一单号查回真实状态。
     */
    private String affiliate_reference_id;

    /**
     * 是否仅锁单不成交。固定 false——本服务不使用两阶段锁单。
     */
    private Boolean hold;

    /**
     * 联系邮箱。Expedia 用它发确认函，也是反查订单的必需参数之一。
     */
    private String email;

    private Phone phone;

    private List<Room> rooms;

    private List<Payment> payments;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Phone {
        private String country_code;
        private String area_code;
        private String number;
    }

    /**
     * 一间房一个条目，条目数必须等于订房间数。
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Room {
        private String given_name;
        private String family_name;
        /** 特殊要求，可空 */
        private String special_request;
    }

    /**
     * 支付方式。本项目合同为 EAC（Expedia Affiliate Collect），
     * 固定取 {@code affiliate_collect}——由我方向旅客收款，Expedia 按账期向我方开账，
     * 故<b>不向 Expedia 传递任何卡片信息</b>。
     *
     * <p>该判断有三方一致证据：① 技术研讨会材料把 EAC 即时下单的请求体标注为
     * {@code affiliate_collect}；② 同材料定义 Expedia Collect 为「Expedia Group
     * <i>或 partner</i> 向旅客收款」，partner 即我方；③ tg-trip-cursor 生产代码对 elong
     * 采用同一模型（{@code isGuaranteeOrCharged=true} 表示我方已收款），并留有实战注释：
     * 「标了已收款就绝不能再带卡信息，否则供应商会拒单」。
     *
     * <p>因此本类<b>有意不含</b>卡号、CVV、有效期字段——既非契约所需，
     * 且一旦存在便会沿日志与快照扩散，形成不必要的敏感数据面。
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payment {
        /** 固定 affiliate_collect，见 {@link Payment} 类注释 */
        private String type;
    }
}
