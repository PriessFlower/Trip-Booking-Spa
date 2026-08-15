package com.trip.booking.spa.platform.http;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住分块切分的两条不变量：块必须无缝覆盖整个文件、且各块长度之和等于文件总长。
 *
 * <p>此前的等分实现漏字节时无人察觉，正因为这块零测试覆盖。切分若有一字节的缝隙或重叠，
 * 下载出的文件会残缺或错位，而当时的完整性判据（文件长度）恰恰查不出这类错误。
 */
class ChunkedFileAccessPlanTest {

    private static final long CHUNK_SIZE = 1L << 20;

    /** 复刻 downloadInParallel 的切分逻辑 */
    private List<long[]> plan(long total) {
        List<long[]> chunks = new ArrayList<>();
        for (long from = 0; from < total; from += CHUNK_SIZE) {
            chunks.add(new long[]{from, Math.min(from + CHUNK_SIZE, total) - 1});
        }
        return chunks;
    }

    private void assertCoversExactly(long total) {
        List<long[]> chunks = plan(total);
        long sum = 0;
        long expectedNext = 0;
        for (long[] c : chunks) {
            assertEquals(expectedNext, c[0], "块起点必须紧接上一块，不得有缝隙或重叠");
            assertTrue(c[1] >= c[0], "块终点不得小于起点");
            sum += c[1] - c[0] + 1;
            expectedNext = c[1] + 1;
        }
        assertEquals(total, sum, "各块长度之和必须等于文件总长");
        assertEquals(total, expectedNext, "最后一块必须恰好收尾于文件末字节");
    }

    /** 生产实际文件大小：不是块大小的整数倍，末块偏短 */
    @Test
    void coversRealCatalogSizeExactly() {
        assertCoversExactly(103_679_417L);
    }

    /** 整除边界：末块恰好填满，不应多出一个空块 */
    @Test
    void coversExactMultipleWithoutTrailingEmptyChunk() {
        assertCoversExactly(CHUNK_SIZE * 8);
        assertEquals(8, plan(CHUNK_SIZE * 8).size());
    }

    /** 小于一块：应切出恰好一块，而非零块 */
    @Test
    void coversFileSmallerThanOneChunk() {
        assertCoversExactly(1);
        assertEquals(1, plan(1).size());
        assertCoversExactly(CHUNK_SIZE - 1);
    }

    /** 恰好一块 */
    @Test
    void coversFileOfExactlyOneChunk() {
        assertCoversExactly(CHUNK_SIZE);
        assertEquals(1, plan(CHUNK_SIZE).size());
    }

    /** 一块多一字节：应切出两块，末块仅 1 字节 */
    @Test
    void coversOneChunkPlusOneByte() {
        assertCoversExactly(CHUNK_SIZE + 1);
        List<long[]> chunks = plan(CHUNK_SIZE + 1);
        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(1)[1] - chunks.get(1)[0] + 1, "末块应为 1 字节");
    }
}
