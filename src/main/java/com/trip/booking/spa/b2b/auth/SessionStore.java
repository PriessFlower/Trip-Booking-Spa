package com.trip.booking.spa.b2b.auth;

import com.trip.booking.spa.b2b.config.B2bProperties;
import com.trip.booking.spa.platform.redis.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 代理商会话。令牌是不透明随机串，除「指向哪个代理商」外不携带任何信息；
 * 代理商身份只存在服务端 Redis 里，改不了也伪造不了。
 *
 * <p>沿用 {@code state/offer/OfferStore} 的三个动作与失败语义：签发写不进 Redis 即视为
 * 登录失败（宁可让用户重登，也不发一个查不到的令牌）；解析不到与已过期不作区分，
 * 都是「需要重新登录」这同一件事。
 */
@Slf4j
@Component
public class SessionStore {

    private static final String REDIS_KEY_PREFIX = "b2b:session:";
    private static final int TOKEN_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private B2bProperties props;

    /** @return 会话令牌；写 Redis 失败返回 null */
    public String issue(String agentId) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = "b2s_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long ttl = props.getSession().getTtlSeconds();
        if (!redisUtils.setex(REDIS_KEY_PREFIX + token, agentId, ttl)) {
            log.warn("会话令牌写入 Redis 失败 agentId={}", agentId);
            return null;
        }
        return token;
    }

    /** @return 令牌对应的代理商编号；令牌不存在或已过期返回 null */
    public String resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String agentId = redisUtils.get(REDIS_KEY_PREFIX + token);
        return agentId == null || agentId.isBlank() ? null : agentId;
    }

    /** 登出。令牌不存在时也算成功——登出的目标状态就是「这个令牌不再有效」 */
    public void drop(String token) {
        if (token != null && !token.isBlank()) {
            redisUtils.remove(REDIS_KEY_PREFIX + token);
        }
    }
}
