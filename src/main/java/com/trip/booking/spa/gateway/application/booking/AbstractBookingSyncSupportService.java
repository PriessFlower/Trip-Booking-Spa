package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.domain.booking.BookingResult;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 下单模板。
 *
 * <p>模板持有<b>安全护栏闸口</b>（§3.8）：下单是唯一会真扣钱的链路，每家都必须能
 * 不发版就关掉它。此前这道闸是各家自己写的——艺龙写了，Expedia 只写在 BFF
 * （独立项目，与网关这条路无关，§0.4），飞猪连配置项都没有，于是三家里只有一家
 * 真有闸，而"漏了"在编译期与运行期都不报错。凡是「新接一家必须记得写」的安全
 * 措施，都该长在骨架上（同 {@code AbstractCheckPriceFlow} 的理由）。
 *
 * <p>此前还有一道"实现方漏填 outcome 就按 UNKNOWN 处理"的运行期兜底，随本次切领域模型
 * 删除：{@link BookingResult} 的三态由工厂钉死，漏填在构造上就不成立。
 */
@Slf4j
public abstract class AbstractBookingSyncSupportService implements BookingSyncService {

    /**
     * 安全护栏闸口的配置键名，进拦截日志（§3.8.4 拦截可定位）。
     * 例：{@code elong.booking-enabled}。
     */
    protected abstract String bookingGateKey();

    /**
     * 安全护栏：是否允许向该供应商真实下单。取值来自各家 properties，模板只读不判来源。
     *
     * <p><b>§3.8.5 风险与执行面声明</b>
     * <ul>
     *   <li><b>误开的后果</b>：向供应商提交真实订单并即时扣款。艺龙无沙箱、飞猪无沙箱，
     *       误开即真单真钱；Expedia 误开时是否真单还取决于端点指向沙箱还是生产。</li>
     *   <li><b>误关的后果</b>：该家下单全部确定失败（FAILED，非 UNKNOWN——供应商侧
     *       什么都没发生，上游可直接终结订单并退款，不必查单）。不影响查价、验价、
     *       查单、取消。</li>
     *   <li><b>生效执行面</b>：所有承载 {@code /booking} 端点的节点，与 profile 无关。</li>
     * </ul>
     */
    protected abstract boolean bookingAllowed();

    @Override
    public BookingResult booking(BookingCommand command) {
        try {
            // §3.8.3 关闸即停做功：判定在最外层，关闸后不解析凭据、不取句柄、不组装报文
            if (!bookingAllowed()) {
                log.info("闸口 {} 关闭，拒绝下单,orderId={},sHotelId={}",
                        bookingGateKey(), command.orderId(), command.supplierHotelId());
                return BookingResult.failed(command.orderId(), "booking_disabled",
                        "下单未开通（安全护栏 " + bookingGateKey() + " 关闭）",
                        "下单未开通（安全护栏关闭），供应商侧未发生任何动作");
            }
            BookingResult result = doBooking(command);

            log.info("BookingSyncService orderId={}, bookingResult:{}",
                    command.orderId(), JsonUtils.writeObject2Json(result));

            if (result == null) {
                // 无响应不等于未下单：请求可能已送达供应商而响应丢失，须交上游查单确证
                log.error("BookingSyncService doBooking 无响应，回报 UNKNOWN, orderId={}", command.orderId());
                return BookingResult.unknown(command.orderId(), "供应商无响应，结果不确定，请查单确证");
            }
            // 实现漏回显我方单号时补上；其余字段一律不动
            return result.withOrderId(command.orderId());
        } catch (Exception e) {
            // 异常同样不足以断定未下单：连接在请求发出后中断，与请求根本没发出，在本地无从区分
            log.error("BookingSyncService 异常，回报 UNKNOWN, orderId={}", command.orderId(), e);
            return BookingResult.unknown(command.orderId(),
                    "下单过程异常，结果不确定，请查单确证：" + e.getClass().getSimpleName());
        }
    }

    /**
     * 向供应商下单并给出三态结论。
     *
     * <p>此前是 {@code doBooking} 产原始响应、{@code bookingRespConvert} 再转一道，
     * 而三家的转换全是纯字段拷贝（飞猪那个甚至是恒等函数），两家还各自养了一个与
     * {@link BookingResult} 同形的 {@code BookingOutcomeHolder}。同一形状写了四遍，
     * 遂与取消能力同规收成一步。
     */
    protected abstract BookingResult doBooking(BookingCommand command);
}
