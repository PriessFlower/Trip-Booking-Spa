package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.catalog.ExpediaCatalogTransformService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.client.ExpediaCatalogFileClient;
import com.trip.booking.spa.platform.concurrent.ThreadPools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * 阶段2.5 播种器：Catalog 全量清单文件 → property_id 名单 →（可选）摄取 →（可选）加工进目录。
 * 运维能力对齐旧链路 saveOrUpdateHotelInfo(downloadFlag, allPushFlag, updateDays, ids, startLine)：
 * 文件复用 / 日期增量 / 断点续跑 / ThreadPoolUtils 并发。
 */
@Slf4j
@Service
public class ExpediaCatalogSeedService {

    /**
     * 每个并发分片的酒店数（摄取按 250/批已在下游分批，这里控制并发粒度）
     */
    private static final int PART_SIZE = 200;

    private final ExpediaCatalogFileClient catalogFileClient;
    private final ExpediaStaticDataIngestionService ingestionService;
    private final ExpediaCatalogTransformService transformService;

    public ExpediaCatalogSeedService(
            ExpediaCatalogFileClient catalogFileClient,
            ExpediaStaticDataIngestionService ingestionService,
            ExpediaCatalogTransformService transformService) {
        this.catalogFileClient = catalogFileClient;
        this.ingestionService = ingestionService;
        this.transformService = transformService;
    }

    /**
     * @param countryCodes 国家码过滤（空 = 全球）
     * @param limit        本次最多播种多少家（&lt;=0 = 不限）
     * @param ingest       是否顺带摄取（拉双语原文进快照表）
     * @param transform    是否顺带加工进打底目录（需 ingest=true）
     * @param download     true=强制重新下载 catalog 文件；false=复用本地缓存（对齐旧 downloadFlag）
     * @param updateDays   只处理最近 N 天有新增/更新的酒店（null=全量，对齐旧 updateDays/allPushFlag）
     * @param startLine    从文件第 N 行开始（断点续跑，对齐旧 startLine）
     */
    public Map<String, Object> seed(Set<String> countryCodes, int limit, boolean ingest, boolean transform,
                                    boolean download, Integer updateDays, int startLine) {
        File file = catalogFileClient.downloadCatalog(null, download);
        ExpediaCatalogFileClient.SeedResult result =
                catalogFileClient.streamPropertyIds(file, countryCodes, limit, updateDays, startLine);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("catalogFile", file.getAbsolutePath());
        summary.put("scannedLines", result.scannedLines());
        summary.put("lastMatchedLine", result.lastMatchedLine());
        summary.put("matched", result.propertyIds().size());

        if (ingest && !result.propertyIds().isEmpty()) {
            long start = System.currentTimeMillis();
            int[] counts = ingestConcurrently(result.propertyIds(), transform);
            summary.put("ingestedRows", counts[0]);
            if (transform) {
                summary.put("transformedHotels", counts[1]);
            }
            summary.put("costMs", System.currentTimeMillis() - start);
        }
        log.info("Catalog seed 完成: {}", summary);
        return summary;
    }

    /**
     * 并发摄取+加工（对齐旧链路 ThreadPoolUtils 用法：分片 → submit → Future 汇总）
     */
    private int[] ingestConcurrently(List<String> propertyIds, boolean transform) {
        int numberOfParts = Math.max(1, (propertyIds.size() + PART_SIZE - 1) / PART_SIZE);
        List<List<String>> parts = splitListIntoParts(propertyIds, numberOfParts);
        List<Future<int[]>> futures = new ArrayList<>();
        for (List<String> part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            futures.add(ThreadPools.fixedCallerRuns(ExpediaGeographyIngestionService.CONTENT_POOL_NAME, 20, 1000).submit(() -> {
                int ingested = ingestionService.ingestByPropertyIds(part, null);
                int transformed = transform ? transformService.transformByPropertyIds(part) : 0;
                return new int[]{ingested, transformed};
            }));
        }
        int ingested = 0;
        int transformed = 0;
        for (Future<int[]> future : futures) {
            try {
                int[] counts = future.get();
                ingested += counts[0];
                transformed += counts[1];
            } catch (Exception e) {
                log.error("Catalog seed 分片执行失败", e);
            }
        }
        return new int[]{ingested, transformed};
    }

    /** 大集合均分为 N 份（原居 ThreadPoolUtils，唯一调用方是本类，随其退役搬入） */
    private static <T> java.util.List<java.util.List<T>> splitListIntoParts(java.util.List<T> list, int numberOfParts) {
        final int size = list.size();
        final int chunkSize = size / numberOfParts;
        final int leftOver = size % numberOfParts;
        java.util.List<java.util.List<T>> parts = new java.util.ArrayList<>(numberOfParts);
        int start = 0;
        for (int i = 0; i < numberOfParts; i++) {
            int end = start + chunkSize + (i < leftOver ? 1 : 0);
            parts.add(new java.util.ArrayList<>(list.subList(start, end)));
            start = end;
        }
        return parts;
    }
}
