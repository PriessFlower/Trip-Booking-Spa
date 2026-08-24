package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住"借入反成降级"缺陷:验价升档曾把成交档酒店从快车道抽进慢车道。
 *
 * <p>缺陷由两处各自正确的改动交叉而成。{@code upgradeByShId} 原本不过滤档位,因为
 * 当时只有档 0(高频)/档 1(常规)/档 2(兜底),<b>档号越大越不急</b>,借进档 0 恒为提速。
 * 批次5 把档 2 改作成交档(高德出过单的 464 家,T+0~2,每轮全扫、缓存龄 ≤30 分钟)、
 * 新增档 3 远期档(同一批酒店 T+7~29,每店 23 行),这个前提就没了。
 *
 * <p>于是:一次验价 → 该店 3 条成交行 + 23 条远期行全部 temporary_upgrade=1 →
 * 成交/远期档取批以 AND 排除借入行,这 26 行离开本档 → 全被档 0 的 OR 借走。后果两条,
 * 都与反馈环初衷相反:成交档"≤30 分钟"的承诺对<b>刚被验价、最可能马上下单</b>的那家
 * 酒店失效,退回档 0 约 2.6h 的 LRU 轮转;而档 0 每轮只取 400 行,464 家 × 26 行的
 * 借入池足以把真档 0 的行整批挤饿。
 *
 * <p>修法是在<b>写入侧</b>限定可升档的档位。读取侧(getQueryPriceTaskList)不能加档位
 * 过滤:存量的已升档档 2/3 行会哪档都不匹配,即刻变成 issue #95 的孤儿行——而复位只在
 * 行被取到时执行,取不到即永不复位、永不再刷。
 */
class BorrowNeverDemotesTest {

    private static final Path MAPPER_XML = Path.of("src/main/resources/mapper/ElongQueryPriceTaskMapper.xml");

    private static final Path SCHEDULER = Path.of("src/main/java/com/trip/booking/spa/gateway/adapter"
            + "/inbound/scheduler/ElongCPSQueryPriceTask.java");

    @Test
    @DisplayName("升档只能碰轮转档(0/1)——成交档比档 0 更快,借入是降级")
    void upgradeOnlyTouchesRotatingTiers() throws Exception {
        String upgrade = block(Files.readString(MAPPER_XML), "<update id=\"upgradeByShId\">", "</update>");

        assertTrue(upgrade.contains("sh_id = #{shId}"),
                "升档仍应按酒店维度整店生效(F-6.1),不要退化成按行升档");
        assertTrue(upgrade.contains("priority_level_number in (0, 1)"),
                "upgradeByShId 又变成不限档位升档了。成交档(2)每轮全扫、缓存龄 ≤30 分钟,"
                        + "借进档 0 的 2.6h 轮转是降级,降的还是刚被验价、最可能下单的那家;"
                        + "远期档(3)每店 23 行,464 家借进档 0 的 400 批量会把真档 0 挤饿");
    }

    @Test
    @DisplayName("取批侧不得按档位过滤借入行——那会把存量借入行变成孤儿")
    void fetchMustNotBoundBorrowedRowsByTier() throws Exception {
        String select = block(Files.readString(MAPPER_XML), "<select id=\"getQueryPriceTaskList\"", "</select>");
        String borrowBranch = block(select, "<if test=\"1 == temporaryUpgrade\">", "</if>");

        assertFalse(borrowBranch.contains("priority_level_number"),
                "借入分支加了档位过滤。看似与写入侧限定等价,实则不然:改前已升档的档 2/3 行"
                        + "会哪档都不匹配,而复位只在行被取到时执行 → 永不复位、永不再刷(issue #95)。"
                        + "限定必须留在写入侧,存量行靠 24h 到期自愈");
    }

    @Test
    @DisplayName("成交档与远期档必须以 AND 排除借入行,只有档 0 带借入")
    void onlyHighTierTakesBorrowedRows() throws Exception {
        String src = Files.readString(SCHEDULER);

        assertTrue(src.contains("queryPriceQueueTask(0, 1,"),
                "档 0 必须带借入(第二参 1),否则升档的行没人跟刷,反馈环空转");
        assertTrue(src.contains("queryPriceQueueTask(2, 0,"),
                "成交档必须排除借入行(第二参 0)。若改成 1,它会与档 0 同时命中借入行 → 双刷,"
                        + "且破坏 mapper 注释里「任一行恰好命中一档」的不变量");
        assertTrue(src.contains("queryPriceQueueTask(3, 0,"),
                "远期档必须排除借入行(第二参 0),理由同成交档");
    }

    /** 截出 XML/源码里某个标签块,避免整文件 contains 命中别处的同名片段。 */
    private static String block(String text, String startTag, String endTag) {
        int start = text.indexOf(startTag);
        assertTrue(start >= 0, "找不到起始标记:" + startTag);
        int end = text.indexOf(endTag, start);
        assertTrue(end > start, "找不到结束标记:" + endTag + "(起始:" + startTag + ")");
        return text.substring(start, end);
    }
}
