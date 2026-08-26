package com.trip.booking.spa.gateway.domain.supplier;

/**
 * 失败的<b>成因</b>档，与三态 outcome 正交：outcome 说"结果是什么态"，本枚举说
 * "为什么"。只在成因改变处置方式时才立档——立档即立纪律。
 *
 * <p><b>档位随证据增补</b>（与 R-4.6"只登记在产"同理）：cursor 的失败编目有八类
 * （DEAD_PRODUCT/STALE_SNAPSHOT/SOLD_OUT/CAPACITY_SELF/NETWORK/POLICY_REJECT/
 * AUTH_CONFIG/UNKNOWN），其中多数在 SPA 已有对应表达（RATE_DEAD、SOLD_OUT、
 * INDETERMINATE 本身）。此处不预铺全表，哪一档在 SPA 有了真实消费者再进。
 */
public enum FailureKind {

    /**
     * 我方凭据或配置病：session 过期、签名错、出口 IP 不在供应商白名单、网关地址失效、
     * 必填配置缺失。共同点是<b>病在我方、供应商无辜、修复前重试无效</b>。
     *
     * <p>三条纪律（cursor 失败编目铁律②，SPA 照单全收）：
     * <ul>
     *   <li><b>永不拉黑</b>该产品/酒店——货是好的，是我们的钥匙坏了</li>
     *   <li><b>永不判无货/不可订</b>、永不进任何"按失败处理"的兜底</li>
     *   <li><b>必须告警到人</b>——只有人能修配置，静默重试只是给事故续时</li>
     * </ul>
     *
     * <p>为什么必须有这一档：cursor 的飞猪 TOP session 过期后全线 error_response，
     * 因无处表达"我方配置病"而被当成"集成死了/没货"处理了<b>整整两个月</b>
     * （2026-06 ~ 08-10 复活）；08-10 复盘发现真凶还叠加了网关下线（gw.api.taobao.com
     * 停服）——两种配置病表现完全相同，而当时的模型里两者都无处安放。
     */
    AUTH_CONFIG
}
