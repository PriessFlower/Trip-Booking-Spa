package com.trip.booking.spa.bff.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.bff.service.BffBookingService;
import com.trip.booking.spa.bff.service.BffShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * expdia 验收前端专用 BFF 端点。与 /client/spa/*（core 供应商网关，面向 tg-trip-cursor）
 * 完全独立；本组端点面向浏览器，字段对齐 Expedia Rapid 验收清单。
 */
@Slf4j
@RestController
@RequestMapping("/bff")
public class BffController {

    private final BffShopService shopService;
    private final BffBookingService bookingService;

    public BffController(BffShopService shopService, BffBookingService bookingService) {
        this.shopService = shopService;
        this.bookingService = bookingService;
    }

    @GetMapping("/cities")
    public JsonNode cities() {
        return shopService.listCities();
    }

    /** 搜索框联想：按关键词同时返回匹配的城市与酒店 */
    @GetMapping("/suggest")
    public JsonNode suggest(@RequestParam(name = "q", required = false) String keyword) {
        return shopService.suggest(keyword);
    }

    /**
     * occupancy 可重复下发，每间一个（如 {@code occupancy=2-7,11&occupancy=3}），各间人数可不同；
     * 未提供时回退到 adults/childAges/rooms 扁平参数（老链接兼容）。
     */
    @GetMapping("/hotels/search")
    public JsonNode search(@RequestParam String city,
                           @RequestParam String checkin,
                           @RequestParam String checkout,
                           @RequestParam(defaultValue = "2") int adults,
                           @RequestParam(required = false) String childAges,
                           @RequestParam(defaultValue = "1") int rooms,
                           HttpServletRequest request) {
        return shopService.searchHotels(city, checkin, checkout, occupancies(request),
                adults, parseAges(childAges), rooms);
    }

    @GetMapping("/hotels/{propertyId}")
    public JsonNode detail(@PathVariable String propertyId,
                           @RequestParam String checkin,
                           @RequestParam String checkout,
                           @RequestParam(defaultValue = "2") int adults,
                           @RequestParam(required = false) String childAges,
                           @RequestParam(defaultValue = "1") int rooms,
                           @RequestParam(required = false) String test,
                           HttpServletRequest request) {
        return shopService.hotelDetail(propertyId, checkin, checkout, occupancies(request), adults,
                parseAges(childAges), rooms, test);
    }

    /**
     * 直接取原始重复参数值。不能用 {@code @RequestParam List<String>}——单个
     * {@code occupancy=2-7,11} 会被 Spring 的 StringToCollection 转换按逗号拆成
     * ["2-7", "11"]，把一间 2 大 2 小错当成两间。
     */
    private List<String> occupancies(HttpServletRequest request) {
        String[] values = request.getParameterValues("occupancy");
        if (values == null || values.length == 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    @PostMapping("/price-check")
    public JsonNode priceCheck(@RequestBody Map<String, String> body) {
        String rateToken = body.get("rateToken");
        if (rateToken == null || rateToken.isBlank()) {
            throw new BffException(400, "缺少 rateToken");
        }
        return shopService.priceCheck(rateToken, body.get("test"));
    }

    @PostMapping("/bookings")
    public JsonNode book(@RequestBody Map<String, String> body) {
        String bookToken = body.get("bookToken");
        if (bookToken == null || bookToken.isBlank()) {
            throw new BffException(400, "缺少 bookToken，请先验价");
        }
        return bookingService.book(bookToken,
                body.get("givenName"), body.get("familyName"),
                body.get("email"), body.get("phone"),
                body.get("propertyName"), body.get("test"));
    }

    @GetMapping("/orders")
    public JsonNode orders() {
        return bookingService.listOrders();
    }

    @GetMapping("/orders/{orderId}")
    public JsonNode order(@PathVariable String orderId) {
        return bookingService.getOrder(orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public JsonNode cancel(@PathVariable String orderId,
                           @RequestParam(required = false) String test) {
        return bookingService.cancelOrder(orderId, test);
    }

    /** childAges 形如 "7,11"；空串与缺省均为无儿童 */
    private List<Integer> parseAges(String childAges) {
        List<Integer> ages = new ArrayList<>();
        if (childAges == null || childAges.isBlank()) {
            return ages;
        }
        for (String part : childAges.split(",")) {
            try {
                ages.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException e) {
                throw new BffException(400, "儿童年龄格式错误: " + part);
            }
        }
        return ages;
    }
}
