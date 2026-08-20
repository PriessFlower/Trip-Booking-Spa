package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespCacheDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据缓存（{@code quote:*}）里允许放什么——白名单式钉死。
 *
 * <p><b>为什么用白名单而不是"别放金额"的黑名单</b>：这个 DTO 的历史就是一路加字段加到
 * 24 个（Redis 里 17.5 万条详情吃掉该实例 97.4% 的键、1.19G/2G 内存）。加字段永远比删
 * 字段容易，且每次加都有当下看着合理的理由。白名单让"加一个"必须先改这条测试，
 * 从而必须先回答"它凭什么进 Redis"。
 *
 * <p>判据就是 R-2.6 那一句：<b>供应商明天换一批报价码，这条信息还对吗？</b>
 * 对 → MySQL 档案表；错 → 这里。
 */
class QuotePayloadContentTest {

    /**
     * 允许出现在票据缓存里的字段。每一项都要能回答"为什么读侧算不出来"。
     *
     * <ul>
     *   <li>{@code productId}——供应商报价码，易腐，且依 R-2.1 禁止落库，只能在这儿；</li>
     *   <li>{@code productKey}——通往档案表的桥，删了库里的属性就再也取不出来；</li>
     *   <li>{@code storePayCurrency} / {@code currencyType} / {@code currency}——币种不在
     *       每日价里，也不是产品的稳定属性，读侧无处可算；</li>
     *   <li>{@code cancelPolicy}——条款是「卖法 × 住期」的函数，档案表一行只对应一个卖法、
     *       没有住期维度，装不下（它只存得下粗分类 cancel_class）。2026-08-20 曾把它删掉，
     *       后果是 26,011 个可免费取消的卖法在渠道侧全部显示不可退。</li>
     * </ul>
     */
    private static final Set<String> ALLOWED = Set.of(
            "productId", "productKey", "storePayCurrency", "currencyType", "currency", "cancelPolicy");

    private static List<String> declaredFields() {
        return Arrays.stream(ProductRespCacheDTO.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("票据缓存的字段必须在白名单内——新增字段须先说明它凭什么进 Redis")
    void onlyWhitelistedFieldsMayLiveInRedis() {
        List<String> extra = declaredFields().stream()
                .filter(f -> !ALLOWED.contains(f))
                .collect(Collectors.toList());

        assertTrue(extra.isEmpty(),
                "这些字段不在白名单里：" + extra
                        + "\n判据 R-2.6：供应商明天换一批报价码，这条信息还对吗？对 → 进 MySQL 档案表，错 → 才可进这里。"
                        + "\n若它是金额：出价读侧会按客人查询区间逐日重算并覆盖，放这儿写了也白写。");
    }

    /**
     * 金额一个都不许有。
     *
     * <p>它们不只是浪费：{@code totalPrice} 正是 2026-08-19 换票基准取错量级的来源——
     * 缓存里那份是<b>刷价那一次</b>区间的快照（通常 1 晚），而客人查的是自己的区间。
     * 当时改了调用方，字段却留着；留着就还是陷阱，下一个人照样会去读它。
     */
    @Test
    @DisplayName("金额字段一个都不许回到票据缓存")
    void noMonetaryFieldsComeBack() {
        List<String> money = declaredFields().stream()
                .filter(f -> f.equals("totalPrice") || f.equals("totalTaxes") || f.equals("roomTotalPrice")
                        || f.equals("stayPrice") || f.equals("storePayPrice") || f.equals("brokerage"))
                .collect(Collectors.toList());

        assertTrue(money.isEmpty(),
                "金额字段回到了票据缓存：" + money
                        + "\n读侧在 copyProperties 之后会逐项重算覆盖它们，写进 Redis 纯占地方；"
                        + "\n且 totalPrice 是刷价那次区间的快照，被当基准用过一次、错了一个量级。");
    }

    @Test
    @DisplayName("桥不能断：productKey 必须在")
    void theBridgeMustExist() {
        assertTrue(declaredFields().contains("productKey"),
                "删了 productKey，出价拿着易腐的 productId 就再也查不到档案表里的属性——"
                        + "productId 依 R-2.1 不落库，没有第二条路");
    }

    @Test
    @DisplayName("字段总数应当很小——它是一条每轮刷价都要重写的记录")
    void payloadStaysSmall() {
        assertEquals(ALLOWED.size(), declaredFields().size(),
                "实际字段: " + declaredFields());
    }
}
