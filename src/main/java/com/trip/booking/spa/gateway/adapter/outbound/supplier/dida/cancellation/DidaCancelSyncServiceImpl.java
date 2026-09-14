package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.DidaBookingClassifier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.DidaBookingClassifier.CancelStepClassification;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation.client.BookingCancelAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation.client.BookingCancelConfirmAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order.client.BookingSearchAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingDetails;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRequestHeader;
import com.trip.booking.spa.gateway.application.cancellation.AbstractCancelSyncSupportService;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.gateway.domain.shared.Money;
import com.trip.booking.spa.gateway.domain.supplier.FailureKind;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 道旅取消。bean 名 {@code didaCancelSyncService} 供能力注册表发现。
 *
 * <p>道旅的取消是两步（官方 booking-cancel）：预取消 HotelBookingCancel 拿罚金与 10 分钟有效的
 * 确认号，确认取消 HotelBookingCancelConfirm 才真取消。罚金<b>只在预取消响应里给</b>
 * （确认取消成功是空对象），故罚金取预取消的 {@code Amount+Currency}，来源申报 FIELD。
 *
 * <p><b>确认取消之后必须再查单看 Status 是否为 3</b>——官方 booking-search：「Status=3 …这是该状态的
 * 唯一有效确认方式」；且 cursor 生产 2026-09-10 单 18667668003 的确认取消 5 次全部读超时（30 秒），
 * 随后预取消回 3018「已取消」、查单 Status=3——超时的那次已经生效。所以确认这一步无响应或表外码
 * 一律 UNKNOWN 引导查单，绝不能报失败；反过来，只有查单亲眼看到 3 才报 SUCCESS。
 *
 * <p>坐标是我方单号：缺供应商单号时先按 ClientReference 反查取回。取消刻意不设 booking-enabled 闸
 * （已存在的真单必须永远可撤，艺龙同理）。
 */
@Slf4j
@Service("didaCancelSyncService")
public class DidaCancelSyncServiceImpl extends AbstractCancelSyncSupportService {

    /** 确认取消的 Description（「客户备注，可以填取消原因之类的」）；渠道单无更细的原因来源 */
    private static final String CANCEL_DESCRIPTION = "行程变更";

    @Resource
    private DidaProperties properties;

    @Override
    protected CancelResult doCancel(CancelCommand command) {
        if (!properties.isConfigured()) {
            log.error("道旅取消：凭证未配置,orderId={}", command.orderId());
            return CancelResult.failed(command.orderId(), null, "credentials_missing",
                            "道旅凭证未配置，供应商侧未发生任何动作；修复配置前重试无效")
                    .withFailureKind(FailureKind.AUTH_CONFIG);
        }
        String bookingId = StringUtils.trimToNull(command.supplierOrderId());
        if (bookingId == null) {
            Lookup lookup = lookupBookingId(command);
            if (lookup.result != null) {
                return lookup.result;
            }
            bookingId = lookup.bookingId;
        }

        // 第一步：预取消——签发确认号并报罚金，不改订单状态
        ResponseResult<DidaBookingCancelResponse> preResult = cancelAccess()
                .access(cancelRequest(bookingId), CallPurpose.ORDER);
        DidaBookingCancelResponse pre = preResult == null ? null : preResult.getData();
        CancelStepClassification preClass = DidaBookingClassifier.classifyPreCancel(pre);
        log.info("道旅取消：预取消分类,orderId={},sOrderId={},classification={},code={},confirmId={},amount={}{}",
                command.orderId(), bookingId, preClass, pre == null ? null : pre.errorCode(),
                pre == null ? null : pre.confirmId(), pre == null ? null : pre.amount(), pre == null ? null : pre.currency());
        switch (preClass) {
            case ALREADY_CANCELED:
                // 目标状态已达成（幂等）。罚金无从得知：预取消没签出金额
                return CancelResult.success(command.orderId(), bookingId, CancelPenalty.unknown(),
                        "供应商确认订单已处于取消状态");
            case AUTH_CONFIG:
                return CancelResult.failed(command.orderId(), bookingId, pre.errorCode(),
                                "我方凭据/配置被道旅拒绝，取消未发生；修复配置前重试无效：" + pre.errorMessage())
                        .withFailureKind(FailureKind.AUTH_CONFIG);
            case DETERMINISTIC_FAILURE:
                return CancelResult.failed(command.orderId(), bookingId, pre.errorCode(),
                        "供应商拒绝取消（" + pre.errorCode() + "）：" + pre.errorMessage());
            case INDETERMINATE:
                // 预取消本身不取消订单，但没拿到响应就无从断言什么都没发生
                return CancelResult.unknown(command.orderId(), bookingId, null,
                        "预取消未取得结果，取消未确认，请查单确证后再重试");
            case ACCEPTED:
            default:
                break;
        }
        String confirmId = StringUtils.trimToNull(pre.confirmId());
        if (confirmId == null) {
            log.error("道旅取消：预取消成功却未返回确认号,orderId={},sOrderId={}", command.orderId(), bookingId);
            return CancelResult.failed(command.orderId(), bookingId, "cancel_confirm_id_missing",
                    "预取消未返回取消确认号，取消未发出，可稍后重试");
        }
        CancelPenalty penalty = penaltyOf(pre, command.orderId());

        // 第二步：确认取消——真正改状态的那一步
        ResponseResult<DidaBookingCancelConfirmResponse> confirmResult = cancelConfirmAccess()
                .access(confirmRequest(bookingId, confirmId), CallPurpose.ORDER);
        DidaBookingCancelConfirmResponse confirm = confirmResult == null ? null : confirmResult.getData();
        CancelStepClassification confirmClass = DidaBookingClassifier.classifyCancelConfirm(confirm);
        log.info("道旅取消：确认取消分类,orderId={},sOrderId={},classification={},code={}",
                command.orderId(), bookingId, confirmClass, confirm == null ? null : confirm.errorCode());
        switch (confirmClass) {
            case ACCEPTED:
                return verifyCanceled(command, bookingId, penalty);
            case ALREADY_CANCELED:
                // 预取消到确认之间被别处取消了：目标状态已达成，但那笔取消按什么罚不是我们签出的那份
                return CancelResult.success(command.orderId(), bookingId, CancelPenalty.unknown(),
                        "供应商确认订单已处于取消状态");
            case AUTH_CONFIG:
                return CancelResult.failed(command.orderId(), bookingId, confirm.errorCode(),
                                "我方凭据/配置被道旅拒绝，取消未发生；修复配置前重试无效：" + confirm.errorMessage())
                        .withFailureKind(FailureKind.AUTH_CONFIG);
            case DETERMINISTIC_FAILURE:
                return CancelResult.failed(command.orderId(), bookingId, confirm.errorCode(),
                        "供应商拒绝确认取消（" + confirm.errorCode() + "）：" + confirm.errorMessage());
            case INDETERMINATE:
            default:
                return CancelResult.unknown(command.orderId(), bookingId, confirm == null ? null : confirm.errorCode(),
                        "确认取消未取得确定结果，可能已生效（道旅超时的确认取消实证会生效），请查单确证");
        }
    }

