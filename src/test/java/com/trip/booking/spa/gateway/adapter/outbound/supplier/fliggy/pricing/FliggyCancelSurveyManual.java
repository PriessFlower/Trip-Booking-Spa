package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing.client.AriAvailabilityAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 飞猪退改/餐食形态<b>取证工具</b>（手动跑，CI 不含）——为补 NON_REFUNDABLE 判据收集
 * 真实证据（快照 §9 必测清单：官方码表空白，码义未核实一律不确定）。
 *
 * <pre>
 * set -a; . .env; set +a
 * FLIGGY_SURVEY=1 mvn test -Dtest=FliggyCancelSurveyManual \
 *   -Dspa.survey.shids=90241168,89777062,...
 * </pre>
 *
 * <p>输出：餐食 type 分布、退改三形态（有免费窗/全程收费/无规则）分布、
 * cancel_policy.code 与形态的交叉表、"全程收费"与"无规则"的原始报文样本。
 */
@EnabledIfEnvironmentVariable(named = "FLIGGY_SURVEY", matches = "1")
class FliggyCancelSurveyManual {

    @Test
    void surveyCancelAndMealShapes() throws Exception {
        FliggyProperties props = new FliggyProperties();
        props.setAppKey(System.getenv("FLIGGY_APP_KEY"));
        ReflectionTestUtils.setField(props, "secret", System.getenv("FLIGGY_SECRET"));
        ReflectionTestUtils.setField(props, "session", System.getenv("FLIGGY_SESSION"));
        ReflectionTestUtils.setField(props, "distributor", System.getenv("FLIGGY_DISTRIBUTOR"));
        String host = System.getenv("FLIGGY_API_HOST");
        ReflectionTestUtils.setField(props, "urlHost",
                host == null || host.isBlank() ? "https://eco.taobao.com/router/rest" : host);
        assertTrue(props.isConfigured(), "缺 FLIGGY_* 凭据（先 source .env）");

        RateLimitHolder holder = new RateLimitHolder();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(new RateLimitManager() {
            @Override public void acquire(String key) { }
            @Override public boolean tryAcquire(String key) { return true; }
            @Override public boolean isRegistered(String key) { return true; }
        });
        holder.setApplicationContext(ctx);

        String[] shids = System.getProperty("spa.survey.shids", "50363404").split(",");
        LocalDate checkIn = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .plusDays(Integer.parseInt(System.getProperty("spa.survey.delta", "13")));

        int hotelOk = 0, hotelEmpty = 0, hotelDelisted = 0, hotelError = 0;
        int rates = 0;
        Map<String, Integer> mealDist = new TreeMap<>();
        Map<String, Integer> cancelDist = new TreeMap<>();
        // 卖法名语种：含假名=日文原文,纯 ASCII=英文,其余含 CJK 视为中文（粗分类,取证够用）
        Map<String, Integer> nameLang = new TreeMap<>();
        Map<String, String> nameSamples = new LinkedHashMap<>();
        // code 与形态的交叉：形态 -> (code -> 计数)
        Map<String, Map<String, Integer>> codeByShape = new TreeMap<>();
        Map<String, String> samples = new LinkedHashMap<>();

        for (String shid : shids) {
            Thread.sleep(250); // 与 cursor keeper 共池(合计贴 20 QPS 顶),取证别去挤生产的份额
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("check_in", checkIn.toString());
            query.put("check_out", checkIn.plusDays(1).toString());
            query.put("adults", 2);
            query.put("children", 0);
            query.put("hotel_id", shid.trim());
            query.put("language", "zh_CN");
            query.put("distributor", props.getDistributor());
            ResponseResult<FliggyAriResponse> result = new AriAvailabilityAccess(props).access(
                    new FliggyTopCall("taobao.xhotel.distribution.ari.availability",
                            Map.of("availability_query", JsonUtils.writeObject2Json(query))),
                    CallPurpose.REFRESH);
            FliggyAriResponse resp = result == null ? null : result.getData();
            if (resp == null || resp.isPlatformError()) {
                hotelError++;
                System.out.println("[err] shid=" + shid + " -> "
                        + (resp == null ? "无结果(网络/超时)" : resp.platformError()));
                continue;
            }
            if (resp.isHotelDelisted()) {
                hotelDelisted++;
                continue;
            }
            if (resp.isEmptyResult()) {
                hotelEmpty++;
                continue;
            }
            hotelOk++;
            for (JsonNode rate : resp.rates()) {
                rates++;
                // 卖法名语种
                JsonNode planNameNode = rate.get("rate_plan_name");
                String planName = planNameNode == null || planNameNode.isNull() ? null : planNameNode.asText();
                String lang;
                if (planName == null || planName.isBlank()) {
                    lang = "缺席";
                } else if (planName.codePoints().anyMatch(c -> (c >= 0x3040 && c <= 0x30FF))) {
                    lang = "日文(含假名)";
                } else if (planName.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF)) {
                    lang = "中文(CJK无假名)";
                } else {
                    lang = "英文/其他";
                }
                nameLang.merge(lang, 1, Integer::sum);
                if (planName != null && !planName.isBlank()) {
                    nameSamples.putIfAbsent(lang, planName);
                }
                // 餐食形态
                JsonNode meals = rate.get("meals");
                String mealKey = meals == null || meals.isNull() ? "null"
                        : "type=" + meals.path("type").asText("?");
                mealDist.merge(mealKey, 1, Integer::sum);
                // 退改形态
                JsonNode cp = rate.get("cancel_policy");
                JsonNode rules = cp == null ? null : cp.get("rules");
                String code = cp == null ? "-" : cp.path("code").asText("-");
                String shape;
                if (rules == null || !rules.isArray() || rules.isEmpty()) {
                    shape = "NO_RULES";
                } else {
                    boolean hasZero = false;
                    boolean allParsable = true;
                    for (JsonNode rule : rules) {
                        JsonNode amount = rule.get("inclusive_amount");
                        Integer cents = null;
                        try {
                            cents = amount == null || amount.isNull() ? null
                                    : Integer.parseInt(amount.asText());
                        } catch (NumberFormatException ignored) {
                        }
                        if (cents == null) {
                            allParsable = false;
                        } else if (cents == 0) {
                            hasZero = true;
                        }
                    }
                    shape = !allParsable ? "UNPARSABLE" : hasZero ? "FREE_WINDOW" : "ALL_CHARGED";
                }
                cancelDist.merge(shape, 1, Integer::sum);
                if ("ALL_CHARGED".equals(shape)) {
                    // 全程收费的关键细分：罚金=全款(真不可退) vs 罚部分(可退但收费)
                    int total = parseIntSafe(rate.path("total_rate").path("inclusive").asText(null));
                    int maxPenalty = 0;
                    for (JsonNode rule : rules) {
                        maxPenalty = Math.max(maxPenalty, parseIntSafe(rule.path("inclusive_amount").asText(null)));
                    }
                    cancelDist.merge(total > 0 && maxPenalty >= total
                            ? "ALL_CHARGED.罚全款" : "ALL_CHARGED.罚部分", 1, Integer::sum);
                }
                codeByShape.computeIfAbsent(shape, k -> new TreeMap<>()).merge(code, 1, Integer::sum);
                if (!"FREE_WINDOW".equals(shape)) {
                    samples.putIfAbsent(shape + ":" + code,
                            "shid=" + shid + " total=" + rate.path("total_rate").path("inclusive").asText("?")
                                    + " cancel_policy=" + cp);
                }
            }
        }

        System.out.printf("[survey] 店: ok=%d empty=%d delisted=%d error=%d | 报价 %d 条%n",
                hotelOk, hotelEmpty, hotelDelisted, hotelError, rates);
        System.out.println("[survey] 餐食分布: " + mealDist);
        System.out.println("[survey] 退改形态分布: " + cancelDist);
        System.out.println("[survey] code×形态: " + codeByShape);
        System.out.println("[survey] 卖法名语种: " + nameLang + " 样例: " + nameSamples);
        samples.forEach((k, v) -> System.out.println("[sample] " + k + " -> " + v));
        assertTrue(hotelOk + hotelEmpty + hotelDelisted > 0, "一家都没问出来——先查凭据/网络");
    }

    private static int parseIntSafe(String v) {
        try {
            return v == null ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
