package com.trip.booking.spa.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trip.booking.spa.bff.client.RapidGateway;
import com.trip.booking.spa.bff.client.RapidReply;
import com.trip.booking.spa.bff.config.BffProperties;
import com.trip.booking.spa.bff.offer.OfferCache;
import com.trip.booking.spa.bff.store.PropertyContentRepo;
import com.trip.booking.spa.bff.web.BffException;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaContractProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物链路：搜索报价 → 详情房价 → 验价。
 *
 * <p>纪律：Rapid 返回的金额与政策字段一律以 JsonNode 原样透传（BP5：逐字显示、
 * 不四舍五入、不换币种）；price_check / book href 只存服务端，前端只见不透明 token。
 */
@Slf4j
@Service
public class BffShopService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 英文原名取自静态摄取的 en-US 行（与 zh-CN 同表不同行），与展示语言无关 */
    private static final String NAME_LANGUAGE_EN = "en-US";

    private final RapidGateway gateway;
    private final PropertyContentRepo contentRepo;
    private final OfferCache offerCache;
    private final BffProperties props;

    /**
     * 合同档案：本层与 core 共用同一份取值，不再各自绑定配置。
     *
     * <p>四项参数构成一条车道，必须整体取自同一套档案。此前本层独立绑定三项、
     * 另把 {@code sales_channel} 写死为 {@code website}，等于在 core 的启动期校验
     * 之外另开一个写入点——档案切到 B2C 时本层会继续发 {@code website}，实测同一
     * 报价因此贵约 18%，且无任何报错。本层直接对旅客展示金额，这一差价会照单呈现。
     *
     * <p>依赖方向为 bff → core，与本层既有的 {@code ExpediaRapidProperties}、
     * {@code ExpediaUtils} 一致；core 不感知本层存在。
     */
    @Resource
    private ExpediaContractProfile contractProfile;

    public BffShopService(RapidGateway gateway, PropertyContentRepo contentRepo,
                             OfferCache offerCache, BffProperties props) {
        this.gateway = gateway;
        this.contentRepo = contentRepo;
        this.offerCache = offerCache;
        this.props = props;
    }

    // ---------- 城市 ----------

    public JsonNode listCities() {
        ArrayNode cities = MAPPER.createArrayNode();
        for (Map<String, Object> row : contentRepo.listCities(props.getLanguage(), 5)) {
            ObjectNode node = cities.addObject();
            node.put("city", String.valueOf(row.get("city")));
            node.put("countryCode", String.valueOf(row.get("countryCode")));
            node.put("propertyCount", ((Number) row.get("propertyCount")).intValue());
        }
        return cities;
    }

    // ---------- 搜索 ----------

    public JsonNode searchHotels(String city, String checkin, String checkout,
                                 List<String> occupancy, int adults, List<Integer> childAges, int rooms) {
        return searchHotels(city, checkin, checkout, occupancy, adults, childAges, rooms, null);
    }

    public JsonNode searchHotels(String city, String checkin, String checkout,
                                 List<String> occupancy, int adults, List<Integer> childAges,
                                 int rooms, String testScenario) {
        List<PropertyContentRepo.PropertySummary> properties =
                contentRepo.searchByCity(city, props.getLanguage(), props.getSearchLimit());
        if (properties.isEmpty()) {
            throw new BffException(404, "该城市暂无已摄取的酒店静态数据: " + city);
        }

        List<String> occupancies = buildOccupancies(occupancy, adults, childAges, rooms);
        List<String> propertyIds = properties.stream().map(p -> p.propertyId).toList();
        Map<String, JsonNode> priced = queryAvailability(
                propertyIds, checkin, checkout, occupancies, 1, "shopping", testScenario);
        Map<String, String> englishNames = contentRepo.findNames(propertyIds, NAME_LANGUAGE_EN);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("city", city);
        result.put("checkin", checkin);
        result.put("checkout", checkout);
        ArrayNode occArr = result.putArray("occupancy");
        occupancies.forEach(occArr::add);
        ArrayNode hotels = result.putArray("hotels");

        for (PropertyContentRepo.PropertySummary property : properties) {
            JsonNode hotelPrice = priced.get(property.propertyId);
            if (hotelPrice == null) {
                continue; // 无报价的酒店不展示（满房或未开放）
            }
            ObjectNode hotel = hotels.addObject();
            fillContentSummary(hotel, property, englishNames.get(property.propertyId));
            JsonNode rate = firstRate(hotelPrice);
            if (rate != null) {
                ObjectNode offer = hotel.putObject("offer");
                JsonNode room = hotelPrice.path("rooms").path(0);
                offer.put("roomName", room.path("room_name").asText(null));
                offer.put("refundable", rate.path("refundable").asBoolean(false));
                offer.put("merchantOfRecord", rate.path("merchant_of_record").asText(null));
                offer.set("cancelPenalties", rate.path("cancel_penalties"));
                offer.set("nonrefundableDateRanges", rate.path("nonrefundable_date_ranges"));
                JsonNode pricing = firstOccupancyPricing(rate);
                if (pricing != null) {
                    // 金额节点原样透传（单间价）
                    offer.set("totals", pricing.path("totals"));
                    offer.set("nightly", pricing.path("nightly"));
                }
                // 列表展示的是订单总价（多间时为各间之和），与详情/结账口径一致
                ObjectNode aggregate = PricingMath.orderAggregate(rate.path("occupancy_pricing"), occupancies);
                if (aggregate != null) {
                    offer.set("orderTotals", aggregate.path("totals"));
                    offer.put("roomCount", aggregate.path("roomCount").asInt());
                }
            }
        }
        result.put("resultCount", hotels.size());
        return result;
    }

    // ---------- 详情 ----------

    public JsonNode hotelDetail(String propertyId, String checkin, String checkout,
                                List<String> occupancy, int adults, List<Integer> childAges, int rooms) {
        return hotelDetail(propertyId, checkin, checkout, occupancy, adults, childAges, rooms, null);
    }

    public JsonNode hotelDetail(String propertyId, String checkin, String checkout,
                                List<String> occupancy, int adults, List<Integer> childAges,
                                int rooms, String testScenario) {
        PropertyContentRepo.PropertySummary property = contentRepo
                .findById(propertyId, props.getLanguage())
                .orElseThrow(() -> new BffException(404, "酒店不存在或未摄取: " + propertyId));

        List<String> occupancies = buildOccupancies(occupancy, adults, childAges, rooms);
        Map<String, JsonNode> priced = queryAvailability(
                List.of(propertyId), checkin, checkout, occupancies, 250, "shopping", testScenario);
        JsonNode hotelPrice = priced.get(propertyId);

        ObjectNode result = MAPPER.createObjectNode();
        fillContentSummary(result, property,
                contentRepo.findNames(List.of(propertyId), NAME_LANGUAGE_EN).get(propertyId));
        fillContentDetail(result, property);
        result.put("checkin", checkin);
        result.put("checkout", checkout);
        ArrayNode occArr = result.putArray("occupancy");
        occupancies.forEach(occArr::add);

        ArrayNode roomsOut = result.putArray("rooms");
        if (hotelPrice != null) {
            for (JsonNode room : hotelPrice.path("rooms")) {
                ObjectNode roomOut = roomsOut.addObject();
                String roomId = room.path("id").asText();
                roomOut.put("roomId", roomId);
                roomOut.put("roomName", room.path("room_name").asText(null));
                fillRoomContent(roomOut, property.raw, roomId);
                ArrayNode ratesOut = roomOut.putArray("rates");
                for (JsonNode rate : room.path("rates")) {
                    ratesOut.add(buildRateNode(property, roomId, rate, checkin, checkout, occupancies));
                }
            }
        }
        result.put("available", roomsOut.size() > 0);
        return result;
    }

    /** 单个房价：透传政策与价格，床型组生成 rateToken（床型选择走各自的 price_check——AP1） */
    private ObjectNode buildRateNode(PropertyContentRepo.PropertySummary property, String roomId,
                                     JsonNode rate, String checkin, String checkout,
                                     List<String> occupancies) {
        ObjectNode out = MAPPER.createObjectNode();
        out.put("rateId", rate.path("id").asText());
        out.put("status", rate.path("status").asText(null));
        out.put("availableRooms", rate.path("available_rooms").asInt(0));
        out.put("refundable", rate.path("refundable").asBoolean(false));
        out.put("merchantOfRecord", rate.path("merchant_of_record").asText(null));
        out.set("saleScenario", rate.path("sale_scenario"));
        out.set("cancelPenalties", rate.path("cancel_penalties"));
        out.set("nonrefundableDateRanges", rate.path("nonrefundable_date_ranges"));
        out.set("promotions", rate.path("promotions"));

        ArrayNode amenities = out.putArray("amenities");
        rate.path("amenities").forEach(a -> amenities.add(a.path("name").asText()));

        JsonNode pricing = firstOccupancyPricing(rate);
        if (pricing != null) {
            // 单间口径：nightly/stay/fees/totals 均为一间房的原始十进制字符串
            out.set("nightly", pricing.path("nightly"));
            out.set("stay", pricing.path("stay"));
            out.set("fees", pricing.path("fees"));
            out.set("totals", pricing.path("totals"));
            // BP5：tax_and_service_fee + property_fee 等税费项的精确合并行
            JsonNode taxesAndFees = PricingMath.taxesAndFees(pricing);
            if (taxesAndFees != null) {
                out.set("taxesAndFees", taxesAndFees);
            }
        }
        // 订单口径：逐间累加（各间人数可不同，价格随之不同）
        ObjectNode aggregate = PricingMath.orderAggregate(rate.path("occupancy_pricing"), occupancies);
        if (aggregate != null) {
            out.set("order", aggregate);
        }

        String paymentOptionsHref = rate.path("links").path("payment_options").path("href").asText(null);
        boolean refundable = rate.path("refundable").asBoolean(false);
        String merchantOfRecord = rate.path("merchant_of_record").asText(null);

        ArrayNode bedGroups = out.putArray("bedGroups");
        rate.path("bed_groups").forEach(bedGroup -> {
            ObjectNode bg = bedGroups.addObject();
            bg.put("id", bedGroup.path("id").asText());
            bg.put("description", bedGroup.path("description").asText(null));
            bg.set("configuration", bedGroup.path("configuration"));
            String priceCheckHref = bedGroup.path("links").path("price_check").path("href").asText(null);
            if (priceCheckHref != null) {
                OfferCache.RateOffer offer = new OfferCache.RateOffer();
                offer.priceCheckHref = priceCheckHref;
                offer.paymentOptionsHref = paymentOptionsHref;
                offer.propertyId = property.propertyId;
                offer.roomId = roomId;
                offer.rateId = rate.path("id").asText();
                offer.bedGroupId = bedGroup.path("id").asText();
                offer.bedDescription = bedGroup.path("description").asText(null);
                offer.checkin = checkin;
                offer.checkout = checkout;
                offer.occupancies = occupancies;
                offer.refundable = refundable;
                offer.merchantOfRecord = merchantOfRecord;
                if (!rate.path("cancel_penalties").isMissingNode()) {
                    offer.cancelPenaltiesJson = rate.path("cancel_penalties").toString();
                }
                if (!rate.path("nonrefundable_date_ranges").isMissingNode()) {
                    offer.nonrefundableDateRangesJson = rate.path("nonrefundable_date_ranges").toString();
                }
                bg.put("rateToken", offerCache.putRate(offer));
            }
        });
        return out;
    }

    // ---------- 验价 ----------

    public JsonNode priceCheck(String rateToken) {
        return priceCheck(rateToken, null);
    }

    public JsonNode priceCheck(String rateToken, String testScenario) {
        OfferCache.RateOffer offer = offerCache.getRate(rateToken);
        if (offer == null) {
            throw new BffException(410, "报价已过期，请重新查询房价");
        }
        StringBuilder checkPath = new StringBuilder(offer.priceCheckHref);
        appendContractTerms(checkPath); // href 自带 ?token=，续接 & 安全
        RapidReply reply = gateway.get("price-check", checkPath.toString(), testScenario);
        if (!reply.is2xx() || reply.getBody() == null) {
            if (reply.getStatus() == 410 || reply.getStatus() == 404) {
                throw new BffException(410, "该房价已失效（sold out / rate dead），请重新选择");
            }
            throw new BffException(502, "验价未能确认，请稍后重试");
        }
        JsonNode body = reply.getBody();

        ObjectNode result = MAPPER.createObjectNode();
        result.put("status", body.path("status").asText(null));
        // occupancy_pricing 整节点透传：nightly/stay/fees/totals 原始十进制字符串
        result.set("occupancyPricing", body.path("occupancy_pricing"));
        JsonNode checkPricing = body.path("occupancy_pricing");
        if (checkPricing.isObject() && checkPricing.fields().hasNext()) {
            // 单间税费（保留原字段，前端逐间明细用）
            JsonNode taxesAndFees = PricingMath.taxesAndFees(checkPricing.fields().next().getValue());
            if (taxesAndFees != null) {
                result.set("taxesAndFees", taxesAndFees);
            }
        }
        // 订单口径：逐间累加后的总额与总税费（多间时与单间值不同）
        ObjectNode aggregate = PricingMath.orderAggregate(checkPricing, offer.occupancies);
        if (aggregate != null) {
            result.set("order", aggregate);
        }

        ObjectNode context = result.putObject("offerContext");
        context.put("propertyId", offer.propertyId);
        context.put("roomId", offer.roomId);
        context.put("rateId", offer.rateId);
        context.put("bedGroupId", offer.bedGroupId);
        context.put("bedDescription", offer.bedDescription);
        context.put("checkin", offer.checkin);
        context.put("checkout", offer.checkout);
        context.put("refundable", offer.refundable);
        context.put("merchantOfRecord", offer.merchantOfRecord);
        ArrayNode occArr = context.putArray("occupancy");
        offer.occupancies.forEach(occArr::add);

        // BP8/BP10：收款方与付款处理国家来自 Payment Options API
        JsonNode paymentOptions = null;
        if (offer.paymentOptionsHref != null) {
            RapidReply payment = gateway.get("payment-options", offer.paymentOptionsHref);
            if (payment.is2xx() && payment.getBody() != null) {
                paymentOptions = payment.getBody();
                result.set("paymentOptions", paymentOptions);
            }
        }

        String bookHref = body.path("links").path("book").path("href").asText(null);
        if (bookHref != null && "available".equals(body.path("status").asText())) {
            String bookToken = offerCache.putBook(bookHref, offer);
            OfferCache.BookOffer bookOffer = offerCache.getBook(bookToken);
            // 快照随订单落库：确认页/凭证的金额与政策必须与 API 响应逐字一致（CP1/ER6）
            bookOffer.pricingJson = body.path("occupancy_pricing").toString();
            bookOffer.paymentOptionsJson = paymentOptions == null ? null : paymentOptions.toString();
            result.put("bookToken", bookToken);
            result.put("orderId", bookOffer.orderId);
        }
        return result;
    }

    // ---------- 公共 ----------

    /**
     * 每间客房一个 occupancy 串（成人数-儿童年龄列表），Shopping/Price Check/Booking 三段一致（SP1）。
     *
     * <p>优先使用前端逐间下发的 {@code occupancy} 参数（各间人数可不同）；未提供时回退到
     * 旧的 adults/childAges/rooms 扁平参数并复制 N 份，保证老链接仍可用。
     */
    private List<String> buildOccupancies(List<String> requested, int adults,
                                          List<Integer> childAges, int rooms) {
        if (requested != null && !requested.isEmpty()) {
            List<String> normalized = new ArrayList<>();
            for (String occupancy : requested) {
                normalized.add(normalizeOccupancy(occupancy));
            }
            if (normalized.size() > 8) {
                throw new BffException(400, "标准 API 单次预订不超过 8 间客房（TR6）");
            }
            return normalized;
        }
        if (rooms < 1 || rooms > 8) {
            throw new BffException(400, "标准 API 单次预订不超过 8 间客房（TR6）");
        }
        String occupancy = normalizeOccupancy(adults + (childAges == null || childAges.isEmpty()
                ? ""
                : "-" + String.join(",", childAges.stream().map(String::valueOf).toList())));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < rooms; i++) {
            result.add(occupancy);
        }
        return result;
    }

    /** 校验并规整单间 occupancy 串，形如 {@code 2} 或 {@code 2-7,11} */
    private String normalizeOccupancy(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BffException(400, "客房人数不能为空");
        }
        String[] parts = raw.trim().split("-", 2);
        int adults;
        try {
            adults = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            throw new BffException(400, "客房人数格式错误: " + raw);
        }
        if (adults < 1 || adults > 8) {
            throw new BffException(400, "每间成人数必须在 1-8 之间");
        }
        if (parts.length == 1 || parts[1].isBlank()) {
            return String.valueOf(adults);
        }
        String[] ages = parts[1].split(",");
        if (ages.length > 6) {
            throw new BffException(400, "每间最多支持 6 名儿童");
        }
        List<String> normalizedAges = new ArrayList<>();
        for (String age : ages) {
            int value;
            try {
                value = Integer.parseInt(age.trim());
            } catch (NumberFormatException e) {
                throw new BffException(400, "儿童年龄格式错误: " + age);
            }
            if (value < 0 || value > 17) {
                throw new BffException(400, "儿童年龄必须在 0-17 之间");
            }
            normalizedAges.add(String.valueOf(value));
        }
        return adults + "-" + String.join(",", normalizedAges);
    }

    /** availability 查询，返回 property_id → hotelPrice 节点 */
    private Map<String, JsonNode> queryAvailability(List<String> propertyIds, String checkin,
                                                    String checkout, List<String> occupancies,
                                                    int ratePlanCount, String evidenceTag,
                                                    String testScenario) {
        StringBuilder query = new StringBuilder("/v3/properties/availability?");
        query.append("checkin=").append(encode(checkin));
        query.append("&checkout=").append(encode(checkout));
        query.append("&currency=").append(encode(props.getCurrency()));
        query.append("&language=").append(encode(props.getLanguage()));
        query.append("&country_code=").append(encode(props.getCountryCode()));
        query.append("&sales_channel=").append(encode(contractProfile.getSalesChannel()));
        query.append("&sales_environment=hotel_only");
        query.append("&rate_plan_count=").append(ratePlanCount);
        appendContractTerms(query);
        for (String propertyId : propertyIds) {
            query.append("&property_id=").append(encode(propertyId));
        }
        for (String occupancy : occupancies) {
            query.append("&occupancy=").append(encode(occupancy));
        }

        RapidReply reply = gateway.get(evidenceTag, query.toString(), testScenario);
        Map<String, JsonNode> result = new HashMap<>();
        if (reply.is2xx() && reply.getBody() != null && reply.getBody().isArray()) {
            for (JsonNode hotelPrice : reply.getBody()) {
                result.put(hotelPrice.path("property_id").asText(), hotelPrice);
            }
            // Rapid 测试场景（Test 头）返回固定 mock property_id（如 185418），与请求的
            // 测试酒店 11775754 不一致；单酒店查询时按请求 ID 归位，保证链路可走通
            if (testScenario != null && propertyIds.size() == 1 && reply.getBody().size() == 1
                    && !result.containsKey(propertyIds.get(0))) {
                result.put(propertyIds.get(0), reply.getBody().get(0));
            }
        } else if (reply.getStatus() == 404) {
            // 全部无房：Rapid 对无结果返回 404，按空结果处理而非报错
            return result;
        } else if (!reply.is2xx()) {
            log.warn("availability 查询失败 status={} body={}", reply.getStatus(),
                    reply.getRaw() != null && reply.getRaw().length() > 500
                            ? reply.getRaw().substring(0, 500) : reply.getRaw());
            throw new BffException(502, "房价查询未能完成，请稍后重试");
        }
        return result;
    }

    /**
     * 补齐合同条款三项。取值来自 {@link ExpediaContractProfile}，其启动期校验已保证
     * 四项同属一套档案且均非空，故此处无须再判空——判空只会把「配置缺失」悄悄降级成
     * 「少发几个参数」。
     *
     * <p>此处有意不含 {@code sales_channel}，与 core 的 {@code appendTo} 一致：
     * 验价链路自接入起就不带该参数且实测通行。
     */
    private void appendContractTerms(StringBuilder query) {
        query.append("&billing_terms=").append(encode(contractProfile.getBillingTerms()));
        query.append("&payment_terms=").append(encode(contractProfile.getPaymentTerms()));
        query.append("&partner_point_of_sale=").append(encode(contractProfile.getPartnerPointOfSale()));
    }

    private JsonNode firstRate(JsonNode hotelPrice) {
        JsonNode rate = hotelPrice.path("rooms").path(0).path("rates").path(0);
        return rate.isMissingNode() ? null : rate;
    }

    /** occupancy_pricing 的第一个值（单间；多间同构时各间同价） */
    private JsonNode firstOccupancyPricing(JsonNode rate) {
        JsonNode pricing = rate.path("occupancy_pricing");
        if (pricing.isObject() && pricing.fields().hasNext()) {
            return pricing.fields().next().getValue();
        }
        return null;
    }

    /** 静态内容摘要：名称、星级、坐标、地址、点评、主图、亮点描述 */
    private void fillContentSummary(ObjectNode out, PropertyContentRepo.PropertySummary property) {
        fillContentSummary(out, property, null);
    }

    /**
     * @param nameEn 英文原名（en-US 行），与中文名并列展示；同名或缺失时不下发
     */
    private void fillContentSummary(ObjectNode out, PropertyContentRepo.PropertySummary property,
                                    String nameEn) {
        out.put("propertyId", property.propertyId);
        out.put("name", property.name);
        if (nameEn != null && !nameEn.isBlank() && !nameEn.equals(property.name)) {
            out.put("nameEn", nameEn);
        }
        out.put("city", property.city);
        out.put("countryCode", property.countryCode);
        if (property.starRating != null) {
            out.put("starRating", property.starRating);
        }
        JsonNode raw = property.raw;
        if (raw == null) {
            return;
        }
        out.put("address", raw.path("address").path("line_1").asText(null));
        JsonNode guest = raw.path("ratings").path("guest");
        if (!guest.isMissingNode()) {
            out.put("guestRating", guest.path("overall").asText(null));
            out.put("reviewCount", guest.path("count").asText(null));
        }
        out.put("heroImage", pickImage(raw, true));
        out.put("headline", raw.path("descriptions").path("headline").asText(null));
    }

    /** 静态内容全量：AP3/BP2 所需的入住退房说明、费用、政策、设施、图片 */
    private void fillContentDetail(ObjectNode out, PropertyContentRepo.PropertySummary property) {
        JsonNode raw = property.raw;
        if (raw == null) {
            return;
        }
        // checkin 含 begin_time/end_time/instructions/special_instructions/min_age
        out.set("checkinPolicy", raw.path("checkin"));
        out.set("checkoutPolicy", raw.path("checkout"));
        out.set("fees", raw.path("fees"));
        out.set("policies", raw.path("policies"));
        out.set("descriptions", raw.path("descriptions"));
        JsonNode location = raw.path("location").path("coordinates");
        if (!location.isMissingNode()) {
            out.set("coordinates", location);
        }
        ArrayNode amenities = out.putArray("amenityNames");
        int count = 0;
        for (JsonNode amenity : raw.path("amenities")) {
            if (count++ >= 16) {
                break;
            }
            amenities.add(amenity.path("name").asText());
        }
        ArrayNode images = out.putArray("images");
        int imageCount = 0;
        for (JsonNode image : raw.path("images")) {
            if (imageCount >= 8) {
                break;
            }
            String href = imageHref(image);
            if (href != null) {
                ObjectNode img = images.addObject();
                img.put("href", href);
                img.put("caption", image.path("caption").asText(null));
                imageCount++;
            }
        }
    }

    /** 房型静态内容：面积、描述、图片、最大入住 */
    private void fillRoomContent(ObjectNode roomOut, JsonNode raw, String roomId) {
        if (raw == null) {
            return;
        }
        JsonNode room = raw.path("rooms").path(roomId);
        if (room.isMissingNode()) {
            return;
        }
        roomOut.put("description", room.path("descriptions").path("overview").asText(null));
        JsonNode area = room.path("area").path("square_meters");
        if (!area.isMissingNode()) {
            roomOut.put("squareMeters", area.asText());
        }
        JsonNode maxAllowed = room.path("occupancy").path("max_allowed");
        if (!maxAllowed.isMissingNode()) {
            roomOut.set("maxOccupancy", maxAllowed);
        }
        for (JsonNode image : room.path("images")) {
            String href = imageHref(image);
            if (href != null) {
                roomOut.put("image", href);
                break;
            }
        }
    }

    private String pickImage(JsonNode raw, boolean preferHero) {
        String fallback = null;
        for (JsonNode image : raw.path("images")) {
            String href = imageHref(image);
            if (href == null) {
                continue;
            }
            if (!preferHero || image.path("hero_image").asBoolean(false)) {
                return href;
            }
            if (fallback == null) {
                fallback = href;
            }
        }
        return fallback;
    }

    private String imageHref(JsonNode image) {
        JsonNode links = image.path("links");
        for (String size : new String[]{"1000px", "350px", "200px", "70px"}) {
            String href = links.path(size).path("href").asText(null);
            if (href != null) {
                return href;
            }
        }
        return null;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
