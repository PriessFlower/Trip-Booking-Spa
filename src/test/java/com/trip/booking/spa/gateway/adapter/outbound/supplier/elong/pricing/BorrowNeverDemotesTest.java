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

    private static final Path SERVICE = Path.of("src/main/java/com/trip/booking/spa/gateway/adapter"
            + "/outbound/supplier/elong/pricing/ElongCPSQueryPriceServiceImpl.java");
    private static final Path SKELETON = Path.of("src/main/java/com/trip/booking/spa/gateway"
            + "/application/pricing/AbstractCPSQueryPriceService.java");
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

    /**
     * 2026-08-25 起档位序列与借入判定在服务实现里（{@code tiers()} / {@code borrowFor()}），
     * 不再是调度类里的字面参数——档序是刷价语义，属适配层的知识。守的规则没变，只是位置变了。
     * 2026-08-28 起档位=住期远近(与飞猪统一,模板偏移算法)，近档先消费、无货位殿后。
     */
    @Test
    @DisplayName("近档最先、无货位殿后,且只有档 0 带借入")
    void onlyHighTierTakesBorrowedRows() throws Exception {
        String src = Files.readString(SERVICE);

        assertTrue(src.contains("List.of(0, 1, 2, SOLD_OUT_OFFSET, SOLD_OUT_OFFSET + 1, SOLD_OUT_OFFSET + 2)"),
                "档位序列变了。近档(0=T0-2)须最先——卖得最急；无货位(10/11/12)须殿后——"
                        + "只为低频探活,排前面会侵占有货店的节奏");

        // 借入判定：默认实现只让档 0 借入。各家若要覆写，必须仍然只有一档借入
        String skeleton = Files.readString(SKELETON);
        assertTrue(skeleton.contains("priority == 0 ? 1 : 0"),
                "借入判定变了。只有档 0 可以借入(F-2.4.1)：成交档比档 0 更快,借进来是降级,"
                        + "而降的正是刚被验价、最可能马上下单的酒店；远期档每店 23 行,"
                        + "464 家的借入池会挤爆档 0 的批量");
        assertFalse(src.contains("borrowFor"),
                "艺龙覆写了 borrowFor。目前没有理由偏离默认(只有档 0 借入),"
                        + "若确有理由,请连同 F-2.4.1 一起改并说明");
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
