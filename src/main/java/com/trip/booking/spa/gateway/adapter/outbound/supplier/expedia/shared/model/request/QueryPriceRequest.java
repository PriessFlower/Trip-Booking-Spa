package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.request;

import lombok.Builder;

import java.util.List;

/**
 * 查询报价相关信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/

@Builder
public class QueryPriceRequest {

    private String checkin;

    private String checkout;

    private String country_code;

    private String currency;

    private String language;

    private List<String> occupancies;

    private String property_id;

    private String rate_plan_count;

    private String sales_channel;

    private String sales_environment;

    private String rate_option;

    private String billing_terms;

    private String payment_terms;

    private String partner_point_of_sale;

    public String getCheckin() {
        return checkin;
    }

    public void setCheckin(String checkin) {
        this.checkin = checkin;
    }

    public String getCheckout() {
        return checkout;
    }

    public void setCheckout(String checkout) {
        this.checkout = checkout;
    }

    public String getCountry_code() {
        return country_code;
    }

    public void setCountry_code(String country_code) {
        this.country_code = country_code;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<String> getOccupancies() {
        return occupancies;
    }

    public void setOccupancies(List<String> occupancies) {
        this.occupancies = occupancies;
    }

    public String getProperty_id() {
        return property_id;
    }

    public void setProperty_id(String property_id) {
        this.property_id = property_id;
    }

    public String getRate_plan_count() {
        return rate_plan_count;
    }

    public void setRate_plan_count(String rate_plan_count) {
        this.rate_plan_count = rate_plan_count;
    }

    public String getSales_channel() {
        return sales_channel;
    }

    public void setSales_channel(String sales_channel) {
        this.sales_channel = sales_channel;
    }

    public String getSales_environment() {
        return sales_environment;
    }

    public void setSales_environment(String sales_environment) {
        this.sales_environment = sales_environment;
    }

    public String getRate_option() {
        return rate_option;
    }

    public void setRate_option(String rate_option) {
        this.rate_option = rate_option;
    }

    public String getBilling_terms() {
        return billing_terms;
    }

    public void setBilling_terms(String billing_terms) {
        this.billing_terms = billing_terms;
    }

    public String getPayment_terms() {
        return payment_terms;
    }

    public void setPayment_terms(String payment_terms) {
        this.payment_terms = payment_terms;
    }

    public String getPartner_point_of_sale() {
        return partner_point_of_sale;
    }

    public void setPartner_point_of_sale(String partner_point_of_sale) {
        this.partner_point_of_sale = partner_point_of_sale;
    }
}
