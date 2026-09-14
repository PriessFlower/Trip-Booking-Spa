package com.trip.booking.spa.b2b.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 代理商口令哈希。只用 JDK 自带的 PBKDF2，不引 Spring Security。
 *
 * <p>不引的理由是它会顺带改变全站行为：{@code spring-boot-starter-security} 一进依赖，
 * Spring Boot 默认给所有端点挂上认证，包括面向上游的 {@code /client/spa/**} 与
 * {@code /actuator/**}——为一个登录页付这个代价不值当。
 *
 * <p>哈希串自带算法与迭代次数（{@code pbkdf2-sha256:<迭代>:<盐>:<哈希>}），
 * 故日后调高迭代次数不会作废存量口令：校验按串内参数走，只有重设口令才用新参数。
 */
public final class PasswordHash {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String TAG = "pbkdf2-sha256";

    /** OWASP 对 PBKDF2-HMAC-SHA256 的现行建议值（查阅日期 2026-09-03）。登录是低频动作，耗时可接受 */
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private PasswordHash() {
    }

    /** 生成可直接写入 b2b_agent.password_hash 的串 */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        return TAG + ":" + ITERATIONS + ":" + ENCODER.encodeToString(salt) + ":" + ENCODER.encodeToString(hash);
    }

    /**
     * 校验口令。串格式不认、参数缺失、算法不支持一律返回 false——
     * 不抛异常，避免把「库里那行坏了」暴露成与「口令错了」不同的响应。
     */
    public static boolean matches(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split(":");
        if (parts.length != 4 || !TAG.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = DECODER.decode(parts[2]);
            byte[] expected = DECODER.decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            // 定长比较，避免按字节提前返回泄露信息
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 计算失败", e);
        }
    }
}
