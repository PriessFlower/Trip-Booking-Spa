package com.trip.booking.spa.b2b.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * B2B 代理商平台配置。
 *
 * <p>本包是与 {@code bff}（B2C 演示站）平级的独立边界：取数与下单在进程内复用 bff 的
 * {@code BffShopService} / {@code BffBookingService}，本包只加「门」——登录、协议签署、
 * 订单归属。不改 bff 一行，也不反向被 bff 引用。
 *
 * <p>两站共用同一条 Expedia 车道（{@code supplier.expedia.*}，当前为 B2B 档案）。
 * 车道由 {@code ExpediaContractProfile} 全程序唯一持有，本包不另开写入点。
 */
@Component
@ConfigurationProperties(prefix = "b2b")
public class B2bProperties {

    private final Agreement agreement = new Agreement();
    private final Session session = new Session();
    private final Support support = new Support();

    /**
     * Expedia 下游代理协议（GR3）与 Expedia 集团条款（GR2）。
     *
     * <p>两者是不同的东西，验收也分成两条：GR3 要求代理商**签**下游代理协议并留痕，
     * GR2 只要求页面上**能点开** Expedia 集团条款。故 {@code expediaTermsUrl} 不参与签署。
     *
     * <p>{@code version} 一经发布不应回改，只应递增：留痕以它为键，改版即要求所有代理商重签。
     */
    public static class Agreement {
        private String version = "2026-09-v1";

        /** 下游代理协议原文，登录页与协议弹窗各展示一处 */
        private String url = "https://developers.expediagroup.com/rapid/setup/launch-requirements/lodging-launch-reqs";

        /** GR2：Expedia 集团条款链接，与上面那份不是同一份 */
        private String expediaTermsUrl = "https://www.expedia.com/lp/lg/terms-of-use";

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getExpediaTermsUrl() {
            return expediaTermsUrl;
        }

        public void setExpediaTermsUrl(String expediaTermsUrl) {
            this.expediaTermsUrl = expediaTermsUrl;
        }
    }

    /**
     * ER2：客服入口。要求「清楚展示客户支持方式，含在线客服工具链接」，
     * 故三项都做成配置——运营口径未定时不该把某个邮箱写死在代码里。
     *
     * <p>默认值只是占位，上线前必须换成真实值；留着占位过审等于交一份假证据。
     */
    public static class Support {
        private String email = "support@tripbooks.org";
        private String phone = "";

        /** 在线客服工具地址。为空时前端只展示邮箱与电话 */
        private String onlineUrl = "";

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getOnlineUrl() {
            return onlineUrl;
        }

        public void setOnlineUrl(String onlineUrl) {
            this.onlineUrl = onlineUrl;
        }
    }

    public static class Session {
        /** 会话有效期。到期即需重新登录，不做滑动续期 */
        private long ttlSeconds = 7200;

        /**
         * 会话 Cookie 是否只在 HTTPS 下回传。生产必须为 true；
         * 误开后果：本地 HTTP 调试登录不上（Cookie 不回传）。
         * 误关后果：Cookie 可能经明文链路泄露，等于会话被劫持。
         * 生效执行面：所有 profile 的 /b2b/** 登录响应。
         */
        private boolean cookieSecure = true;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public Agreement getAgreement() {
        return agreement;
    }

    public Session getSession() {
        return session;
    }

    public Support getSupport() {
        return support;
    }
}
