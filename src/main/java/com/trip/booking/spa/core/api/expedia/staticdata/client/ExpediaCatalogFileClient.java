package com.trip.booking.spa.core.api.expedia.staticdata.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.core.api.expedia.config.ExpediaRapidProperties;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/**
 * Property Catalog File 客户端（播种器数据源）。
 * 能力对齐旧链路 saveOrUpdateHotelInfo：
 * - downloadFlag：文件落盘可复用，重跑/调试不重复下载 60MB（旧 FileDealUtils 语义）
 * - updateDays：按行内 dates.added/updated 过滤最近 N 天有变化的酒店（旧 parseFile 语义）
 * - startLine：从第 N 行开始处理，支持断点续跑（旧 startLine 语义）
 * 解析为流式，内存占用与文件大小无关。
 */
@Slf4j
@Component
public class ExpediaCatalogFileClient {

    private static final String CATALOG_PATH = "/v3/files/properties/catalog";

    private final ExpediaRapidProperties properties;
    private final ExpediaUtils signer;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ExpediaCatalogFileClient(
            ExpediaRapidProperties properties,
            ExpediaUtils signer,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * 下载 catalog 文件到本地固定路径。
     *
     * @param download true=强制重新下载；false=本地已有则直接复用（对齐旧 downloadFlag）
     */
    public File downloadCatalog(String language, boolean download) {
        String lang = StringUtils.hasText(language) ? language : properties.getStaticData().getLanguage();
        Path target = Path.of(System.getProperty("java.io.tmpdir"), "expedia-catalog-" + lang + ".jsonl.gz");
        if (!download && Files.exists(target)) {
            log.info("Catalog 文件复用本地缓存: {} ({} bytes)", target, target.toFile().length());
            return target.toFile();
        }
        String href = fetchCatalogHref(lang);
        long start = System.currentTimeMillis();
        long total = probeContentLength(href);
        int connections = properties.getStaticData().getDownloadConnections();

        boolean parallel = total > 0 && connections > 1;
        if (parallel) {
            try {
                downloadInParallel(href, target, total, connections);
            } catch (Exception e) {
                // 并行失败一律回落单连接：清单是摄取的唯一输入，宁可慢也不能拿不到
                log.warn("Catalog 分段并行下载失败，回落单连接: {}", e.toString());
                parallel = false;
            }
        }
        if (!parallel) {
            downloadSingleStream(href, target);
        }

        long cost = System.currentTimeMillis() - start;
        long size = target.toFile().length();
        log.info("Catalog 文件下载完成: {} ({} bytes, {} ms, {}, {} KB/s)", target, size, cost,
                parallel ? connections + " 连接并行" : "单连接",
                cost > 0 ? size * 1000 / cost / 1024 : 0);
        return target.toFile();
    }

    /**
     * 探测文件总长，并同时确认对端支持 Range。
     *
     * <p>不用 HEAD——实测该 S3 桶对 HEAD 返回 XML 错误而不给长度。改用一个 1 字节的 Range
     * 请求：既从 {@code Content-Range} 拿到总长，又验证了 Range 支持，一次调用办两件事。
     *
     * @return 文件总字节数；无法探测或对端不支持 Range 时返回 -1，调用方据此走单连接
     */
    private long probeContentLength(String href) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(href).openConnection();
            conn.setRequestProperty(HttpHeaders.RANGE, "bytes=0-0");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            try {
                if (conn.getResponseCode() != 206) {
                    log.info("Catalog 源不支持 Range（响应 {}），改用单连接下载", conn.getResponseCode());
                    return -1;
                }
                String range = conn.getHeaderField(HttpHeaders.CONTENT_RANGE);
                int slash = range == null ? -1 : range.lastIndexOf('/');
                return slash < 0 ? -1 : Long.parseLong(range.substring(slash + 1).trim());
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.info("Catalog 长度探测失败，改用单连接下载: {}", e.toString());
            return -1;
        }
    }

    /**
     * 分段并行下载。
     *
     * <p>各段用 {@link FileChannel} 按偏移直写同一个目标文件，故不需要临时分段文件、
     * 不需要合并：磁盘占用就是文件本身，内存只有每段的传输缓冲。
     */
    private void downloadInParallel(String href, Path target, long total, int connections) throws Exception {
        long segment = (total + connections - 1) / connections;
        ExecutorService pool = Executors.newFixedThreadPool(connections);
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < connections; i++) {
                long from = i * segment;
                if (from >= total) {
                    break;
                }
                long to = Math.min(from + segment - 1, total - 1);
                futures.add(pool.submit(() -> {
                    fetchRange(href, channel, from, to);
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get();   // 任一段失败即整体失败，由调用方回落单连接
            }
        } finally {
            pool.shutdownNow();
        }
        long written = target.toFile().length();
        if (written != total) {
            throw new IllegalStateException("分段下载字节数不符: 期望 " + total + " 实得 " + written);
        }
    }

    /** 取 [from, to] 区段，写入 channel 的对应偏移 */
    private void fetchRange(String href, FileChannel channel, long from, long to) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(href).openConnection();
        conn.setRequestProperty(HttpHeaders.RANGE, "bytes=" + from + "-" + to);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(120_000);
        try (InputStream in = conn.getInputStream();
             ReadableByteChannel src = Channels.newChannel(in)) {
            long position = from;
            long remaining = to - from + 1;
            while (remaining > 0) {
                long n = channel.transferFrom(src, position, remaining);
                if (n <= 0) {
                    break;
                }
                position += n;
                remaining -= n;
            }
            if (remaining > 0) {
                throw new IllegalStateException("区段 " + from + "-" + to + " 少收 " + remaining + " 字节");
            }
        } finally {
            conn.disconnect();
        }
    }

