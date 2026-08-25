package com.trip.booking.spa.gateway.application.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 2026-08-19 的生产缺陷：刷价轮次曾被一个自造的「等待预算」截断，每轮丢 41% 的行。
 *
 * <p>根因不在公式算错，而在<b>这个预算本不该存在</b>。刷价是后台循环、没有外部截止时间；
 * 而"一轮跑过头"早就有正确处理——供应商执行器单线程 + SynchronousQueue，下一次 cron 被拒并
 * 记日志，跳一次是安全的、一行不丢。自造的预算把这个安全行为换成了截断：预算按 行数/配额 算，
 * 隐含假设总能跑到配置的 QPS，而实际速率是 min(配额, 并发度/单行耗时)。常规档配额 4、并发 3，
 * 实际 1.82 QPS，预算 220s 与实需 220s 撞上 → 连续三轮 shutdownNow，每轮丢 166/400 行；
 * 且那些行的 last_time 已在调用前更新，会被当成"刚刷过"排到队尾饿死。
 */
class RoundNoTruncationTest {

    /** 2026-08-25 起等待逻辑在骨架里（各家共用一份），故本测试盯骨架而不是艺龙实现 */
    private static final Path SKELETON = Path.of("src/main/java/com/trip/booking/spa/gateway"
            + "/application/pricing/AbstractCPSQueryPriceService.java");

    @Test
    @DisplayName("轮次不得因超时截断——只有容器关闭那一条路径可以 shutdownNow")
    void mustNotTruncateOnTimeout() throws Exception {
        String src = Files.readString(SKELETON);

        // awaitTermination 的返回值只允许用于「继续等 + 报告」，不允许接 shutdownNow
        assertFalse(src.contains("pool.shutdownNow();\n            }"),
                "又出现了「等不到就 shutdownNow」。刷价没有截止时间，跑久一点没有代价，"
                        + "而截断会丢行、且丢掉的行 last_time 已更新会被饿死");

        // shutdownNow 只应出现在 InterruptedException 分支（容器关闭）。
        // 只数真实调用，不数注释里提到它的文字——否则"解释为什么不该截断"的注释会把测试搞红
        int calls = src.split("pool\\.shutdownNow\\(\\)", -1).length - 1;
        assertTrue(calls == 1,
                "pool.shutdownNow() 调用 " + calls + " 次。只有容器关闭（InterruptedException）"
                        + "这一条路径才该打断在跑的行");
        assertTrue(src.contains("catch (InterruptedException"),
                "唯一的 shutdownNow 必须挂在 InterruptedException 分支上");

        // 必须是「等不到就再等」的循环，而不是一次性判断
        assertTrue(src.contains("while (!pool.awaitTermination("),
                "必须用 while 循环持续等待并报告，不能 if 一次就放弃");
    }

    @Test
    @DisplayName("估时取 min(配额, 并发能力)，且只用于报告阈值")
    void estimateFollowsTheSlowerOfQuotaAndConcurrency() {
        // 事故当时的取值：400 行、配额 4 QPS、并发 3 → 实测 1.82 QPS，实需约 220s
        long est = AbstractCPSQueryPriceService.estimateRoundSeconds(400, 4.0, 3);
        assertTrue(est > 180 && est < 300,
                "估时 " + est + "s 与实测的 220s 不符；只按配额算会得到 100s，"
                        + "那正是当年把预算算成 220s 的来源");

        // 并发富余时瓶颈回到配额
        long ample = AbstractCPSQueryPriceService.estimateRoundSeconds(400, 4.0, 60);
        assertTrue(ample <= 100, "并发富余时估时应由配额决定，实际 " + ample + "s");

        // 安全侧兜底（并发 1）只是慢，不该被当成异常处理掉。用相对判据而非绝对秒数：
        // 单行耗时常数将来会随实测调整，写死秒数会让调常数时假红
        long serial = AbstractCPSQueryPriceService.estimateRoundSeconds(900, 4.0, 1);
        long conc6 = AbstractCPSQueryPriceService.estimateRoundSeconds(900, 4.0, 6);
        assertTrue(serial >= conc6 * 4,
                "并发 1 的估时应如实反映串行之慢（用于报告阈值）：串行 " + serial
                        + "s 对并发6 " + conc6 + "s，差距不足 4 倍说明估时没跟着并发走");
    }
}
