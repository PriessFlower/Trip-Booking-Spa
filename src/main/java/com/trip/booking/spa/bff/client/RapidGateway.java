package com.trip.booking.spa.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

/**
 * bff 包私有的 Rapid HTTP 通道。
 *
 * <p>签名与凭证复用 core 的 {@link ExpediaUtils} / {@link ExpediaRapidProperties}（只读），
 * HTTP 收发独立实现：core 的 QueryProductAccess 存在 occupancy 参数丢失问题且其 DTO 会
 * 丢弃验收必需字段（refundable、nonrefundable_date_ranges、费用拆分等），故本层直接
 * 透传 Rapid 原始 JSON。
 *
 * <p>每次调用的请求与响应原文按接口名落盘至 evidence 目录（TR7 / Site Review 证据）。
 */
@Slf4j
@Component
public class RapidGateway {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExpediaRapidProperties rapidProperties;
    private final ExpediaUtils expediaUtils;
    private final BffProperties bffProperties;
    private final HttpClient httpClient;

    public RapidGateway(ExpediaRapidProperties rapidProperties,
                        ExpediaUtils expediaUtils,
                        BffProperties bffProperties) {
        this.rapidProperties = rapidProperties;
        this.expediaUtils = expediaUtils;
        this.bffProperties = bffProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** path 为以 / 开头的路径（可含 query，如 Rapid 返回的 link href） */
    public RapidReply get(String evidenceTag, String path) {
        return exchange(evidenceTag, "GET", path, null, null);
    }

    /** testScenario 非空时附加 Rapid 的 Test 请求头（仅测试端点生效），用于触发官方测试场景 */
    public RapidReply get(String evidenceTag, String path, String testScenario) {
        return exchange(evidenceTag, "GET", path, null, testScenario);
    }

    public RapidReply post(String evidenceTag, String path, String jsonBody) {
        return exchange(evidenceTag, "POST", path, jsonBody, null);
    }

    public RapidReply post(String evidenceTag, String path, String jsonBody, String testScenario) {
        return exchange(evidenceTag, "POST", path, jsonBody, testScenario);
    }

    public RapidReply delete(String evidenceTag, String path) {
        return exchange(evidenceTag, "DELETE", path, null, null);
    }

    public RapidReply delete(String evidenceTag, String path, String testScenario) {
        return exchange(evidenceTag, "DELETE", path, null, testScenario);
    }

    private RapidReply exchange(String evidenceTag, String method, String path, String jsonBody,
                                String testScenario) {
        String url = rapidProperties.getUrl().getHost() + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", expediaUtils.signGeneration())
                .header("Customer-Ip", rapidProperties.getOwnIp())
                .header("Customer-Session-Id", rapidProperties.getSession())
                .header("User-Agent", rapidProperties.getUserAgent())
                .header("Accept", "application/json");
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json");
        }
        if (testScenario != null && !testScenario.isBlank()) {
            builder.header("Test", testScenario);
        }
        switch (method) {
            case "POST":
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
                break;
            case "DELETE":
                builder.DELETE();
                break;
            default:
                builder.GET();
        }

        RapidReply reply;
        try {
            // Rapid 对部分接口无条件 gzip 响应体，而 JDK HttpClient 不自动解压，
            // 必须按 Content-Encoding 自行处理
            HttpResponse<byte[]> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String bodyText = decodeBody(response);
            reply = new RapidReply(response.statusCode(), parseQuietly(bodyText), bodyText, null);
        } catch (IOException e) {
            // 网络层异常必须按「不确定」处理：请求可能已到达供应商（尤其下单）
            log.warn("Rapid {} {} 传输异常: {}", method, evidenceTag, e.toString());
            reply = new RapidReply(0, null, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply = new RapidReply(0, null, null, e);
        }

        writeEvidence(evidenceTag, method, url, jsonBody, reply, testScenario);
        return reply;
    }

    private String decodeBody(HttpResponse<byte[]> response) throws IOException {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return null;
        }
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        if ("gzip".equalsIgnoreCase(encoding)
                // 兜底：响应头缺失但魔数为 gzip
                || (body.length > 2 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b)) {
            try (java.util.zip.GZIPInputStream in =
                         new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private JsonNode parseQuietly(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    /** 逐行 JSON 追加写入 evidence 目录，文件按接口名区分（shopping / price-check / booking / retrieve / cancel / payment-options） */
    private void writeEvidence(String tag, String method, String url, String requestBody, RapidReply reply, String testScenario) {
        try {
            Path dir = Paths.get(bffProperties.getEvidenceDir());
            Files.createDirectories(dir);
            ObjectNode line = MAPPER.createObjectNode();
            line.put("ts", Instant.now().toString());
            line.put("method", method);
            if (testScenario != null && !testScenario.isBlank()) {
                line.put("testScenario", testScenario);
            }
            line.put("url", url);
            if (requestBody != null) {
                JsonNode parsed = parseQuietly(requestBody);
                if (parsed != null) {
                    line.set("request", parsed);
                } else {
                    line.put("request", requestBody);
                }
            }
            line.put("status", reply.getStatus());
            if (reply.getBody() != null) {
                line.set("response", reply.getBody());
            } else if (reply.getRaw() != null) {
                line.put("response", reply.getRaw());
            }
            if (reply.getTransportError() != null) {
                line.put("transportError", reply.getTransportError().toString());
            }
            Files.writeString(dir.resolve(tag + ".jsonl"),
                    line.toString() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 证据写入失败不阻断业务，但必须留痕
            log.error("evidence 写入失败 tag={}", tag, e);
        }
    }
}
