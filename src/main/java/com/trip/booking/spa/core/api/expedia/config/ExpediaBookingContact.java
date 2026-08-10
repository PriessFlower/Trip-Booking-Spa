package com.trip.booking.spa.core.api.expedia.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 下单提交给 Expedia 的固定联系与账单信息，均为我方（affiliate）信息，非旅客信息。
 *
 * <p><b>邮箱为何必须是配置而非每单传入</b>：反查订单要求邮箱与下单时完全一致
 * （{@code GET /v3/itineraries?affiliate_reference_id=..&email=..}）。若每单邮箱不同，
 * 网关就必须持久化"这一单当初用了哪个邮箱"才能反查——而反查恰恰是下单结果不确定时的
 * 唯一确证手段，把它建立在额外状态之上会让最脆弱的路径更脆弱。固定为一个值后，
 * 仅凭上游订单号即可反查。
 *
 * <p>取值由 Nacos {@code supplier.expedia.booking-contact} 下发（JSON 字符串）。
 * 该值应长期稳定：更换后，更换之前创建的订单将无法再用新邮箱反查。
 */
@Slf4j
@Getter
@Setter
@Component
@RefreshScope
public class ExpediaBookingContact {

    @Value("${supplier.expedia.booking-contact:}")
    private String bookingContactJson;

    private volatile Contact contact = new Contact();

    @PostConstruct
    void parse() {
        if (StringUtils.isBlank(bookingContactJson)) {
            // 缺配置不在此处抛错：下单未启用时本 bean 也会被创建，不应拖垮启动。
            // 实际下单前由 requireUsable() 校验，届时失败可归入「确定失败」而非「结果不确定」
            log.warn("supplier.expedia.booking-contact 未配置，下单将不可用");
            contact = new Contact();
            return;
        }
        try {
            Contact parsed = JsonUtils.decodeJson(bookingContactJson, new TypeReference<Contact>() {
            });
            contact = parsed == null ? new Contact() : parsed;
        } catch (Exception e) {
            log.error("supplier.expedia.booking-contact 解析失败，下单将不可用: {}", bookingContactJson, e);
            contact = new Contact();
        }
    }

    /**
     * 下单前校验必需字段齐备。缺失属配置问题、重试不会改变，
     * 故调用方应据此判「确定失败」，而非「结果不确定」。
     */
    public void requireUsable() {
        if (StringUtils.isAnyBlank(contact.getEmail(), contact.getGivenName(), contact.getFamilyName(),
                contact.getAddressLine1(), contact.getCity(), contact.getPostalCode(), contact.getCountryCode())) {
            throw new IllegalStateException(
                    "supplier.expedia.booking-contact 配置不完整，无法下单；缺失项见该 Nacos 键");
        }
    }

    @Getter
    @Setter
    public static class Contact {
        private String email;
        private String phoneCountryCode;
        private String phoneNumber;
        private String givenName;
        private String familyName;
        private String addressLine1;
        private String city;
        private String stateProvinceCode;
        private String postalCode;
        private String countryCode;
    }
}
