package com.trip.booking.spa.gateway.application.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫改写后的 F-2.2：刷价可以常驻循环，但<b>每轮之间必须重新读闸</b>。
 *
 * <p>为什么要一条测试盯着：原规则禁止常驻 {@code while(true)}，理由是"关闸最迟要等进程重启才
 * 生效"。2026-08-25 改成连续循环换回了 45% 的空等时间，代价是那条理由变成了必须由代码结构
 * 保证的东西——闸读在循环<b>里</b>还是<b>外</b>，是"关闸延迟一轮"与"关闸要重启"的分界，
 * 而这个区别在代码上只差一个括号的位置。
 *
 * <p>另一半是"各家不得自己写循环"：一旦某家自己写一份，这条纪律就只对骨架成立。
 */
class RefreshLoopHonoursGateTest {

    private static final Path SKELETON = Path.of("src/main/java/com/trip/booking/spa/gateway"
            + "/application/pricing/AbstractCPSQueryPriceService.java");

    @Test
    @DisplayName("闸必须读在循环条件上，不能只在循环外读一次")
    void gateIsReadInsideTheLoop() throws Exception {
        String src = Files.readString(SKELETON);

        assertTrue(src.contains("while (gateOpen())"),
                "循环条件不是 gateOpen()。闸读在循环外＝关掉 Nacos 开关也停不下来，"
                        + "那正是原 F-2.2 禁止常驻循环的理由；读在循环条件上，关闸延迟才收敛到一轮");

        // 退出条件必须存在：没活时要退出，否则空表上会以限流器的速率空转
        assertTrue(src.contains("if (!didWork)"),
                "没有「各档都没活就退出」的分支。表空时循环会一直取批、一直空转，"
                        + "既刷不出价又持续占着分布式锁");
    }

    @Test
    @DisplayName("循环只许有一份实现——各家不得自己写")
    void nobodyWritesTheirOwnLoop() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith("CPSQueryPriceServiceImpl.java")).toList()) {
                String s = Files.readString(p);
                if (s.contains("while (") || s.contains("while(")) {
                    offenders.add(p.getFileName().toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "这些刷价实现里出现了 while：" + offenders + "。循环、锁、取批、计数、指标都在骨架里，"
                        + "各家只答「取哪一批、按哪些维度刷、怎么刷一行、怎么记账」。"
                        + "自己写一份循环就等于自己维护一遍关闸纪律，而那是会漂的");
    }

    @Test
    @DisplayName("cron 的角色是看门狗，注释必须写明——否则下一个人会按周期去调节奏")
    void cronRoleIsDocumented() throws Exception {
        String task = Files.readString(Path.of("src/main/java/com/trip/booking/spa/gateway/adapter"
                + "/inbound/scheduler/ElongCPSQueryPriceTask.java"));
        assertTrue(task.contains("看门狗"),
                "调度类没说明 cron 已从节拍器变成看门狗。不写清楚，运维会以为调 cron 周期能改刷价节奏，"
                        + "而实际节奏由循环和批量决定");
    }

    @Test
    @DisplayName("骨架不得自建限流器")
    void skeletonDoesNotBuildItsOwnLimiter() throws Exception {
        String src = Files.readString(SKELETON);
        assertFalse(src.contains("RateLimiter.create"),
                "骨架自建了限流器。限流一律走统一限流（§3.3），速率取值只在 Nacos");
    }
}
