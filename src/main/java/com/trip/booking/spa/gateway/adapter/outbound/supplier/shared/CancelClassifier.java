package com.trip.booking.spa.gateway.adapter.outbound.supplier.shared;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 退改三分类的<b>唯一</b>判据（R-5.1）。三家此前各抄一份私有 {@code classifyCancel}，
 * 同一句注释抄了三遍、同一个漏也漏了三遍：<b>都不判段是否已过期</b>。
 *
 * <p>2026-09-02 生产实测：艺龙 659 条"可免费取消"里 94 条（14.3%）的免费窗早已关闭
 * （店 11957937 入住 09-03、{@code before=78} → 免费截止 08-31 18:00，读到时已是 09-02），
 * 飞猪 90 条里 4 条。供应商把作废条款照抄在报文里，我们照单全收判成免费可退。
 *
 * <p>两处代价：① 对外可能宣称一个订不到的免费取消（客人退订被罚=资损）；
 * ② 同一卖法在相邻住期被判成两种（一天有过期免费段、一天没有），productKey 随之分裂，
 * 多晚查询凑不齐每一天而整条丢弃（{@code day_count_mismatch}）。
 *
 * <p><b>过期判据</b>：段的截止时刻 = 入住日 24:00 − {@code before} 小时（与
 * {@code before} 的定义同源，见 {@link CancelPolicy#getBefore()}）；早于 {@code now} 即作废。
 * 基准时区取段自带的 {@code timeZone}，缺失或不可解析时回落北京时间——生产容器跑 UTC，
 * 用服务器时区会把"还能免费取消多久"说长 8 小时。
 *
 * <p><b>为什么在适配层而不在 domain</b>：判据吃的是归一化后的 {@link CancelPolicy}
 * （住在 inbound dto），而 {@code gateway/domain} 是纯模型、不许 import 任何 adapter
 * （{@code CancellationLayerBoundaryTest}）。故落在供应商共享包，三家同层复用。
 *
 * <p>本类<b>只做判定不做兜底</b>：判不出的一律 {@link CancelClass#UNKNOWN}（元规则 R-1.6
 * 赌错只许少卖）——UNKNOWN 照常可售，但不进产品目录（R-5.4）。
 */
public final class CancelClassifier {

    /** 回落基准时区：{@code before} 的口径本就以北京时间的入住日界为基准 */
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Shanghai");

    private CancelClassifier() {
    }

    /**
     * 判类。{@code totalCents} 用于"定额罚金≥全款=经济上不可退"，缺失时该分支不成立
     * （与各家原实现一致：Expedia 此前只按比例判，不传总价）。
     */
    public static CancelClass classify(List<CancelPolicy> policies, String checkIn,
                                       Integer totalCents, Instant now) {
        if (CollectionUtils.isEmpty(policies)) {
            return CancelClass.UNKNOWN;
        }
        List<CancelPolicy> live = liveSegments(policies, checkIn, now);
        if (live.isEmpty()) {
            // 全部段都已作废：此刻取消按什么罚已无从判读，不许猜成免费或不可退
            return CancelClass.UNKNOWN;
        }
        if (live.stream().anyMatch(CancelClassifier::isFreeWindow)) {
            // 存在仍然有效的免费取消窗（R-5.1 的 FREE 判据），罚金阶梯照常跟在后面
            return CancelClass.FREE_CANCELLABLE;
        }
        if (live.stream().allMatch(p -> Integer.valueOf(0).equals(p.getCancelType()))) {
            return CancelClass.NON_REFUNDABLE;
        }
        if (live.stream().allMatch(p -> p.deductsFullPrice(totalCents))) {
            // 每段确定罚≥全款=经济上不可退（三家同判据：艺龙 CutType=4 官方语义即全额房费；
            // 飞猪 305 条采样里全程收费形态 122/122 罚金=全款，docs/fliggy §2；
            // Expedia 为比例 100%）
            return CancelClass.NON_REFUNDABLE;
        }
        // 罚金阶梯存在但判不出"全款"（首晚/按晚/定额低于总价）：三分类无处安放
        return CancelClass.UNKNOWN;
    }

    /**
     * 仍然有效的段——即对外能承诺、也是判类唯一该看的那些。
     *
     * <p>出报侧同样要过这道滤：只修判类不修出报，客人照旧会看到一个已经关闭的免费窗。
     */
    public static List<CancelPolicy> liveSegments(List<CancelPolicy> policies, String checkIn,
                                                  Instant now) {
        if (CollectionUtils.isEmpty(policies)) {
            return List.of();
        }
        if (checkIn == null || now == null) {
            // 无入住日或无时钟＝无从判过期，原样返回（宁可少判，不可误删有效段）
            return policies;
        }
        Instant checkInEnd = checkInEndOf(policies.get(0), checkIn);
        if (checkInEnd == null || !checkInEnd.isAfter(now)) {
            // 住期已开始或已过去：这单本就不可订，过滤无意义（考古/对账要看完整阶梯）
            return policies;
        }
        List<CancelPolicy> live = policies.stream()
                .filter(p -> !isExpired(p, checkIn, now)).toList();
        if (!live.isEmpty()) {
            return live;
        }
        // 全滤空时看终段（before 最小的那条）：它的 before 常是钳到 25 的哨兵值
        // （表示"此后一直"），不是一个会关闭的窗，故罚金终段要保住——它恰恰是
        // "此刻取消要罚多少"的那一段。但若终段本身就是免费窗，保它等于凭空造一个
        // 免费承诺，宁可回空让判类落 UNKNOWN（R-1.6 赌错只许少卖）
        return policies.stream()
                .min(java.util.Comparator.comparingInt(CancelPolicy::getBefore))
                .filter(p -> !isFreeWindow(p))
                .map(List::of).orElse(List.of());
    }

    /** 免费窗：不扣费且未被标成"不可取消"（{@code cancelType=0} 是不可取消的标记段） */
    private static boolean isFreeWindow(CancelPolicy policy) {
        return RefundType.NO_DEDUCTION == policy.getType()
                && !Integer.valueOf(0).equals(policy.getCancelType());
    }

    private static boolean isExpired(CancelPolicy policy, String checkIn, Instant now) {
        Instant deadline = deadlineOf(policy, checkIn);
        return deadline != null && deadline.isBefore(now);
    }

    /** 入住日 24:00（段自带时区，缺失回落北京） */
    private static Instant checkInEndOf(CancelPolicy policy, String checkIn) {
        try {
            return LocalDate.parse(checkIn).plusDays(1).atStartOfDay(zoneOf(policy)).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /** 段截止时刻 = 入住日 24:00 − before 小时；解析不出返回 null（按未过期处理） */
    private static Instant deadlineOf(CancelPolicy policy, String checkIn) {
        try {
            ZoneId zone = zoneOf(policy);
            Instant checkInEnd = LocalDate.parse(checkIn).plusDays(1).atStartOfDay(zone).toInstant();
            return checkInEnd.minus(Duration.ofHours(policy.getBefore()));
        } catch (Exception e) {
            return null;
        }
    }

    private static ZoneId zoneOf(CancelPolicy policy) {
        String tz = policy.getTimeZone();
        if (tz == null || tz.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            // 形如 GMT+08:00（各家归一化时写入）
            return ZoneId.of(tz);
        } catch (Exception e) {
            return FALLBACK_ZONE;
        }
    }
}