    private void downloadSingleStream(String href, Path target) {
        try (InputStream in = new URL(href).openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException("Catalog 文件下载失败", e);
        }
    }

    /**
     * 闸口 supplier.expedia.static-data-enabled：是否允许调用 Expedia 目录清单接口。
     * 误开风险=误在开发或测试环境下载全量目录文件（约 100MB）并消耗接口配额；
     * 误关风险=无法获取目录清单，播种与每日增量同步取不到酒店名单；
     * 执行面：全部节点的静态摄取路径（BackDoor 播种与 ExpediaHotelSyncTask 定时同步）。
     */
    public String fetchCatalogHref(String language) {
        if (!properties.isStaticDataEnabled()) {
            log.warn("[gate] supplier.expedia.static-data-enabled=false，拒绝获取目录清单: language={}", language);
            throw new IllegalStateException("Expedia static data ingestion is disabled");
        }
        properties.requireCredentials();
        URI uri = UriComponentsBuilder
                .fromHttpUrl(properties.getUrl().getHost())
                .path(CATALOG_PATH)
                .queryParam("language", StringUtils.hasText(language)
                        ? language : properties.getStaticData().getLanguage())
                .queryParam("supply_source", properties.getStaticData().getSupplySource())
                .build().encode().toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, signer.signGeneration());
        headers.set("Customer-Ip", properties.getOwnIp());
        headers.set("Customer-Session-Id", properties.getSession());
        headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());

        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String href = root.path("href").asText(null);
            if (!StringUtils.hasText(href)) {
                throw new IllegalStateException("Catalog file response has no href: " + response.getBody());
            }
            return href;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse catalog file response", e);
        }
    }

    /**
     * 流式提取符合条件的 property_id。
     *
     * @param file         downloadCatalog 落盘的文件
     * @param countryCodes 国家码过滤（空 = 不过滤）
     * @param limit        最多提取多少个（&lt;=0 = 不限）
     * @param updateDays   只要最近 N 天内 added 或 updated 的酒店（null = 不按日期过滤，对齐旧 allPushFlag=true）
     * @param startLine    从第 N 行开始（&lt;=1 = 从头，对齐旧 startLine 断点续跑）
     */
    public SeedResult streamPropertyIds(File file, Set<String> countryCodes, int limit,
                                        Integer updateDays, int startLine) {
        List<String> ids = new ArrayList<>();
        long lineNo = 0;
        long matchedUntilLine = 0;
        Instant cutoff = updateDays == null ? null : Instant.now().minus(Duration.ofDays(updateDays));
        Predicate<String> countryMatch = code -> countryCodes == null || countryCodes.isEmpty()
                || (code != null && countryCodes.contains(code.toUpperCase()));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file.toPath())), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (startLine > 1 && lineNo < startLine) {
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                String propertyId = node.path("property_id").asText(null);
                if (!StringUtils.hasText(propertyId)) {
                    continue;
                }
                if (!countryMatch.test(node.path("address").path("country_code").asText(null))) {
                    continue;
                }
                if (cutoff != null && !changedSince(node, cutoff)) {
                    continue;
                }
                ids.add(propertyId);
                matchedUntilLine = lineNo;
                if (limit > 0 && ids.size() >= limit) {
                    break;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to stream Expedia catalog file", e);
        }
        log.info("Catalog seed 扫描至第 {} 行，命中 {} 家（startLine={}, updateDays={}）",
                lineNo, ids.size(), startLine, updateDays);
        return new SeedResult(lineNo, matchedUntilLine, ids);
    }

    /**
     * 对齐旧 parseFile 的日期过滤：added 或 updated 任一在窗口内即命中
     */
    private boolean changedSince(JsonNode node, Instant cutoff) {
        JsonNode dates = node.path("dates");
        return isAfter(dates.path("added").asText(null), cutoff)
                || isAfter(dates.path("updated").asText(null), cutoff);
    }

    private boolean isAfter(String isoTime, Instant cutoff) {
        if (!StringUtils.hasText(isoTime)) {
            return false;
        }
        try {
            return Instant.parse(isoTime).isAfter(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @param scannedLines    本次扫描到的最后行号（中断续跑时的参照）
     * @param lastMatchedLine 最后一个命中酒店所在行号（limit 截断时从这里+1 续跑）
     */
    public record SeedResult(long scannedLines, long lastMatchedLine, List<String> propertyIds) {
    }
}
