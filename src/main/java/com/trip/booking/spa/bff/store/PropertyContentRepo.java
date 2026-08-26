package com.trip.booking.spa.bff.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 只读访问 core 静态摄取落库的 expedia_property_content（Rapid Content API 原文）。
 * raw_json 含验收所需的 checkin/checkout 说明、fees、policies、images 等完整字段。
 */
@Slf4j
@Component
public class PropertyContentRepo {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class PropertySummary {
        public String propertyId;
        public String name;
        public String city;
        public String countryCode;
        public Double starRating;
        public JsonNode raw;
    }

    private final JdbcTemplate jdbcTemplate;

    public PropertyContentRepo(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 城市清单：给前端搜索框用，只列有足够酒店的城市 */
    public List<Map<String, Object>> listCities(String language, int minCount) {
        return jdbcTemplate.queryForList(
                "SELECT city, country_code AS countryCode, COUNT(*) AS propertyCount"
                        + " FROM expedia_property_content"
                        + " WHERE language = ? AND active = 1 AND city IS NOT NULL"
                        + " GROUP BY city, country_code HAVING COUNT(*) >= ?"
                        + " ORDER BY propertyCount DESC LIMIT 20",
                language, minCount);
    }

    /** 结果在 Java 侧按星级降序排——外层 SQL 排序同样会把 raw_json 拖进 sort buffer */
    public List<PropertySummary> searchByCity(String city, String language, int limit) {
        // raw_json 为大 JSON 列，直接参与 ORDER BY 会打爆 MySQL sort buffer
        // （Out of sort memory）；先仅按小列排序取 ID，再回表取整行
        return jdbcTemplate.query(
                "SELECT p.property_id, p.name, p.city, p.country_code, p.star_rating, p.raw_json"
                        + " FROM expedia_property_content p JOIN ("
                        + "   SELECT property_id FROM expedia_property_content"
                        + "   WHERE language = ? AND active = 1 AND city = ?"
                        + "   ORDER BY star_rating DESC, property_id"
                        + "   LIMIT " + Math.max(1, Math.min(limit, 250))
                        + " ) ids ON p.property_id = ids.property_id"
                        + " WHERE p.language = ? AND p.active = 1",
                (rs, i) -> {
                    PropertySummary summary = new PropertySummary();
                    summary.propertyId = rs.getString("property_id");
                    summary.name = rs.getString("name");
                    summary.city = rs.getString("city");
                    summary.countryCode = rs.getString("country_code");
                    summary.starRating = rs.getObject("star_rating") == null ? null : rs.getDouble("star_rating");
                    summary.raw = parseQuietly(rs.getString("raw_json"));
                    return summary;
                },
                language, city, language)
                .stream()
                .sorted((a, b) -> {
                    double sa = a.starRating == null ? 0 : a.starRating;
                    double sb = b.starRating == null ? 0 : b.starRating;
                    int byStar = Double.compare(sb, sa);
                    return byStar != 0 ? byStar : a.propertyId.compareTo(b.propertyId);
                })
                .toList();
    }

    public Optional<PropertySummary> findById(String propertyId, String language) {
        List<PropertySummary> rows = jdbcTemplate.query(
                "SELECT property_id, name, city, country_code, star_rating, raw_json"
                        + " FROM expedia_property_content WHERE property_id = ? AND language = ? AND active = 1",
                (rs, i) -> {
                    PropertySummary summary = new PropertySummary();
                    summary.propertyId = rs.getString("property_id");
                    summary.name = rs.getString("name");
                    summary.city = rs.getString("city");
                    summary.countryCode = rs.getString("country_code");
                    summary.starRating = rs.getObject("star_rating") == null ? null : rs.getDouble("star_rating");
                    summary.raw = parseQuietly(rs.getString("raw_json"));
                    return summary;
                },
                propertyId, language);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 批量取指定语言的酒店名（BP2：中文名旁必须并列英文原名）。静态摄取按
     * (property_id, language) 逐语言建行，英文名就在同一张表的 en-US 行里，
     * 无须额外调 Content API。
     *
     * @return property_id → name；该语言未摄取的酒店不会出现在结果中
     */
    public Map<String, String> findNames(List<String> propertyIds, String language) {
        Map<String, String> names = new HashMap<>();
        if (propertyIds == null || propertyIds.isEmpty()) {
            return names;
        }
        String placeholders = String.join(",", Collections.nCopies(propertyIds.size(), "?"));
        Object[] args = new Object[propertyIds.size() + 1];
        args[0] = language;
        for (int i = 0; i < propertyIds.size(); i++) {
            args[i + 1] = propertyIds.get(i);
        }
        // 只取小列，不碰 raw_json——那是大 JSON 列，批量拉会拖垮查询
        jdbcTemplate.query(
                "SELECT property_id, name FROM expedia_property_content"
                        + " WHERE language = ? AND active = 1 AND property_id IN (" + placeholders + ")",
                rs -> {
                    names.put(rs.getString("property_id"), rs.getString("name"));
                },
                args);
        return names;
    }

    private JsonNode parseQuietly(String json) {
        try {
            return json == null ? null : MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("raw_json 解析失败", e);
            return null;
        }
    }
}
