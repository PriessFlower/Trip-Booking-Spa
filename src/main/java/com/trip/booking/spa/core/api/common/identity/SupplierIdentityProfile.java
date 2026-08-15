package com.trip.booking.spa.core.api.common.identity;

import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;

import java.time.Duration;

/**
 * 腐性申报三行制的代码化（docs/product-identity.md §4）。
 *
 * <p>每家供应商接入前必须申报三个标识的稳定性并附证据（R-4.1）；无证据一律按易腐
 * 处理（R-4.2）——成本不对称：错待稳定码 = 多一次现查（几百毫秒）；错待易腐码 =
 * 僵尸价 + 丢真单（cursor：dida KR 66,469 行僵尸价、艺龙 45/47 验价全灭）。
 *
 * <p>申报的消费方：
 * <ul>
 *   <li>目录层：只有 {@link QuoteCodeStability#STABLE} 的真码才许进 hint 列（R-2.3）；
 *       {@link RoomIdStability#STABLE} 之外的供应商不进房型级目录（R-4.3）</li>
 *   <li>OfferStore：易腐令牌的 TTL 以 {@link #tokenTtlCap()} 为上限（R-2.2）</li>
 * </ul>
 *
 * <p>证据写在各常量的 javadoc 里，改申报必须同步改证据。hotel_id 不稳的供应商直接
 * 拒接（R-4.4），故本枚举不设 hotel_id 字段——能出现在这里的，酒店 ID 都已核验稳定。
 */
public enum SupplierIdentityProfile {

    /**
     * 报价码 rate_id：<b>稳定</b>。2026-08-14 沙箱实测：同参数两次、跨日期
     * （10-04/4晚 vs 11-10/1晚）、跨天（08-11 记录 vs 08-14 复测）全部不变；
     * 易腐的是验价 href 的 {@code ?token=} query（每次不同）——Expedia 上游
     * 已自行分离身份与令牌。room_id 同场实测稳定，且内容 API 以其为键发布静态
     * 目录（本仓已入库 35.6 万间）。
     */
    EXPEDIA(SupplierSourceEnum.EXPEDIA, RoomIdStability.STABLE, QuoteCodeStability.STABLE, null),

    /**
     * 报价码 goodsId：<b>稳定</b>。供应商文档定义为"产品ID"（Long 业务号，非 hash）；
     * cursor 对美团零救回代码；本仓静态同步链路将其作为主数据推送。
     */
    MEITUAN(SupplierSourceEnum.MEITUAN, RoomIdStability.STABLE, QuoteCodeStability.STABLE, null),

    /**
     * 报价码 RatePlanID：<b>易腐，轮换快于 4h</b>。证据：供应商错误码 2005（报价码
     * 已轮换）；cursor 2026-06-29 实证——验价失败的 rpId 全部不在现货清单（该酒店
     * 有 164 个现行 plan）；其 KR 静态库 25.6% 映射是死 id。
     * TTL 上限取 30 分钟：确切轮换周期未知，按已观测最短间隔保守取值。
     */
    DIDATRAVEL(SupplierSourceEnum.DIDATRAVEL, RoomIdStability.STABLE, QuoteCodeStability.PERISHABLE,
            Duration.ofMinutes(30)),

    /**
     * 报价码 rpid/ratePlanCode：<b>易腐 ≤4h</b>。证据：cursor 团队与供应商确认
     * （2026-06-19）；告警样本 35/35 全 ERR:1001（报价码过期）。本仓现有代码也已
     * 用行为承认这一点：验价前强制重打一次 getPrice、rpid 从新响应现取。
     * TTL 上限 = 轮换周期之半（R-2.2）。
     */
    HUITRAVEL(SupplierSourceEnum.HUITRAVEL, RoomIdStability.STABLE, QuoteCodeStability.PERISHABLE,
            Duration.ofHours(2)),

