package com.trip.booking.spa.platform.http;

import com.trip.booking.spa.platform.concurrent.ThreadPools;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import java.util.Map;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.Permits;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通道层的大文件下载能力，与 {@link BaseHttpAccess} 并列。
 *
 * <p>两者同属 ④ 通道层，区别只在传输形态：{@code BaseHttpAccess} 建模「一次请求换一次响应」，
 * 本类建模「把一个远端文件搬到本地」。二者共用同一套限流闸门与监控埋点。
 *
 * <p><b>为什么要分块并行。</b>供应商的大文件常放在无 CDN 的对象存储上，跨境 RTT 可达 170ms。
 * 单条 TCP 流在这种长肥管道上吞吐被拥塞窗口卡死——Expedia 目录清单实测 21 KB/s，而同一台机器
 * 入网能力有 2 MB/s，闲置七十倍。分块并行绕开该限制，实测提速 6.8 倍。
 *
 * <p><b>为什么按块确认而不是按文件长度。</b>各块按偏移直写同一文件时，文件长度等于最高写入
 * 偏移——只要最后一块写到位，中间块整块丢失也测不出来，产出一个「长度正确而内容残缺」的文件。
 * 故完整性判据是各块实收字节之和，而非文件长度。此坑已在生产上真实发生过一次。
 *
 * <p><b>降级。</b>对端不支持 Range、长度探测失败、或任一块重试耗尽，一律回落单连接整取。
 * 大文件通常是后续流程的唯一输入，宁可慢也不能拿不到。
 */
@Slf4j
public class ChunkedFileAccess {

    private static final String LIMIT_PREFIX = "GLOBAL_LIMIT";

    /** 块大小取 1MB：小块使快者多劳，避免等分时慢连接拖出长尾 */
    private static final long CHUNK_SIZE = 1L << 20;
    private static final int CHUNK_RETRIES = 3;

    private final SupplierSourceEnum supplier;
    private final MonitorNameEnum monitorKey;
    private final int connections;
    private final long deadlineMillis;

    public ChunkedFileAccess(SupplierSourceEnum supplier, MonitorNameEnum monitorKey,
                             int connections, long deadlineMillis) {
        this.supplier = supplier;
        this.monitorKey = monitorKey;
        this.connections = connections;
        this.deadlineMillis = deadlineMillis;
    }

    /**
     * 下载 {@code url} 至 {@code target}。
     *
     * @return 实际写入的字节数
     * @throws IllegalStateException 单连接兜底亦失败时
     */
    public long download(String url, Path target) {
        long start = System.currentTimeMillis();
        long total = probeContentLength(url);
        boolean parallel = total > 0 && connections > 1;

        if (parallel) {
            try {
                downloadInChunks(url, target, total);
            } catch (Exception e) {
                log.warn("[{}] 分块下载失败，回落单连接: {}", supplier.name(), e.toString());
                Monitor.recordOne(MetricNames.SUPPLIER_FILE_ACCESS, fileTags(MetricNames.FILE_FALLBACK));
                parallel = false;
            }
        }
        if (!parallel) {
            downloadSingleStream(url, target);
        }

        long cost = System.currentTimeMillis() - start;
        long size = target.toFile().length();
        Monitor.recordOne(MetricNames.SUPPLIER_FILE_ACCESS,
                fileTags(parallel ? MetricNames.FILE_CHUNKED : MetricNames.FILE_SINGLE), cost);
        Monitor.recordValue(MetricNames.SUPPLIER_FILE_BYTES, MetricTags.of(supplier, monitorKey),
                (int) Math.min(size / 1024, Integer.MAX_VALUE));
        log.info("[{}] 文件下载完成: {} ({} bytes, {} ms, {}, {} KB/s)", supplier.name(), target, size, cost,
                parallel ? connections + " 连接分块" : "单连接",
                cost > 0 ? size * 1000 / cost / 1024 : 0);
        return size;
    }

