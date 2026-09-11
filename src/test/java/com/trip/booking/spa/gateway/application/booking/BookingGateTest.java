package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.booking.BookingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下单安全护栏由模板统一执行（§3.8）。
 *
 * <p>下单是唯一真扣钱的链路，每家都必须能不发版关掉它。此前这道闸是各家自己写的，
 * 结果三家里只有艺龙真有：Expedia 只在 BFF（独立项目，§0.4）判过，网关这条路一直裸奔；
 * 飞猪连配置项都没有。漏了在编译期与运行期都不报错——只能靠测试钉住。
 */
class BookingGateTest {

    private static BookingCommand command() {
        return BookingCommand.builder()
                .supplierId(10015).supplierHotelId("H-1").supplierProductId("P-1").orderId("O-1")
                .personName("ZHANG SAN").contactName("ZHANG SAN").contactPhone("13800000000")
                .checkIn("2026-10-10").checkOut("2026-10-11")
                .roomNum(1).totalPrice(10000).settlePrice(9000)
                .build();
    }

    /** 关闸后模板不得调用实现——§3.8.3 关闸即停做功 */
    static class StubBooking extends AbstractBookingSyncSupportService {
        boolean allowed;
        boolean doBookingCalled;

        StubBooking(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        protected String bookingGateKey() {
            return "stub.booking-enabled";
        }

        @Override
        protected boolean bookingAllowed() {
            return allowed;
        }

        @Override
        protected BookingResult doBooking(BookingCommand command) {
            doBookingCalled = true;
            return BookingResult.success(command.orderId(), "S-1", null, null);
        }
    }

    @Test
    @DisplayName("关闸：回 FAILED（不是 UNKNOWN）——供应商侧什么都没发生，上游可直接退款")
    void closedGateIsDeterministicFailure() {
        BookingResult resp = new StubBooking(false).booking(command());

        assertEquals(BookingOutcome.FAILED, resp.outcome(),
                "UNKNOWN 会让上游必须去查单，而这里根本没发出请求");
        assertEquals("booking_disabled", resp.supplierErrorCode());
        assertEquals("O-1", resp.orderId());
        assertNull(resp.supplierOrderId(), "没下单就不该有供应商单号");
    }

    @Test
    @DisplayName("关闸即停做功：不许进到实现里去解析凭据、取句柄、组装报文")
    void closedGateShortCircuitsBeforeImplementation() {
        StubBooking flow = new StubBooking(false);

        flow.booking(command());

        assertTrue(!flow.doBookingCalled, "§3.8.3：关闸后该路径不得继续做功");
    }

    @Test
    @DisplayName("开闸：照常下单")
    void openGateProceeds() {
        StubBooking flow = new StubBooking(true);

        BookingResult resp = flow.booking(command());

        assertTrue(flow.doBookingCalled);
        assertEquals(BookingOutcome.SUCCESS, resp.outcome());
    }

    /** 拦截日志必须带闸口标识与业务主键（§3.8.4），键名由实现申报 */
    @Test
    @DisplayName("闸口键名由各家申报，且不得为空")
    void everyImplementationDeclaresItsGateKey() throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = Path.of("src/main/java/com/trip/booking/spa/gateway/adapter/outbound/supplier");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("BookingSyncServiceImpl.java"))
                    .toList()) {
                String src = read(file);
                if (!src.contains("bookingGateKey()") || !src.contains("bookingAllowed()")) {
                    violations.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "每家下单实现必须申报 bookingGateKey/bookingAllowed（闸口由模板执行，键名与取值由各家给）："
                        + violations);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