    /**
     * <b>无房型 ID</b>：查价响应只有 room_name（自由文本）+ rg_ext（床型/浴室/容量
     * 结构化属性），走现货级降级（R-4.3），房型身份由适配层用 rg_ext 属性拼替身。
     * 报价码 book_hash：<b>易腐</b>（字段名即自白）；本仓现有代码从不复用上一轮的
     * book_hash，验价必现查。
     */
    RATEHAWK(SupplierSourceEnum.RATEHAWK, RoomIdStability.ABSENT, QuoteCodeStability.PERISHABLE,
            Duration.ofMinutes(30)),

    /**
     * 报价码 plansid：<b>易腐（会话级）</b>。本仓现有代码即自白：验价时丢弃入参里的
     * planSession，重发一次 search 现取新 plansid。room_id 稳定性无证据，按未核验
     * 处理（R-4.2）。
     */
    TRAVELCONNECT(SupplierSourceEnum.TRAVELCONNECT, RoomIdStability.UNVERIFIED, QuoteCodeStability.PERISHABLE,
            Duration.ofMinutes(30)),

    /**
     * 报价码 room_key：结构上是确定性合成（base64(room_type..*rate_plan_code..*hotel_id)，
     * 无时间戳、无 nonce），<b>但无供应商文档背书，按易腐起步</b>（R-4.2），
     * 待实测证据再升级。room_id 稳定性同样未核验。
     */
    AICHOTELS(SupplierSourceEnum.AICHOTELS, RoomIdStability.UNVERIFIED, QuoteCodeStability.PERISHABLE,
            Duration.ofMinutes(30)),

    /**
     * 未做任何腐性调查，全项按最保守申报（R-4.2）。接入真实流量前必须补齐证据。
     */
    FASTPAYHOTELS(SupplierSourceEnum.FASTPAYHOTELS, RoomIdStability.UNVERIFIED, QuoteCodeStability.PERISHABLE,
            Duration.ofMinutes(30));

    /** 房型 ID 的申报档位 */
    public enum RoomIdStability {
        /** 有证据稳定：可进房型级目录、可作 productKey 成分 */
        STABLE,
        /** 供应商就没有房型 ID：现货级降级，用结构化属性替身（R-4.3） */
        ABSENT,
        /** 无证据：按 ABSENT 同等对待，直到给出证据 */
        UNVERIFIED
    }

    /** 报价码的申报档位 */
    public enum QuoteCodeStability {
        /** 有证据长期有效：可进目录 hint 列（R-2.3） */
        STABLE,
        /** 会轮换/过期（或无证据）：只许进 OfferStore，禁止落库（R-2.1） */
        PERISHABLE
    }

    private final SupplierSourceEnum supplier;
    private final RoomIdStability roomId;
    private final QuoteCodeStability quoteCode;
    private final Duration tokenTtlCap;

    SupplierIdentityProfile(SupplierSourceEnum supplier, RoomIdStability roomId,
                            QuoteCodeStability quoteCode, Duration tokenTtlCap) {
        this.supplier = supplier;
        this.roomId = roomId;
        this.quoteCode = quoteCode;
        this.tokenTtlCap = tokenTtlCap;
    }

    /** 按供应商编码取申报；未申报即抛——R-4.1 是接入的前置门，不给默认值 */
    public static SupplierIdentityProfile forCode(int supplierCode) {
        for (SupplierIdentityProfile profile : values()) {
            if (profile.supplier.getCode() == supplierCode) {
                return profile;
            }
        }
        throw new IllegalStateException("供应商 " + supplierCode
                + " 未做腐性申报，禁止接入。先补 SupplierIdentityProfile 并附证据（docs/product-identity.md R-4.1）");
    }

    public SupplierSourceEnum supplier() {
        return supplier;
    }

    public RoomIdStability roomIdStability() {
        return roomId;
    }

    public QuoteCodeStability quoteCodeStability() {
        return quoteCode;
    }

    /** 该家令牌在 OfferStore 里的 TTL 上限；申报为稳定的家返回 null（不设上限） */
    public Duration tokenTtlCap() {
        return tokenTtlCap;
    }

    /** 是否许进房型级目录（R-4.3 的判定入口） */
    public boolean catalogEligibleAtRoomLevel() {
        return roomId == RoomIdStability.STABLE;
    }
}