    /**
     * 探测总长并确认对端支持 Range。
     *
     * <p>用 1 字节 Range 请求而非 HEAD：实测部分对象存储对 HEAD 返回 XML 错误而不给长度，
     * 而 Range 探测一次调用既取长度又验证 Range 支持。
     *
     * @return 总字节数；无法探测或不支持 Range 时返回 -1
     */
    private long probeContentLength(String url) {
        acquirePermit();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Range", "bytes=0-0");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            try {
                if (conn.getResponseCode() != 206) {
                    log.info("[{}] 源不支持 Range（响应 {}），改用单连接", supplier.name(), conn.getResponseCode());
                    return -1;
                }
                String range = conn.getHeaderField("Content-Range");
                int slash = range == null ? -1 : range.lastIndexOf('/');
                return slash < 0 ? -1 : Long.parseLong(range.substring(slash + 1).trim());
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.info("[{}] 长度探测失败，改用单连接: {}", supplier.name(), e.toString());
            return -1;
        }
    }

    /**
     * 分块下载。
     *
     * <p>切成定长小块投入队列，各线程取一块下一块——快的连接自然多取，不会像等分那样
     * 被最慢的一段拖出长尾。各块按偏移直写同一文件，故无需临时分块文件与合并。
     */
    private void downloadInChunks(String url, Path target, long total) throws Exception {
        List<long[]> chunks = new ArrayList<>();
        for (long from = 0; from < total; from += CHUNK_SIZE) {
            chunks.add(new long[]{from, Math.min(from + CHUNK_SIZE, total) - 1});
        }
        ConcurrentLinkedQueue<long[]> queue = new ConcurrentLinkedQueue<>(chunks);
        AtomicLong confirmed = new AtomicLong();
        AtomicLong retried = new AtomicLong();
        long deadline = System.currentTimeMillis() + deadlineMillis;

        ExecutorService pool = ThreadPools.fixed(
                supplier.name().toLowerCase() + "-file-download", connections, false);
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < connections; i++) {
                futures.add(pool.submit(() -> {
                    long[] chunk;
                    while ((chunk = queue.poll()) != null) {
                        if (System.currentTimeMillis() > deadline) {
                            throw new IllegalStateException("下载超过总时限 " + deadlineMillis + "ms");
                        }
                        confirmed.addAndGet(fetchChunkWithRetry(url, channel, chunk[0], chunk[1], retried));
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        if (retried.get() > 0) {
            Monitor.recordMany(MetricNames.SUPPLIER_FILE_CHUNK_RETRY, MetricTags.of(supplier, monitorKey),
                    (int) retried.get());
        }
        // 判据是各块实收字节之和，不是文件长度——后者等于最高写入偏移，测不出中间空洞
        if (confirmed.get() != total) {
            throw new IllegalStateException("分块确认字节数不符: 期望 " + total + " 实得 " + confirmed.get());
        }
    }

    private long fetchChunkWithRetry(String url, FileChannel channel, long from, long to, AtomicLong retried) {
        long expected = to - from + 1;
        Exception last = null;
        for (int attempt = 1; attempt <= CHUNK_RETRIES; attempt++) {
            try {
                long got = fetchChunk(url, channel, from, to);
                if (got == expected) {
                    return got;
                }
                last = new IllegalStateException("应收 " + expected + " 实收 " + got);
            } catch (Exception e) {
                last = e;
            }
            retried.incrementAndGet();
            log.debug("[{}] 块 {}-{} 第 {} 次未收满，重试", supplier.name(), from, to, attempt);
        }
        throw new IllegalStateException("块 " + from + "-" + to + " 重试 " + CHUNK_RETRIES + " 次仍失败: " + last);
    }

    /** 取 [from, to] 并写入 channel 对应偏移；返回实收字节 */
    private long fetchChunk(String url, FileChannel channel, long from, long to) throws Exception {
        acquirePermit();
        long expected = to - from + 1;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("Range", "bytes=" + from + "-" + to);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[64 * 1024];
            long position = from;
            long got = 0;
            while (got < expected) {
                int read = in.read(buf, 0, (int) Math.min(buf.length, expected - got));
                if (read < 0) {
                    break;
                }
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, read);
                while (bb.hasRemaining()) {
                    position += channel.write(bb, position);
                }
                got += read;
            }
            return got;
        } finally {
            conn.disconnect();
        }
    }

    private void downloadSingleStream(String url, Path target) {
        acquirePermit();
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Monitor.recordOne(MetricNames.SUPPLIER_FILE_ACCESS, fileTags(MetricNames.FILE_ERROR));
            throw new IllegalStateException("文件下载失败: " + url, e);
        }
    }

    /**
     * 过限流闸门。与 {@link BaseHttpAccess} 同一套键构造与同一套用途语义，故运维在 Nacos 调整
     * {@code ratelimit.qps} 即可同时约束请求与下载，无需发版。
     *
     * <p>用途固定为 {@link CallPurpose#CONTENT}：下载属后台批量作业，故阻塞排队而非快速失败——
     * 等得起，而失败要整个文件重来，代价远高于等待。用途桶未登记时只扣接口桶（与 BaseHttpAccess 同规）。
     */
    private void acquirePermit() {
        Permits.take(LIMIT_PREFIX + ":" + supplier.name() + ":" + monitorKey.name(), CallPurpose.CONTENT);
    }

    /** 固定一个指标名，供应商与方式全部进标签（O-2.1）——此前拼名字，最多产生上千个独立名字 */
    private Map<String, Object> fileTags(String outcome) {
        return MetricTags.outcomeOf(supplier, monitorKey, outcome);
    }
}