    /**
     * 确认取消已被受理，再查单确证 Status=3——官方：这是「已取消」的唯一有效确认方式。
     * 看到 3 才 SUCCESS；看到别的状态或查不到，取消可能仍在处理，回 UNKNOWN 引导上游稍后查单。
     */
    private CancelResult verifyCanceled(CancelCommand command, String bookingId, CancelPenalty penalty) {
        DidaBookingSearchResponse search = searchQuietly(BookingSearchAccess.byBookingId(properties, bookingId), command.orderId());
        DidaBookingDetails d = search == null || !search.isSucc() ? null : search.single();
        if (d == null) {
            log.warn("道旅取消：确认已受理但查单未取得该单,orderId={},sOrderId={},searchSucc={}",
                    command.orderId(), bookingId, search != null && search.isSucc());
            return CancelResult.unknown(command.orderId(), bookingId, null,
                    "取消已提交，但未能查单确证订单状态，请稍后查单");
        }
        if (d.getStatus() != null && d.getStatus() == DidaBookingDetails.STATUS_CANCELED) {
            log.info("道旅取消：查单确证 Status=3,orderId={},sOrderId={},penaltySource={}",
                    command.orderId(), bookingId, penalty.source());
            return CancelResult.success(command.orderId(), bookingId, penalty, "取消成功（查单确证 Status=3）");
        }
        log.warn("道旅取消：确认已受理但查单状态尚非已取消,orderId={},sOrderId={},status={}",
                command.orderId(), bookingId, d.getStatus());
        return CancelResult.unknown(command.orderId(), bookingId, "status:" + d.getStatus(),
                "取消已提交，供应商订单状态尚为 " + d.getStatus() + "（非 3），请稍后查单确证");
    }

