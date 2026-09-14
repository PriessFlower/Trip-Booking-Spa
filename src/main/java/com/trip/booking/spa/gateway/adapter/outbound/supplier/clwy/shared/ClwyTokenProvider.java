package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyTokenRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyTokenResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 差旅无忧的 JWT 令牌持有者（腐性申报 {@code CredentialRenewal.SELF_RENEWING}）。
 *
 * <p>官方 01-authentication：token 有效期 1 小时，「获取到token之后应缓存起来，推荐缓存50分钟，
 * 在token缓存过期后重新请求接口获取新的token，<b>请勿频繁重复请求获取token接口</b>」。
 *
 * <p><b>为什么是进程内缓存而不是 Redis</b>：官方没有说令牌是账号独占的（换新不使旧失效），
 * 多实例各持一份不违反任何约定；而放 Redis 就要处理跨实例的刷新竞争与序列化，为一个
 * 每 50 分钟一次的调用引入这些复杂度不划算。若日后实测发现换新会使旧令牌失效，
 * 这个选择就必须推翻——届时看 {@code supplier_auth_config} 指标是否出现成对的 401。
 *
 * <p><b>失败不缓存</b>：取不到就返回 null，由调用方按"未取得结果"回报。缓存一个 null 会把
 * 一次网络抖动放大成 50 分钟的全家不可用。
 */
@Slf4j
@Component
public class ClwyTokenProvider {

    /** 官方建议值：有效期 1 小时，缓存 50 分钟，留 10 分钟余量给时钟偏差与在途请求 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(50);

    @Resource
    private ClwyProperties properties;

    private final ReentrantLock lock = new ReentrantLock();

    /** 同一对象整体替换，避免读到"新 token 配旧到期时间"的撕裂状态 */
    private volatile Cached cached;

    /**
     * 取可用令牌；取不到返回 null（调用方据此回报"未取得结果"，不得当成无货）。
     *
     * <p><b>用途必须由调用方传入</b>，不能写死：续期发生在<b>业务调用内部</b>，后台刷价触发的
     * 续期若按前台口径快速失败，那一行刷价就白白失败一次；而前台调用触发的续期若按后台口径
     * 阻塞排队，客人就被挂在限流上。等还是走，跟着它所服务的那次调用走。
     *
     * <p>双检：快路径无锁读缓存；缓存失效才进锁，锁内再查一次——并发的其它线程等到锁时
     * 缓存通常已被第一个线程填好，于是只打一次远端。
     */
    public String token(CallPurpose purpose) {
        Cached snapshot = cached;
        if (snapshot != null && snapshot.usableAt(System.currentTimeMillis())) {
            return snapshot.token;
        }
        lock.lock();
        try {
            snapshot = cached;
            if (snapshot != null && snapshot.usableAt(System.currentTimeMillis())) {
                return snapshot.token;
            }
            String fresh = fetch(purpose);
            if (fresh == null) {
                return null;
            }
            cached = new Cached(fresh, System.currentTimeMillis() + CACHE_TTL.toMillis());
            return fresh;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 作废当前令牌。业务接口收到 401 时调用——那说明服务端认为令牌已不可用，而我们的
     * 到期时间只是本地推算，可能因时钟偏差或服务端提前吊销而落后于事实。
     */
    public void invalidate() {
        cached = null;
        log.info("差旅无忧令牌已作废，下次调用将重新获取");
    }

    private String fetch(CallPurpose purpose) {
        if (!properties.isConfigured()) {
            log.error("[auth-config] 差旅无忧凭证未配置，无法获取令牌——我方配置病，重试无效");
            Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.CLWY));
            return null;
        }
        ResponseResult<ClwyTokenResponse> result = new ClwyTokenAccess(properties)
                .access(new ClwyTokenRequest(properties.getWid(), properties.getApiKey()), purpose);
        ClwyTokenResponse resp = result == null ? null : result.getData();
        if (resp == null) {
            // 空体 401 会走到这儿（见 ClwyTokenAccess 类注释）：既可能是缺 X-Wid，也可能是网络
            log.error("差旅无忧取令牌未取得可解析响应,httpStatus={}", result == null ? null : result.getHttpStatus());
            return null;
        }
        if (!resp.isSucc()) {
            // 凭据错是 HTTP 200 + code 500，属我方配置病：供应商无辜、重试无效、必须告警到人
            log.error("[auth-config] 差旅无忧取令牌被拒，我方凭据/配置病,code={},message={}",
                    resp.getCode(), resp.getMessage());
            Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.CLWY));
            return null;
        }
        log.info("差旅无忧令牌已获取,长度={},缓存={}分钟", StringUtils.length(resp.getToken()), CACHE_TTL.toMinutes());
        return resp.getToken();
    }

    /** 仅供测试构造场景使用 */
    public void setProperties(ClwyProperties properties) {
        this.properties = properties;
    }

    private static final class Cached {
        final String token;
        final long expiresAtMillis;

        Cached(String token, long expiresAtMillis) {
            this.token = token;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean usableAt(long now) {
            return now < expiresAtMillis;
        }
    }
}
