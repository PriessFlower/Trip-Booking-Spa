package com.trip.booking.spa.b2b.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Expedia 下游代理协议的签署留痕。
 *
 * <p>留痕本身就是验收要求（「下游代理在预订前阅读并接受 Expedia 下游代理协议」），
 * 故记的是签署这一事件的证据——谁、哪个版本、什么时候、什么来源 IP 与 UA，
 * 而不只是一个「已同意」的布尔位。Site Review 要的是能拿出来的证据。
 */
@Slf4j
@Component
public class AgreementStore {

    private static final String SCHEMA_FILE = "config/mysql/b2b-schema.sql";

    /** 来源 UA 超长时截断入库；列宽 512，超出即写不进，而签署本身不该因此失败 */
    private static final int USER_AGENT_MAX = 512;

    private final JdbcTemplate jdbcTemplate;

    public AgreementStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void verifySchema() {
        try {
            jdbcTemplate.queryForList("SELECT id, agent_id, agreement_version, accepted_at,"
                    + " client_ip, user_agent FROM b2b_agreement_accept LIMIT 0");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "b2b_agreement_accept 表不存在或结构不符，服务拒绝启动；请先按 " + SCHEMA_FILE + " 建表", e);
        }
        log.info("b2b_agreement_accept 表结构校验通过");
    }

    public boolean hasAccepted(String agentId, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM b2b_agreement_accept WHERE agent_id = ? AND agreement_version = ?",
                Integer.class, agentId, version);
        return count != null && count > 0;
    }

    /** 重复签署同一版本视为已签，不报错也不新增行（唯一键拦下） */
    public void accept(String agentId, String version, String clientIp, String userAgent) {
        jdbcTemplate.update("INSERT IGNORE INTO b2b_agreement_accept"
                        + " (agent_id, agreement_version, client_ip, user_agent) VALUES (?,?,?,?)",
                agentId, version, clientIp, truncate(userAgent));
    }

    private String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX);
    }
}