    /** 反查取供应商单号；顺带判掉"已取消/已失败"两种无可取消的终态。返回值二选一：result 或 bookingId */
    private Lookup lookupBookingId(CancelCommand command) {
        DidaBookingSearchResponse search = searchQuietly(
                BookingSearchAccess.byClientReference(properties, command.orderId()), command.orderId());
        if (search == null) {
            return Lookup.of(CancelResult.unknown(command.orderId(), null, null,
                    "反查供应商单号未取得结果，取消未发出，请稍后重试或先查单"));
        }
        if (!search.isSucc()) {
            if (DidaBookingClassifier.isAuthConfig(search.errorCode())) {
                return Lookup.of(CancelResult.failed(command.orderId(), null, search.errorCode(),
                                "我方凭据/配置被道旅拒绝，取消未发生；修复配置前重试无效：" + search.errorMessage())
                        .withFailureKind(FailureKind.AUTH_CONFIG));
            }
            log.warn("道旅取消：反查供应商单号业务错误,orderId={},code={},message={}",
                    command.orderId(), search.errorCode(), search.errorMessage());
            return Lookup.of(CancelResult.unknown(command.orderId(), null, search.errorCode(),
                    "反查供应商单号未取得确定结果（" + search.errorCode() + "），取消未发出，请稍后重试"));
        }
        DidaBookingDetails d = search.single();
        if (d == null || StringUtils.isBlank(d.getBookingId())) {
            // 空列表不是"无单"的证据（建单窗口内查不到），多笔则有歧义；两者都不许当作无可取消
            log.warn("道旅取消：按我方单号未查到唯一订单,orderId={},hits={}", command.orderId(), search.bookings().size());
            return Lookup.of(CancelResult.unknown(command.orderId(), null, null,
                    "按我方单号未查到唯一的供应商订单（命中 " + search.bookings().size() + " 笔），取消未发出，请携带供应商单号重试"));
        }
        log.info("道旅取消：已按我方单号反查到供应商单号,orderId={},sOrderId={},status={}",
                command.orderId(), d.getBookingId(), d.getStatus());
        if (d.getStatus() != null && d.getStatus() == DidaBookingDetails.STATUS_CANCELED) {
            return Lookup.of(CancelResult.success(command.orderId(), d.getBookingId(), CancelPenalty.unknown(),
                    "订单已处于取消状态"));
        }
        if (d.getStatus() != null && d.getStatus() == DidaBookingDetails.STATUS_FAILED) {
            return Lookup.of(CancelResult.failed(command.orderId(), d.getBookingId(), "status:4",
                    "订单在供应商侧为终态 Failed，未成单，无可取消"));
        }
        return Lookup.of(d.getBookingId());
    }

    /**
     * 预取消响应 → 罚金。{@code Amount} 与 {@code Currency} 缺一即无从得知——宁可不知，不可猜。
     * 单测直接喂真实报文。
     */
    static CancelPenalty penaltyOf(DidaBookingCancelResponse pre, String orderId) {
        BigDecimal amount = pre == null ? null : pre.amount();
        String currency = pre == null ? null : StringUtils.trimToNull(pre.currency());
        if (amount == null || currency == null) {
            log.warn("道旅取消：预取消未给全罚金金额/币种，罚金无从得知,orderId={},amount={},currency={}", orderId, amount, currency);
            return CancelPenalty.unknown();
        }
        if (amount.signum() < 0) {
            log.warn("道旅取消：预取消罚金为负，自相矛盾,orderId={},amount={}", orderId, amount);
            return CancelPenalty.unknown();
        }
        return CancelPenalty.fromField(Money.fromYuan(amount, currency));
    }

    private DidaBookingSearchResponse searchQuietly(
            com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchRequest request,
            String orderId) {
        try {
            ResponseResult<DidaBookingSearchResponse> result = searchAccess().access(request, CallPurpose.ORDER);
            return result == null ? null : result.getData();
        } catch (Exception e) {
            log.error("道旅取消：查单异常,orderId={}", orderId, e);
            return null;
        }
    }

    /**
     * 三条通道的出生地。做成可覆写的钩子只为一件事：让编排本身（预取消→确认→查单确证的分支）
     * 能在不打 HTTP 的前提下用真实报文夹具跑通（DidaCancelFlowTest）——此前艺龙/飞猪的取消编排
     * 只能靠首单真单验，而道旅在 SPA 侧尚无流量，真单闭环没有日期。
     */
    protected BookingCancelAccess cancelAccess() {
        return new BookingCancelAccess(properties);
    }

    protected BookingCancelConfirmAccess cancelConfirmAccess() {
        return new BookingCancelConfirmAccess(properties);
    }

    protected BookingSearchAccess searchAccess() {
        return new BookingSearchAccess(properties);
    }

    private DidaBookingCancelRequest cancelRequest(String bookingId) {
        DidaBookingCancelRequest request = new DidaBookingCancelRequest();
        request.setHeader(new DidaRequestHeader(properties.getClientId(), properties.getLicenseKey()));
        request.setBookingId(bookingId);
        return request;
    }

    private DidaBookingCancelConfirmRequest confirmRequest(String bookingId, String confirmId) {
        DidaBookingCancelConfirmRequest request = new DidaBookingCancelConfirmRequest();
        request.setHeader(new DidaRequestHeader(properties.getClientId(), properties.getLicenseKey()));
        request.setBookingId(bookingId);
        request.setConfirmId(confirmId);
        request.setDescription(CANCEL_DESCRIPTION);
        return request;
    }

    /** 反查的两种出口：要么已能下结论（result），要么拿到了供应商单号继续（bookingId） */
    private static final class Lookup {
        final CancelResult result;
        final String bookingId;

        private Lookup(CancelResult result, String bookingId) {
            this.result = result;
            this.bookingId = bookingId;
        }

        static Lookup of(CancelResult result) {
            return new Lookup(result, null);
        }

        static Lookup of(String bookingId) {
            return new Lookup(null, bookingId);
        }
    }
}
