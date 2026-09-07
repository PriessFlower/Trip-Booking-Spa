package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
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
 */
@Slf4j
public abstract class AbstractBookingSyncSupportService<T> implements BookingSyncService {

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
    public BookingRespDTO booking(BookingReq bookingReq) {
        try {
            // §3.8.3 关闸即停做功：判定在最外层，关闸后不解析凭据、不取句柄、不组装报文
            if (!bookingAllowed()) {
                log.info("闸口 {} 关闭，拒绝下单,orderId={},sHotelId={}",
                        bookingGateKey(), bookingReq.getOrderId(), bookingReq.getSHotelId());
                return BookingRespDTO.builder()
                        .outcome(BookingOutcome.FAILED)
                        .orderId(bookingReq.getOrderId())
                        .supplierErrorCode("booking_disabled")
                        .supplierErrorMessage("下单未开通（安全护栏 " + bookingGateKey() + " 关闭）")
                        .orderDesc("下单未开通（安全护栏关闭），供应商侧未发生任何动作")
                        .build();
            }
            T t = doBooking(bookingReq);

            log.info("BookingSyncService bookingReq : {}, bookingResp:{}", JsonUtils.writeObject2Json(bookingReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                // 无响应不等于未下单：请求可能已送达供应商而响应丢失，须交上游查单确证
                log.error("BookingSyncService doBooking 无响应，回报 UNKNOWN, orderId={}", bookingReq.getOrderId());
                return unknown(bookingReq, "供应商无响应，结果不确定，请查单确证");
            }

            BookingRespDTO bookingRespDTO = bookingRespConvert(t);

            if (bookingRespDTO == null) {
                log.error("BookingSyncService bookingRespConvert 返回空，回报 UNKNOWN, orderId={}, 原始响应={}",
                        bookingReq.getOrderId(), JsonUtils.writeObject2Json(t));
                return unknown(bookingReq, "供应商响应无法解析，结果不确定，请查单确证");
            }
            if (bookingRespDTO.getOutcome() == null) {
                // 实现方漏填三态即视为不确定，避免默认值悄悄退化成某一态
                log.error("BookingSyncService 实现未填 outcome，按 UNKNOWN 处理, orderId={}", bookingReq.getOrderId());
                bookingRespDTO.setOutcome(BookingOutcome.UNKNOWN);
            }
            if (bookingRespDTO.getOrderId() == null) {
                bookingRespDTO.setOrderId(bookingReq.getOrderId());
            }
            return bookingRespDTO;
        } catch (Exception e) {
            // 异常同样不足以断定未下单：连接在请求发出后中断，与请求根本没发出，在本地无从区分
            log.error("BookingSyncService 异常，回报 UNKNOWN, orderId={}", bookingReq.getOrderId(), e);
            return unknown(bookingReq, "下单过程异常，结果不确定，请查单确证：" + e.getClass().getSimpleName());
        }
    }

    private BookingRespDTO unknown(BookingReq bookingReq, String desc) {
        return BookingRespDTO.builder()
                .outcome(BookingOutcome.UNKNOWN)
                .orderId(bookingReq.getOrderId())
                .orderDesc(desc)
                .build();
    }

    public abstract T doBooking(BookingReq bookingReq);

    public abstract BookingRespDTO bookingRespConvert(T t);
}
