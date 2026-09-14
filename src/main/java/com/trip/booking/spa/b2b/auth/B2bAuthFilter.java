package com.trip.booking.spa.b2b.auth;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * /b2b/** 的登录闸门：无有效会话一律 401，且请求不进入业务代码。
 *
 * <p>这道闸门是 B2B 的实质而非装饰：Expedia 的 B2B 要求代理工具是封闭用户环境，
 * 价格不得对公众公开。因此本包不复用 {@code /bff/**} 的公开端点，而是自建一组
 * 同名端点走本闸门之后——否则前端加登录页也没用，绕过页面直接打接口即可取价。
 *
 * <p><b>本类刻意不是 {@code @Component}。</b>实现了 {@code Filter} 的 Spring Bean 会被
 * Spring Boot 自动注册到 {@code /*}，那样本闸门会把 {@code /client/spa/**}（上游取货）
 * 与 {@code /actuator/**} 一并 401 掉。实例只由
 * {@link com.trip.booking.spa.b2b.config.B2bFilterConfig} 构造并限定在 {@code /b2b/*}。
 */
@Slf4j
public class B2bAuthFilter implements Filter {

    /** 会话 Cookie 名 */
    public static final String COOKIE_NAME = "b2b_session";

    /** 通过闸门后，代理商编号挂在请求上供下游读取 */
    public static final String AGENT_ID_ATTRIBUTE = "b2b.agentId";

    private final SessionStore sessionStore;

    public B2bAuthFilter(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (isOpenPath(req)) {
            chain.doFilter(request, response);
            return;
        }

        String agentId = sessionStore.resolve(tokenOf(req));
        if (agentId == null) {
            reject(resp);
            return;
        }
        req.setAttribute(AGENT_ID_ATTRIBUTE, agentId);
        chain.doFilter(request, response);
    }

    /**
     * 免登录的两个口子：登录本身，以及站级信息。
     *
     * <p>站级信息（协议链接、Expedia 集团条款、客服入口）必须在登录前可读：
     * 「预订前阅读协议」与「客服清楚可见」两条验收要求都不以登录为前提，
     * 登录页上就得能点开。它不含任何价格、库存或订单，放开无损封闭用户环境。
     */
    private boolean isOpenPath(HttpServletRequest req) {
        String path = req.getRequestURI();
        if ("POST".equalsIgnoreCase(req.getMethod()) && "/b2b/session".equals(path)) {
            return true;
        }
        return "GET".equalsIgnoreCase(req.getMethod()) && "/b2b/site".equals(path);
    }

    private String tokenOf(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void reject(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"error\":\"未登录或会话已过期\"}");
        resp.getWriter().flush();
    }
}
