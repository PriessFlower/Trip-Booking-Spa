package com.trip.booking.spa.bff.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * expdia 验收前端订单的本地存储。表由本包独占（bff_order），启动时自建，
 * 不侵入 core 的 mapper 体系。
 *
 * <p>request_json / response_json 保存与 Expedia 往来的原文（TR7 证据 + 排障）；
 * traveler_name 保存旅客真实姓名——只落本地，不出境（对 Expedia 使用固定联系人）。
 */
@Slf4j
@Component
public class OrderStore {

    public static class OrderRow {
        public String orderId;
        public String itineraryId;
        public String propertyId;
        public String propertyName;
        public String checkin;
        public String checkout;
        public String occupancy;
        public String bedDescription;
        public String travelerName;
        /** 旅客联系方式：仅存本地供确认页/客服使用，不发送给供应商（§3.4 过渡方案） */
        public String travelerEmail;
        public String travelerPhone;
        public String status;
        public String requestJson;
        public String responseJson;
        /** 验价价格快照（occupancy_pricing 原文） */
        public String pricingJson;
        /** 政策快照：refundable、cancel_penalties、nonrefundable_date_ranges、paymentOptions */
        public String policyJson;
        public String createdAt;
    }

    private static final RowMapper<OrderRow> MAPPER = new RowMapper<OrderRow>() {
        @Override
        public OrderRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            OrderRow row = new OrderRow();
            row.orderId = rs.getString("order_id");
            row.itineraryId = rs.getString("itinerary_id");
            row.propertyId = rs.getString("property_id");
            row.propertyName = rs.getString("property_name");
            row.checkin = rs.getString("checkin");
            row.checkout = rs.getString("checkout");
            row.occupancy = rs.getString("occupancy");
            row.bedDescription = rs.getString("bed_description");
            row.travelerName = rs.getString("traveler_name");
            row.travelerEmail = rs.getString("traveler_email");
            row.travelerPhone = rs.getString("traveler_phone");
            row.status = rs.getString("status");
            row.requestJson = rs.getString("request_json");
            row.responseJson = rs.getString("response_json");
            row.pricingJson = rs.getString("pricing_json");
            row.policyJson = rs.getString("policy_json");
            row.createdAt = rs.getString("created_at");
            return row;
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public OrderStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS bff_order ("
                + "order_id VARCHAR(32) NOT NULL PRIMARY KEY,"
                + "itinerary_id VARCHAR(64) NULL,"
                + "property_id VARCHAR(32) NOT NULL,"
                + "property_name VARCHAR(512) NULL,"
                + "checkin VARCHAR(10) NOT NULL,"
                + "checkout VARCHAR(10) NOT NULL,"
                + "occupancy VARCHAR(255) NULL,"
                + "bed_description VARCHAR(255) NULL,"
                + "traveler_name VARCHAR(255) NULL,"
                + "traveler_email VARCHAR(255) NULL,"
                + "traveler_phone VARCHAR(64) NULL,"
                + "status VARCHAR(32) NOT NULL,"
                + "request_json JSON NULL,"
                + "response_json JSON NULL,"
                + "pricing_json JSON NULL,"
                + "policy_json JSON NULL,"
                + "created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                + "updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        // 兼容既有表：CREATE IF NOT EXISTS 不会补列，逐列 ALTER，已存在则忽略
        for (String column : new String[]{"traveler_email VARCHAR(255) NULL", "traveler_phone VARCHAR(64) NULL"}) {
            try {
                jdbcTemplate.execute("ALTER TABLE bff_order ADD COLUMN " + column);
            } catch (Exception e) {
                // duplicate column，可忽略
            }
        }
        log.info("bff_order 表就绪");
    }

    public void insert(OrderRow row) {
        jdbcTemplate.update("INSERT INTO bff_order (order_id, itinerary_id, property_id, property_name,"
                        + " checkin, checkout, occupancy, bed_description, traveler_name,"
                        + " traveler_email, traveler_phone, status,"
                        + " request_json, response_json, pricing_json, policy_json)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                row.orderId, row.itineraryId, row.propertyId, row.propertyName,
                row.checkin, row.checkout, row.occupancy, row.bedDescription, row.travelerName,
                row.travelerEmail, row.travelerPhone,
                row.status, asJson(row.requestJson), asJson(row.responseJson),
                asJson(row.pricingJson), asJson(row.policyJson));
    }

    public void update(String orderId, String itineraryId, String status, String responseJson) {
        jdbcTemplate.update("UPDATE bff_order SET itinerary_id = COALESCE(?, itinerary_id),"
                        + " status = ?, response_json = COALESCE(?, response_json) WHERE order_id = ?",
                itineraryId, status, asJson(responseJson), orderId);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** JSON 列只接受合法 JSON；供应商偶发返回非 JSON 原文时包装为 {"raw": "..."} 保留证据 */
    private String asJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JSON.readTree(value);
            return value;
        } catch (Exception e) {
            try {
                return JSON.createObjectNode().put("raw", value).toString();
            } catch (Exception inner) {
                return null;
            }
        }
    }

    public Optional<OrderRow> find(String orderId) {
        List<OrderRow> rows = jdbcTemplate.query(
                "SELECT * FROM bff_order WHERE order_id = ?", MAPPER, orderId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<OrderRow> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM bff_order ORDER BY created_at DESC LIMIT " + Math.max(1, Math.min(limit, 100)),
                MAPPER);
    }
}
