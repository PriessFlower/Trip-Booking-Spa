package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉死「所点报价是否还在」的判定。
 *
 * <p>这两个判定决定验价是否回报 RATE_DEAD。rate.id 实测稳定（docs/product-identity.md
 * E-1 修正），但报价仍会因卖法下架/未开售而消失——上游缓存列表、旅客几分钟后点进来时，
 * 那份报价可能已经不在。此时必须能确定地说出「这份报价没了，请重新查价」，
 * 而不是含糊地说验不过。
 */
class ExpediaRateLookupTest {

    private final ExpediaPriceServiceImpl service = new ExpediaPriceServiceImpl();

    private static QueryPriceResponse responseWith(String... rateIds) {
        QueryPriceResponse.Rooms room = new QueryPriceResponse.Rooms();
        List<QueryPriceResponse.Rates> rates = new java.util.ArrayList<>();
        for (String rateId : rateIds) {
            QueryPriceResponse.Rates rate = new QueryPriceResponse.Rates();
            rate.setId(rateId);
            rates.add(rate);
        }
        room.setRates(rates);
        QueryPriceResponse.HotelPrice hotelPrice = new QueryPriceResponse.HotelPrice();
        hotelPrice.setRooms(List.of(room));
        QueryPriceResponse resp = new QueryPriceResponse();
        resp.setHotelPrices(List.of(hotelPrice));
        return resp;
    }

    private static QueryPriceResponse.Rates rateWithBedGroups(String... bedGroupIds) {
        QueryPriceResponse.Rates rate = new QueryPriceResponse.Rates();
        Map<String, QueryPriceResponse.Bed_groups> groups = new HashMap<>();
        for (String id : bedGroupIds) {
            QueryPriceResponse.Bed_groups group = new QueryPriceResponse.Bed_groups();
            group.setId(id);
            groups.put(id, group);
        }
        rate.setBed_groups(groups);
        return rate;
    }

    // ── 报价是否还在 ────────────────────────────────────────

    @Test
    void findsClickedRate() {
        QueryPriceResponse.Rates found = service.findRate(responseWith("111", "222"), "222");

        assertNotNull(found);
        assertEquals("222", found.getId());
    }

    /** 报价已换代或产品已下架：必须能确定地判空，这是 RATE_DEAD 的依据 */
    @Test
    void missingRateYieldsNull() {
        assertNull(service.findRate(responseWith("111", "222"), "333"),
                "判不出「这份报价没了」，就只能含糊回报验不过，上游会当成满房");
    }

    /** 酒店有报价但房型列表为空，不得抛异常 */
    @Test
    void nullRoomsYieldsNull() {
        QueryPriceResponse.HotelPrice hotelPrice = new QueryPriceResponse.HotelPrice();
        QueryPriceResponse resp = new QueryPriceResponse();
        resp.setHotelPrices(List.of(hotelPrice));

        assertNull(service.findRate(resp, "111"));
    }

    /** 房型下没有报价列表同样不得抛异常 */
    @Test
    void nullRatesYieldsNull() {
        QueryPriceResponse.Rooms room = new QueryPriceResponse.Rooms();
        QueryPriceResponse.HotelPrice hotelPrice = new QueryPriceResponse.HotelPrice();
        hotelPrice.setRooms(List.of(room));
        QueryPriceResponse resp = new QueryPriceResponse();
        resp.setHotelPrices(List.of(hotelPrice));

        assertNull(service.findRate(resp, "111"));
    }

    // ── 床型是否还可选 ──────────────────────────────────────

    @Test
    void picksRequestedBedGroup() {
        QueryPriceResponse.Bed_groups picked = service.pickBedGroup(rateWithBedGroups("37431", "38471"), "38471");

        assertNotNull(picked);
        assertEquals("38471", picked.getId());
    }

    /** 上游未指定床型时取任意一个，不得判空——否则可订的产品会被误报为不可选 */
    @Test
    void picksAnyBedGroupWhenUnspecified() {
        assertNotNull(service.pickBedGroup(rateWithBedGroups("37431"), null));
        assertNotNull(service.pickBedGroup(rateWithBedGroups("37431"), ""));
    }

    /** 指定的床型已不存在：判空，据此回报 RATE_DEAD */
    @Test
    void missingRequestedBedGroupYieldsNull() {
        assertNull(service.pickBedGroup(rateWithBedGroups("37431"), "99999"));
    }

    @Test
    void nullBedGroupsYieldsNull() {
        assertNull(service.pickBedGroup(new QueryPriceResponse.Rates(), "37431"));
        assertNull(service.pickBedGroup(new QueryPriceResponse.Rates(), null));
    }
}
