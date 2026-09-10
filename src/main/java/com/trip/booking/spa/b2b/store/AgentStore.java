package com.trip.booking.spa.b2b.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

/**
 * 代理商账号存储。表由本包独占，以 {@code b2b_} 前缀标明归属。
 *
 * <p>建表由 DBA 依 {@value #SCHEMA_FILE} 执行，本类只校验、不建表（见 {@link #verifySchema()}）。
 */
@Slf4j
@Component
public class AgentStore {

    static final String SCHEMA_FILE = "config/mysql/b2b-schema.sql";

    public static class AgentRow {
        public String agentId;
        public String loginName;
        public String passwordHash;
        public String agentName;
        public String contactEmail;
        public String contactPhone;
        /** 1 启用；0 停用。停用后既登不进，也不该再放行既有会话 */
        public boolean enabled;
    }

    private static final RowMapper<AgentRow> MAPPER = (rs, rowNum) -> {
        AgentRow row = new AgentRow();
        row.agentId = rs.getString("agent_id");
        row.loginName = rs.getString("login_name");
        row.passwordHash = rs.getString("password_hash");
        row.agentName = rs.getString("agent_name");
        row.contactEmail = rs.getString("contact_email");
        row.contactPhone = rs.getString("contact_phone");
        row.enabled = rs.getInt("status") == 1;
        return row;
    };

    private final JdbcTemplate jdbcTemplate;

    public AgentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 逐列取一遍：既确认表在，也确认 {@link #MAPPER} 要读的列都在，缺列不必等到第一次登录才暴露 */
    @PostConstruct
    public void verifySchema() {
        try {
            jdbcTemplate.query("SELECT agent_id, login_name, password_hash, agent_name,"
                    + " contact_email, contact_phone, status FROM b2b_agent LIMIT 0", MAPPER);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "b2b_agent 表不存在或结构不符，服务拒绝启动；请先按 " + SCHEMA_FILE + " 建表", e);
        }
        log.info("b2b_agent 表结构校验通过");
    }

    public Optional<AgentRow> findByLoginName(String loginName) {
        List<AgentRow> rows = jdbcTemplate.query(
                "SELECT * FROM b2b_agent WHERE login_name = ?", MAPPER, loginName);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<AgentRow> findById(String agentId) {
        List<AgentRow> rows = jdbcTemplate.query(
                "SELECT * FROM b2b_agent WHERE agent_id = ?", MAPPER, agentId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 开户。目前只有运维后门一个调用方，没有自助注册 */
    public void insert(AgentRow row) {
        jdbcTemplate.update("INSERT INTO b2b_agent (agent_id, login_name, password_hash, agent_name,"
                        + " contact_email, contact_phone, status) VALUES (?,?,?,?,?,?,?)",
                row.agentId, row.loginName, row.passwordHash, row.agentName,
                row.contactEmail, row.contactPhone, row.enabled ? 1 : 0);
    }
}
