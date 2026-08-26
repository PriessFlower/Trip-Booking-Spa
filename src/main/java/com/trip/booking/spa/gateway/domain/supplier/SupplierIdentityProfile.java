package com.trip.booking.spa.gateway.domain.supplier;

import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;

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
 *   <li>凭据到期监控：{@link #credentialRenewal()} 为 {@link CredentialRenewal#HUMAN_ONLY}
 *       的家必须提供 {@link CredentialExpiry} bean，启动即校验（CredentialExpirySampler）</li>
 * </ul>
 *
 * <p>证据写在各常量的 javadoc 里，改申报必须同步改证据。hotel_id 不稳的供应商直接
 * 拒接（R-4.4），故本枚举不设 hotel_id 字段——能出现在这里的，酒店 ID 都已核验稳定。
 *
 * <p><b>只登记在产供应商</b>（R-4.6）。2026-08-21 删去七家未接入的家；其预研结论是
 * docs/product-identity.md §4 的表，那里才是正本。未接入的家不在此预留条目——迁入时
 * 本就要按 R-4.1 重新申报，提前铺设只会与表漂移。
 */
public enum SupplierIdentityProfile {

    /**
     * 报价码 rate_id：<b>稳定</b>。2026-08-14 沙箱实测：同参数两次、跨日期
     * （10-04/4晚 vs 11-10/1晚）、跨天（08-11 记录 vs 08-14 复测）全部不变；
     * 易腐的是验价 href 的 {@code ?token=} query（每次不同）——Expedia 上游
     * 已自行分离身份与令牌。room_id 同场实测稳定，且内容 API 以其为键发布静态
     * 目录（本仓已入库 35.6 万间）。
     *
     * <p>凭据：<b>每请求现签</b>（{@code ExpediaUtils} SHA-512(apiKey+secret+ts)），
     * 无会话无到期。
     */
    EXPEDIA(SupplierSourceEnum.EXPEDIA, RoomIdStability.STABLE, QuoteCodeStability.STABLE, null,
            CredentialRenewal.STATELESS),

    /**
     * 房型 RoomTypeId：<b>稳定</b>——cursor 全程以其为房型等价判定锚（等价判定与
     * 静态映射均以它为键），无任何轮换救回代码；hotel_id 同为静态映射主键，稳定。
     *
     * <p>报价码 GoodsUniqId + littleMajiaId（马甲）：<b>易腐，官方明示马甲有效期
     * 30 分钟</b>（hotel.detail 文档「Littlemajiaid 有效期为 30 分钟」，2026-08-15
     * 核对）。实证：cursor 2026-07-19 隔时重放验价 45/47 全灭（H001144 马甲过期），
     * 其头号病灶即下单复用验价缓存里的死马甲——SPA 侧二者只进 OfferStore、TTL 短于
     * 有效期，过期一律凭 productKey 现取现验（R-3.1）。马甲是产品维促销凭证而非
     * 账号维（单账号），键的账号成分即艺龙账户名。TTL 上限取 10 分钟 = 官方有效期
     * 之三分之一（R-2.2 要求短于轮换周期），与 OfferStore 生产 TTL（600s）对齐。
     *
     * <p>凭据：<b>每请求现签</b>（{@code ElongSignUtil} 双层 MD5(ts+md5(data+appKey)+secret)），
     * 无会话无到期。
     */
    ELONG(SupplierSourceEnum.ELONG, RoomIdStability.STABLE, QuoteCodeStability.PERISHABLE,
            Duration.ofMinutes(10), CredentialRenewal.STATELESS);

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
    private final CredentialRenewal credentialRenewal;

    SupplierIdentityProfile(SupplierSourceEnum supplier, RoomIdStability roomId,
                            QuoteCodeStability quoteCode, Duration tokenTtlCap,
                            CredentialRenewal credentialRenewal) {
        this.supplier = supplier;
        this.roomId = roomId;
        this.quoteCode = quoteCode;
        this.tokenTtlCap = tokenTtlCap;
        this.credentialRenewal = credentialRenewal;
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

    /** 凭据续期档位；{@link CredentialRenewal#HUMAN_ONLY} 的家必须另供 {@link CredentialExpiry} */
    public CredentialRenewal credentialRenewal() {
        return credentialRenewal;
    }

    /** 是否许进房型级目录（R-4.3 的判定入口） */
    public boolean catalogEligibleAtRoomLevel() {
        return roomId == RoomIdStability.STABLE;
    }
}
