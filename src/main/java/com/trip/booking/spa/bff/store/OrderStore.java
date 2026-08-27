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
 * expdia 验收前端订单的本地存储。表由本包独占，不侵入 core 的 mapper 体系。
 * 表名以 {@code bff_} 前缀标明归属，与业务表同库共存——重命名或并入 core 的命名体系
 * 会抹掉这层归属，不应为「整齐」而改。
 *
 * <p>建表由 DBA 依 {@code config/mysql/bff-acceptance-schema.sql} 执行，本类只校验、不建表
 * （见 {@link #verifySchema()}）。
 *
 * <p>request_json / response_json 保存与 Expedia 往来的原文（TR7 证据 + 排障）；
 * traveler_name 保存旅客真实姓名——只落本地，不出境（对 Expedia 使用固定联系人）。
 *
 * <p><b>本表是上游的替身，不是网关的订单表。</b>本服务是供应商网关，订单归上游持有：
 * 下单用的 {@code affiliate_reference_id} 由上游经 {@code BookingReq.orderId} 传入
 * （见 docs/gateway-boundary.md B5），core 因此一张订单表都没有。验收前端背后没有上游，
 * 只能由本层代为记账。验收结束、真前端改走上游后，本类与本表应一并退役。
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
        public boolean bedChoice;
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
            row.bedChoice = rs.getBoolean("bed_choice");
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

    /** 建表 SQL 的交付位置，校验失败时指给运维 */
    private static final String SCHEMA_FILE = "config/mysql/bff-acceptance-schema.sql";

    private final JdbcTemplate jdbcTemplate;

    public OrderStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动即校验表结构，缺表或缺列一律拒绝启动。
     *
     * <p><b>本类不建表。</b>建表属库结构变更，应由 DBA 依 {@value #SCHEMA_FILE} 执行，
     * 不该由应用在启动时代劳——那会要求运行账号常备 CREATE 权限（MySQL 在
     * {@code IF NOT EXISTS} 生效前先校验权限，故表已存在也仍需该权限），
     * 且把「表没建」这种部署事故藏成运行期报错。
     *
     * <p>校验用 {@code LIMIT 0} 逐列取一遍：既确认表在，也确认 {@link #MAPPER}
     * 要读的每一列都在，缺列不必等到第一笔订单才暴露。
     */
    @PostConstruct
    public void verifySchema() {
        try {
            jdbcTemplate.query("SELECT order_id, itinerary_id, property_id, property_name,"
                    + " checkin, checkout, occupancy, bed_description, bed_choice, traveler_name,"
                    + " traveler_email, traveler_phone, status,"
                    + " request_json, response_json, pricing_json, policy_json, created_at"
                    + " FROM bff_order LIMIT 0", MAPPER);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "bff_order 表不存在或结构不符，服务拒绝启动；请先按 " + SCHEMA_FILE + " 建表", e);
        }
        log.info("bff_order 表结构校验通过");
    }

    public void insert(OrderRow row) {
        jdbcTemplate.update("INSERT INTO bff_order (order_id, itinerary_id, property_id, property_name,"
                        + " checkin, checkout, occupancy, bed_description, bed_choice, traveler_name,"
                        + " traveler_email, traveler_phone, status,"
                        + " request_json, response_json, pricing_json, policy_json)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                row.orderId, row.itineraryId, row.propertyId, row.propertyName,
                row.checkin, row.checkout, row.occupancy, row.bedDescription, row.bedChoice, row.travelerName,
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
