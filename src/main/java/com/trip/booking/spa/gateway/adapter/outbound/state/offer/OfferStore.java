package com.trip.booking.spa.gateway.adapter.outbound.state.offer;

import com.trip.booking.spa.platform.redis.RedisUtils;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * 报价句柄仓库：验价时签发一个不透明的 {@code offerId}，下单时凭它取回供应商凭据。
 *
 * <p><b>为什么要有这一层</b>：上游不应该搬运供应商的内部凭据。Expedia 的下单链接是一串
 * 一千余字符的内部令牌，若直接回传给上游，上游就成了供应商内部结构的搬运工——
 * 它得存下来、传回来，将来多家供应商各有各的形态时，还得在多处判别与拆解。
 * tg-trip-cursor 正是这样一步步走到「一个字段被 6 家供应商赋予 6 种语义」的：
 * 该字段既是身份又是令牌，仓内 4 处独立拆解，其中一处与另三处规则不一致。
 *
 * <p>改由网关持有后，上游只见到一个自己看得懂的短 ID：<b>原样存、原样回传、永不解析</b>。
 * 另一个顺带的好处是「验价与下单的对齐问题」在结构上消失了——凭据由同一份代码写入、
 * 同一份代码读出，不存在两个系统按各自规则拼 key 再期望拼出同一个值的余地。
 * 上游那条「验价端与下单端必须用同一份拼接规则，任何字段顺序变化都会导致对不上」的
 * 纪律，在这里不需要存在，因为无处可漂移。
 *
 * <p><b>过期语义</b>：句柄的存活时间必须短于供应商凭据本身的有效期，否则会取回一个
 * 已失效的凭据去下单。取不到时属确定性失败（供应商侧什么都没发生），上游重新验价即可，
 * 不可判为「结果不确定」。
 *
 * <p><b>本类对供应商无偏</b>：不解释 {@link Offer#getCredentials()} 里的任何键，
 * 也不假设凭据只有一项。后续把其余供应商迁入时无需改动本类。
 *
 * @see Offer 存放的内容，以及为什么凭据是键值对
 */
@Slf4j
@Component
@RefreshScope
public class OfferStore {

    /** 句柄前缀。取值刻意不含供应商信息——句柄对上游必须是完全不透明的 */
    private static final String OFFER_ID_PREFIX = "of_";

    private static final String REDIS_KEY_PREFIX = "offer:";

    /** 128 位随机量，足以排除猜测与碰撞 */
    private static final int RANDOM_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 句柄存活秒数。权威取值在 Nacos，此处仅为兜底。
     *
     * <p>兜底取 300 而非生产的 600：按 §3.3.3，兜底默认值必须取安全侧，禁止以生产
     * 实际值充当。此处两个方向的代价并不对称——过短只是让上游多验一次价，过长则会
     * 取回一个已失效的供应商凭据去下单，而彼时旅客已经付了钱。故兜底往短里取。
     *
     * <p>Nacos 缺该键时生效，是「配置没配好」的降级路径，不是常态。
     */
    @Value("${cache.offer.ttl-seconds:300}")
    private long ttlSeconds;

    @Resource
    private RedisUtils redisUtils;

    /**
     * 签发句柄。
     *
     * @param supplierId  签发方供应商
     * @param credentials 该供应商下单所需的全部凭据，按名存放；键名由该供应商实现自定
     * @return 句柄；<b>写入失败时返回 null</b>，调用方应据此把本次验价视为失败——
     *         返回一个取不回来的句柄，等于给上游一个报得出价却下不了单的报价，
     *         比直接验价失败更糟
     */
    public String issue(Integer supplierId, Map<String, String> credentials) {
        if (supplierId == null || MapUtils.isEmpty(credentials)) {
            log.error("签发报价句柄失败：供应商或凭据为空, supplierId={}", supplierId);
            return null;
        }
        String offerId = OFFER_ID_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomBytes());
        Offer offer = Offer.builder()
                .supplierId(supplierId)
                .credentials(credentials)
                .expiresAt(System.currentTimeMillis() + ttlSeconds * 1000L)
                .build();
        if (!redisUtils.setex(REDIS_KEY_PREFIX + offerId, JsonUtils.writeObject2Json(offer), ttlSeconds)) {
            log.error("签发报价句柄失败：写入缓存未成功, supplierId={}, offerId={}", supplierId, offerId);
            return null;
        }
        log.info("已签发报价句柄 supplierId={}, offerId={}, ttlSeconds={}, credentialKeys={}",
                supplierId, offerId, ttlSeconds, credentials.keySet());
        return offerId;
    }

    /**
     * 取回句柄对应的报价。
     *
     * @return 报价；句柄不存在、已过期或内容无法解析时返回 null。
     *         这三者对调用方是同一件事：<b>此刻无法凭它下单，需重新验价</b>。
     *         注意 Redis 无从区分「已过期」与「从未签发」，故不试图区分
     */
    public Offer resolve(String offerId) {
        if (StringUtils.isBlank(offerId)) {
            return null;
        }
        String payload = redisUtils.get(REDIS_KEY_PREFIX + offerId);
        if (StringUtils.isBlank(payload)) {
            log.warn("报价句柄不存在或已过期 offerId={}", offerId);
            return null;
        }
        try {
            Offer offer = JsonUtils.readValue(payload, Offer.class);
            if (offer == null || offer.getSupplierId() == null
                    || MapUtils.isEmpty(offer.getCredentials())) {
                log.error("报价句柄内容不完整 offerId={}", offerId);
                return null;
            }
            return offer;
        } catch (Exception e) {
            log.error("报价句柄内容无法解析 offerId={}", offerId, e);
            return null;
        }
    }

    /**
     * 核销句柄：下单<b>确定成功</b>后调用，票据用完即焚。
     *
     * <p>此前句柄在下单成功后仍存活到 TTL 届满，期间同一句柄可再次取出凭据重复下单——
     * 安全仅系于供应商侧幂等（Expedia 靠 {@code affiliate_reference_id} 拒重）。
     * 防线必须在自己家：核销后重复下单在网关内即得到确定性失败
     * （{@link #resolve} 返回 null →「报价已过期或不存在」），不再依赖任何一家的行为。
     *
     * <p><b>只在确定成功时核销</b>：FAILED（供应商侧什么都没发生）保留句柄，允许上游修正
     * 数据后用同一报价重试；UNKNOWN 必须保留——对账与凭单反查可能仍需它，
     * 且此刻焚票会把「结果不确定」恶化为「无从重试」。
     *
     * <p>删除失败仅告警不抛出：核销是收尾动作，此刻订单已成立，绝不能让收尾失败
     * 污染已确定的成功结论；漏核销的兜底仍是供应商幂等 + TTL 自然过期。
     */
    public void consume(String offerId) {
        if (StringUtils.isBlank(offerId)) {
            return;
        }
        try {
            redisUtils.remove(REDIS_KEY_PREFIX + offerId);
            log.info("报价句柄已核销（下单成功，用完即焚） offerId={}", offerId);
        } catch (Exception e) {
            log.warn("报价句柄核销失败，句柄将于 TTL 自然过期 offerId={}", offerId, e);
        }
    }

    /** 供调用方回报给上游，使上游得知该报价还能撑多久 */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
