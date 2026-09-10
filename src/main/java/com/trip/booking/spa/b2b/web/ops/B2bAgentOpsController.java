package com.trip.booking.spa.b2b.web.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trip.booking.spa.b2b.auth.PasswordHash;
import com.trip.booking.spa.b2b.store.AgentStore;
import com.trip.booking.spa.b2b.web.B2bException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 代理商开户后门。目前没有自助注册，账号一律由运维开。
 *
 * <p><b>路径刻意挂在 {@code /hotel/} 下而非 {@code /b2b/}。</b>两条理由：
 * 一是 {@code /b2b/**} 在登录闸门之后，而开户必须在没有会话时可用；
 * 二是 nginx 只把 {@code /bff/} 与 {@code /grafana/} 代理到公网
 * （见 deploy/nginx/conf.d/spa.haowan2000.com.conf），{@code /hotel/**} 只能从内网打——
 * 这正是网关侧 {@code BackDoorController} 采用的隔离方式，开户端点必须享有同一层隔离，
 * 否则等于把「谁都能给自己开个代理商账号」放到公网。
 *
 * <p>因此把 {@code /b2b/} 加进 nginx 代理时，<b>不得</b>连带把 {@code /hotel/} 一起放出去。
 */
@Slf4j
@RestController
@RequestMapping("/hotel/b2b")
public class B2bAgentOpsController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentStore agentStore;

    public B2bAgentOpsController(AgentStore agentStore) {
        this.agentStore = agentStore;
    }

    /**
     * 开户。口令只在此处入参一次，落库即为 PBKDF2 哈希，明文不落日志也不可回读。
     *
     * @return 开户结果；不回显口令
     */
    @PostMapping("/agent")
    public JsonNode createAgent(@RequestBody Map<String, String> body) {
        String agentId = required(body, "agentId");
        String loginName = required(body, "loginName");
        String password = required(body, "password");
        String agentName = required(body, "agentName");

        if (agentStore.findById(agentId).isPresent()) {
            throw new B2bException(409, "代理商编号已存在: " + agentId);
        }
        if (agentStore.findByLoginName(loginName).isPresent()) {
            throw new B2bException(409, "登录名已存在: " + loginName);
        }

        AgentStore.AgentRow row = new AgentStore.AgentRow();
        row.agentId = agentId;
        row.loginName = loginName;
        row.passwordHash = PasswordHash.hash(password);
        row.agentName = agentName;
        row.contactEmail = body.get("contactEmail");
        row.contactPhone = body.get("contactPhone");
        row.enabled = true;
        agentStore.insert(row);

        log.info("B2B 开户 agentId={} loginName={} agentName={}", agentId, loginName, agentName);
        ObjectNode node = MAPPER.createObjectNode();
        node.put("agentId", agentId);
        node.put("loginName", loginName);
        node.put("agentName", agentName);
        node.put("created", true);
        return node;
    }

    private String required(Map<String, String> body, String field) {
        String value = body.get(field);
        if (value == null || value.isBlank()) {
            throw new B2bException(400, "缺少字段: " + field);
        }
        return value;
    }
}
