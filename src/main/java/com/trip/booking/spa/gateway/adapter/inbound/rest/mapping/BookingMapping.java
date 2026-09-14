package com.trip.booking.spa.gateway.adapter.inbound.rest.mapping;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.domain.booking.BookingResult;

/**
 * 下单能力的对外形状翻译：JSON 契约 ↔ 领域模型，只此一处。
 *
 * <p>①层持有翻译，②③不知道 JSON 长什么样——继取消、查单之后第三个矫正依赖方向的能力面。
 *
 * <p>对外形状的怪癖也收在这一侧：{@code sOrderId} / {@code sConfirmationNumber} 的
 * {@code @JsonProperty} 钉在 {@link BookingRespDTO} 上（Jackson 会把 {@code sOrderId}
 * 压平成 {@code sorderId}，而入站方向大小写不敏感仍认，两个方向不对称）。领域侧用
 * {@code supplierOrderId} / {@code confirmationNumber} 这样的正常名字，不受其累。
 */
public final class BookingMapping {

    private BookingMapping() {
    }

    public static BookingCommand toCommand(BookingReq req) {
        return BookingCommand.builder()
                .supplierId(req.getSupplierId())
                .orderId(req.getOrderId())
                .offerId(req.getOfferId())
                .supplierHotelId(req.getSHotelId())
                .supplierProductId(req.getSProductId())
                .personName(req.getPersonName())
                .contactName(req.getContactName())
                .contactPhone(req.getContactPhone())
                .checkIn(req.getCheckIn())
                .checkOut(req.getCheckOut())
                .roomNum(req.getRoomNum())
                .totalPrice(req.getTotalPrice())
                .settlePrice(req.getSettlePrice())
                .build();
    }

    public static BookingRespDTO toDto(BookingResult result) {
        return BookingRespDTO.builder()
                .outcome(result.outcome())
                .orderId(result.orderId())
                .sOrderId(result.supplierOrderId())
                .sConfirmationNumber(result.confirmationNumber())
                .supplierErrorCode(result.supplierErrorCode())
                .supplierErrorMessage(result.supplierErrorMessage())
                .orderDesc(result.message())
                .build();
    }
}
