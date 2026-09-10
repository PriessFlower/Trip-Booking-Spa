package com.trip.booking.spa.b2b.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trip.booking.spa.b2b.auth.B2bAuthFilter;
import com.trip.booking.spa.b2b.auth.PasswordHash;
import com.trip.booking.spa.b2b.auth.SessionStore;
import com.trip.booking.spa.b2b.config.B2bProperties;
import com.trip.booking.spa.b2b.service.B2bBookingService;
import com.trip.booking.spa.b2b.store.AgentStore;
import com.trip.booking.spa.b2b.store.AgreementStore;
import com.trip.booking.spa.bff.service.BffShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * B2B 代理商后台端点。与 {@code /bff/**}（B2C 演示站，公网匿名）完全独立：
 * 同样的选购与下单能力，但一律在登录闸门之后。
 *
 * <p>不复用 {@code /bff/**} 而另开一组同名路径，是因为封闭用户环境要挡住的是<b>接口</b>
 * 而不是页面——前端加个登录页，绕过页面直接打 {@code /bff/hotels/search} 一样能取到价。
 */
@Slf4j
@RestController
@RequestMapping("/b2b")
public class B2bController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BffShopService shopService;
    private final B2bBookingService bookingService;
    private final AgentStore agentStore;
    private final AgreementStore agreementStore;
    private final SessionStore sessionStore;
    private final B2bProperties props;

    public B2bController(BffShopService shopService, B2bBookingService bookingService,
                         AgentStore agentStore, AgreementStore agreementStore,
                         SessionStore sessionStore, B2bProperties props) {
        this.shopService = shopService;
        this.bookingService = bookingService;
        this.agentStore = agentStore;
        this.agreementStore = agreementStore;
        this.sessionStore = sessionStore;
        this.props = props;
    }

    // ---------- 会话 ----------

    /**
     * 登录。免闸门放行（见 {@code B2bAuthFilter#isOpenPath}）。
     *
     * <p>账号不存在、口令不对、账号停用一律回同一句话与同一状态码：区分开等于告诉
     * 试探者哪个登录名真实存在。
     */
    @PostMapping("/session")
    public ResponseEntity<JsonNode> login(@RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        String loginName = body.get("loginName");
        String password = body.get("password");
        if (loginName == null || loginName.isBlank() || password == null || password.isEmpty()) {
            throw new B2bException(400, "请输入登录名与口令");
        }
        AgentStore.AgentRow agent = agentStore.findByLoginName(loginName)
                .filter(row -> row.enabled)
                .filter(row -> PasswordHash.matches(password, row.passwordHash))
                .orElseThrow(() -> new B2bException(401, "登录名或口令有误"));

        String token = sessionStore.issue(agent.agentId);
        if (token == null) {
            throw new B2bException(503, "登录暂不可用，请稍后重试");
        }
        log.info("B2B 登录成功 agentId={} ip={}", agent.agentId, clientIp(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, props.getSession().getTtlSeconds()))
                .body(agentView(agent));
    }

    @DeleteMapping("/session")
    public ResponseEntity<JsonNode> logout(HttpServletRequest request) {
        sessionStore.drop(cookieToken(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", 0))
                .body(MAPPER.createObjectNode().put("loggedOut", true));
    }

    /** 我是谁。前端进站先打这个，401 就跳登录页 */
    @GetMapping("/session")
    public JsonNode currentAgent(HttpServletRequest request) {
        String agentId = agentId(request);
        AgentStore.AgentRow agent = agentStore.findById(agentId)
                .orElseThrow(() -> new B2bException(401, "账号不存在，请重新登录"));
        return agentView(agent);
    }

    // ---------- 站级信息与协议 ----------

    /**
     * 站级信息：协议、Expedia 集团条款、客服入口。免闸门放行——登录页上就要能点开协议
     * 与客服，验收要求的「预订前阅读」与「客服清楚可见」都不以登录为前提。
     *
     * <p>都做成后端下发而非前端写死：这三项是运营口径，改一个电话不该动前端发版。
     */
    @GetMapping("/site")
    public JsonNode site() {
        ObjectNode node = MAPPER.createObjectNode();
        ObjectNode agreement = node.putObject("agreement");
        agreement.put("version", props.getAgreement().getVersion());
        agreement.put("url", props.getAgreement().getUrl());
        node.put("expediaTermsUrl", props.getAgreement().getExpediaTermsUrl());
        ObjectNode support = node.putObject("support");
        support.put("email", props.getSupport().getEmail());
        support.put("phone", props.getSupport().getPhone());
        support.put("onlineUrl", props.getSupport().getOnlineUrl());
        return node;
    }

    /** 签署。留痕记 IP 与 UA，作为 Site Review 证据 */
    @PostMapping("/agreement/accept")
    public JsonNode acceptAgreement(HttpServletRequest request) {
        String agentId = agentId(request);
        String version = props.getAgreement().getVersion();
        agreementStore.accept(agentId, version, clientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT));
        log.info("B2B 协议签署 agentId={} version={} ip={}", agentId, version, clientIp(request));
        ObjectNode node = MAPPER.createObjectNode();
        node.put("version", version);
        node.put("accepted", true);
        return node;
    }

    // ---------- 选购 ----------

    @GetMapping("/cities")
    public JsonNode cities() {
        return shopService.listCities();
    }

    @GetMapping("/suggest")
    public JsonNode suggest(@RequestParam(name = "q", required = false) String keyword) {
        return shopService.suggest(keyword);
    }

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

    @PostMapping("/price-check")
    public JsonNode priceCheck(@RequestBody Map<String, String> body) {
        String rateToken = body.get("rateToken");
        if (rateToken == null || rateToken.isBlank()) {
            throw new B2bException(400, "缺少 rateToken");
        }
        return shopService.priceCheck(rateToken, body.get("test"));
    }

    // ---------- 下单与订单 ----------

    /**
     * 下单。{@code rateAmenities} 是房价包含项（早餐、Wi-Fi 等）的原样回传：
     * 该字段只在查价响应里有，验价与下单响应都不带，故由前端带回、本层落档。
     * 因此本端点收 JSON 树而非 {@code Map<String,String>}——它含一个字符串数组。
     */
    @PostMapping("/bookings")
    public JsonNode book(@RequestBody JsonNode body, HttpServletRequest request) {
        String bookToken = text(body, "bookToken");
        if (bookToken == null || bookToken.isBlank()) {
            throw new B2bException(400, "缺少 bookToken，请先验价");
        }
        return bookingService.book(agentId(request), bookToken,
                text(body, "givenName"), text(body, "familyName"),
                text(body, "email"), text(body, "phone"),
                text(body, "propertyName"), text(body, "test"),
                body.path("rateAmenities"));
    }

    private String text(JsonNode body, String field) {
        return body.path(field).asText(null);
    }

    @GetMapping("/orders")
    public JsonNode orders(HttpServletRequest request) {
        return bookingService.listOrders(agentId(request));
    }

    @GetMapping("/orders/{orderId}")
    public JsonNode order(@PathVariable String orderId, HttpServletRequest request) {
        return bookingService.getOrder(agentId(request), orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public JsonNode cancel(@PathVariable String orderId,
                           @RequestParam(required = false) String test,
                           HttpServletRequest request) {
        return bookingService.cancelOrder(agentId(request), orderId, test);
    }

    // ---------- 公共 ----------

    /** 闸门放行时挂上的代理商编号。取不到说明本端点被漏在闸门之外，属配置事故 */
    private String agentId(HttpServletRequest request) {
        Object value = request.getAttribute(B2bAuthFilter.AGENT_ID_ATTRIBUTE);
        if (value == null) {
            throw new IllegalStateException("请求未经登录闸门：检查 B2bFilterConfig 的 URL 模式");
        }
        return value.toString();
    }

    private JsonNode agentView(AgentStore.AgentRow agent) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("agentId", agent.agentId);
        node.put("agentName", agent.agentName);
        node.put("loginName", agent.loginName);
        node.put("agreementVersion", props.getAgreement().getVersion());
        node.put("agreementAccepted", bookingService.hasAcceptedAgreement(agent.agentId));
        return node;
    }

    private String sessionCookie(String token, long maxAgeSeconds) {
        StringBuilder cookie = new StringBuilder(B2bAuthFilter.COOKIE_NAME + "=" + token)
                .append("; Path=/b2b")
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly")
                .append("; SameSite=Lax");
        if (props.getSession().isCookieSecure()) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    private String cookieToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (javax.servlet.http.Cookie cookie : request.getCookies()) {
            if (B2bAuthFilter.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 经 nginx 代理，直连 remoteAddr 是网关内网地址，故优先取转发头的首段 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 直接取原始重复参数值。不能用 {@code @RequestParam List<String>}——单个
     * {@code occupancy=2-7,11} 会被 Spring 的 StringToCollection 转换按逗号拆成
     * ["2-7", "11"]，把一间 2 大 2 小错当成两间。与 {@code BffController} 同因同法。
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
                throw new B2bException(400, "儿童年龄格式错误: " + part);
            }
        }
        return ages;
    }
}
