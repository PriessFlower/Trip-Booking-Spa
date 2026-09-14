package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住升档的档位限定:upgradeByShId 只升档 0/1,不碰档 2。
 *
 * <p>档位自 2026-08-28 三家统一为住期远近:0=T+0~2 / 1=T+3~7 / 2=T+8~30,无货态=业务档+10。
 * 验价事件说明这位客人要订的是<b>近期</b>日期,档 2 的远期行不急;且档 2 行数远多于档 0 的
 * 单轮批量,借进来会把真正的近期行挤饿。故限定留在写入侧。
 *
 * <p><b>历史</b>:本测试原本钉的是"借入反成降级"——批次5 曾把档 2 改作成交档(高德出过单的
 * 464 家,每轮全扫、缓存龄 ≤30 分钟),比档 0 的 LRU 轮转更快,借进档 0 反而是降级。成交档已
 * 随统一档位退役,SQL 的 in (0, 1) 保留、理由换成上面那条;是否放开档 2 借入尚未复核。
 *
 * <p>限定必须在<b>写入侧</b>。读取侧(getQueryPriceTaskList)不能加档位过滤:存量的已升档
 * 非 0/1 档行会哪档都不匹配,即刻变成 issue #95 的孤儿行——而复位只在行被取到时执行,
 * 取不到即永不复位、永不再刷。
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
    @DisplayName("升档只能碰档 0/1——档 2 是远期档,借进来会挤饿近期行")
    void upgradeOnlyTouchesRotatingTiers() throws Exception {
        String upgrade = block(Files.readString(MAPPER_XML), "<update id=\"upgradeByShId\">", "</update>");

        assertTrue(upgrade.contains("sh_id = #{shId}"),
                "升档仍应按酒店维度整店生效(F-6.1),不要退化成按行升档");
        assertTrue(upgrade.contains("priority_level_number in (0, 1)"),
                "upgradeByShId 又变成不限档位升档了。档 2 是远期档(T+8~30),验价者要订的是近期日,"
                        + "远期行不急;且档 2 行数远多于档 0 的单轮批量,借进来会把真正的近期行挤饿");
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
                "借入判定变了。只有档 0 可以借入(F-2.4.1)：档 2 是远期档,借进档 0 会把真正的"
                        + "近期行挤饿");
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
