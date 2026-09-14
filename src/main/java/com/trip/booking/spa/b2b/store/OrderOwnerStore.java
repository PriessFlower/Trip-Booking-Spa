package com.trip.booking.spa.b2b.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

/**
 * 订单归属与下单快照。订单本体在 {@code bff_order}，本表记「哪个订单属于哪个代理商」
 * 以及下单当刻的房价包含项。
 *
 * <p>为什么不另建一张 B2B 订单表：下单链路整条复用 bff 的
 * {@code BffBookingService}，它已按我方单号（{@code affiliate_reference_id}）记账并承担
 * 下单三态与反查确证。再建一张就是两份可能不一致的账，而对不上时无从判断哪份为真。
 *
 * <p>归属查不到即视为不属于任何代理商：B2B 侧不得看见、不得取消。
 * bff（B2C 站）下的单没有归属行，因此对 B2B 一律不可见。
 */
@Slf4j
@Component
public class OrderOwnerStore {

    private static final String SCHEMA_FILE = "config/mysql/b2b-schema.sql";

    public static class OwnerRow {
        public String orderId;
        public String agentId;
        /** 房价包含项的 JSON 数组原文；下单时未带则为 null */
        public String rateAmenities;
    }

    private static final RowMapper<OwnerRow> MAPPER = (rs, rowNum) -> {
        OwnerRow row = new OwnerRow();
        row.orderId = rs.getString("order_id");
        row.agentId = rs.getString("agent_id");
        row.rateAmenities = rs.getString("rate_amenities");
        return row;
    };

    private final JdbcTemplate jdbcTemplate;

    public OrderOwnerStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void verifySchema() {
        try {
            jdbcTemplate.query("SELECT order_id, agent_id, rate_amenities, created_at"
                    + " FROM b2b_order_owner LIMIT 0", MAPPER);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "b2b_order_owner 表不存在或结构不符，服务拒绝启动；请先按 " + SCHEMA_FILE + " 建表", e);
        }
        log.info("b2b_order_owner 表结构校验通过");
    }

    /**
     * 认领订单并记下房价包含项。下单动作已经发生，写失败不该把已成的订单回滚成失败，
     * 故失败只记日志——代价是该单在 B2B 侧看不见，需要人工补一行。
     */
    public void claim(String orderId, String agentId, String rateAmenitiesJson) {
        try {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO b2b_order_owner (order_id, agent_id, rate_amenities)"
                            + " VALUES (?,?,?)",
                    orderId, agentId, rateAmenitiesJson);
        } catch (Exception e) {
            log.error("订单归属写入失败 orderId={} agentId={}，该单在 B2B 侧将不可见", orderId, agentId, e);
        }
    }

    public boolean ownedBy(String orderId, String agentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM b2b_order_owner WHERE order_id = ? AND agent_id = ?",
                Integer.class, orderId, agentId);
        return count != null && count > 0;
    }

    public Optional<OwnerRow> find(String orderId) {
        List<OwnerRow> rows = jdbcTemplate.query(
                "SELECT * FROM b2b_order_owner WHERE order_id = ?", MAPPER, orderId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 按下单时间倒序取该代理商的订单 */
    public List<OwnerRow> listRecent(String agentId, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM b2b_order_owner WHERE agent_id = ?"
                        + " ORDER BY created_at DESC LIMIT " + Math.max(1, Math.min(limit, 100)),
                MAPPER, agentId);
    }
}
