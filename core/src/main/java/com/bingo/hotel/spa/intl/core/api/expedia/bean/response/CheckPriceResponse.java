package com.bingo.hotel.spa.intl.core.api.expedia.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;

import java.util.List;
import java.util.Map;

/**
 * 验价信息反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public class CheckPriceResponse implements BaseResponse {

    private Integer adultCount;
    private String status;
    private Map<String, Occupancy_pricing> occupancy_pricing;
    private Links links;

    public Integer getAdultCount() {
        return adultCount;
    }

    public void setAdultCount(Integer adultCount) {
        this.adultCount = adultCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Occupancy_pricing> getOccupancy_pricing() {
        return occupancy_pricing;
    }

    public void setOccupancy_pricing(Map<String, Occupancy_pricing> occupancy_pricing) {
        this.occupancy_pricing = occupancy_pricing;
    }

    public Links getLinks() {
        return links;
    }

    public void setLinks(Links links) {
        this.links = links;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class Occupancy_pricing {

        private List<List<Nightly>> nightly;
        private List<Stay> stay;
        private Fees fees;
        private Totals totals;

        public List<List<Nightly>> getNightly() {
            return nightly;
        }

        public void setNightly(List<List<Nightly>> nightly) {
            this.nightly = nightly;
        }

        public List<Stay> getStay() {
            return stay;
        }

        public void setStay(List<Stay> stay) {
            this.stay = stay;
        }

        public Fees getFees() {
            return fees;
        }

        public void setFees(Fees fees) {
            this.fees = fees;
        }

        public Totals getTotals() {
            return totals;
        }

        public void setTotals(Totals totals) {
            this.totals = totals;
        }
    }

    public static class Fees {

        private AmountInfo mandatory_fee;
        private AmountInfo resort_fee;
        private AmountInfo mandatory_tax;

        public AmountInfo getMandatory_fee() {
            return mandatory_fee;
        }

        public void setMandatory_fee(AmountInfo mandatory_fee) {
            this.mandatory_fee = mandatory_fee;
        }

        public AmountInfo getResort_fee() {
            return resort_fee;
        }

        public void setResort_fee(AmountInfo resort_fee) {
            this.resort_fee = resort_fee;
        }

        public AmountInfo getMandatory_tax() {
            return mandatory_tax;
        }

        public void setMandatory_tax(AmountInfo mandatory_tax) {
            this.mandatory_tax = mandatory_tax;
        }
    }

    public static class AmountInfo {

        private CurrencyInfo request_currency;
        private CurrencyInfo billable_currency;

        public CurrencyInfo getRequest_currency() {
            return request_currency;
        }

        public void setRequest_currency(CurrencyInfo request_currency) {
            this.request_currency = request_currency;
        }

        public CurrencyInfo getBillable_currency() {
            return billable_currency;
        }

        public void setBillable_currency(CurrencyInfo billable_currency) {
            this.billable_currency = billable_currency;
        }
    }

    public static class CurrencyInfo {

        private String value;
        private String currency;

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getCurrency() {
            return currency;
        }

    }

    public static class Totals {

        private AmountInfo property_inclusive_strikethrough;//线上加线下含税总价
        private AmountInfo strikethrough;
        private AmountInfo exclusive;//线上不含税总价
        private AmountInfo property_inclusive;
        private AmountInfo inclusive;//线上含税总价
        private AmountInfo property_fees;//旅客到店支付总额
        private AmountInfo inclusive_strikethrough;
        private AmountInfo marketing_fee;//佣金
        private AmountInfo gross_profit;//gross_profit*百分比=marketing_fee

        public AmountInfo getProperty_inclusive_strikethrough() {
            return property_inclusive_strikethrough;
        }

        public void setProperty_inclusive_strikethrough(AmountInfo property_inclusive_strikethrough) {
            this.property_inclusive_strikethrough = property_inclusive_strikethrough;
        }

        public AmountInfo getStrikethrough() {
            return strikethrough;
        }

        public void setStrikethrough(AmountInfo strikethrough) {
            this.strikethrough = strikethrough;
        }

        public AmountInfo getExclusive() {
            return exclusive;
        }

        public void setExclusive(AmountInfo exclusive) {
            this.exclusive = exclusive;
        }

        public AmountInfo getProperty_inclusive() {
            return property_inclusive;
        }

        public void setProperty_inclusive(AmountInfo property_inclusive) {
            this.property_inclusive = property_inclusive;
        }

        public AmountInfo getInclusive() {
            return inclusive;
        }

        public void setInclusive(AmountInfo inclusive) {
            this.inclusive = inclusive;
        }

        public AmountInfo getProperty_fees() {
            return property_fees;
        }

        public void setProperty_fees(AmountInfo property_fees) {
            this.property_fees = property_fees;
        }

        public AmountInfo getInclusive_strikethrough() {
            return inclusive_strikethrough;
        }

        public void setInclusive_strikethrough(AmountInfo inclusive_strikethrough) {
            this.inclusive_strikethrough = inclusive_strikethrough;
        }

        public AmountInfo getMarketing_fee() {
            return marketing_fee;
        }

        public void setMarketing_fee(AmountInfo marketing_fee) {
            this.marketing_fee = marketing_fee;
        }

        public AmountInfo getGross_profit() {
            return gross_profit;
        }

        public void setGross_profit(AmountInfo gross_profit) {
            this.gross_profit = gross_profit;
        }
    }

    public static class Nightly {

        private String type;
        private String value;
        private String currency;

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getCurrency() {
            return currency;
        }

    }

    public static class Stay {

        private String type;
        private String value;
        private String currency;

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getCurrency() {
            return currency;
        }

    }

    public static class Links {

        private LinkInfo commit;

        private LinkInfo book;

        public LinkInfo getCommit() {
            return commit;
        }

        public void setCommit(LinkInfo commit) {
            this.commit = commit;
        }

        public LinkInfo getBook() {
            return book;
        }

        public void setBook(LinkInfo book) {
            this.book = book;
        }
    }

    public static class LinkInfo {

        private String method;
        private String href;

        public void setMethod(String method) {
            this.method = method;
        }

        public String getMethod() {
            return method;
        }

        public void setHref(String href) {
            this.href = href;
        }

        public String getHref() {
            return href;
        }

    }
}
