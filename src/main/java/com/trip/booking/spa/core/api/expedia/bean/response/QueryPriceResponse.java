package com.trip.booking.spa.core.api.expedia.bean.response;

import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询报价信息反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public class QueryPriceResponse implements BaseResponse {

    private List<HotelPrice> hotelPrices;

    private List<ProductRespDTO> productRespDTOList;

    public List<HotelPrice> getHotelPrices() {
        return hotelPrices;
    }

    public void setHotelPrices(List<HotelPrice> hotelPrices) {
        this.hotelPrices = hotelPrices;
    }

    public List<ProductRespDTO> getProductRespDTOList() {
        return productRespDTOList;
    }

    public void setProductRespDTOList(List<ProductRespDTO> productRespDTOList) {
        this.productRespDTOList = productRespDTOList;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class HotelPrice {
        private String property_id;
        private String status;
        private List<Rooms> rooms;
        private int score;

        public void setProperty_id(String property_id) {
            this.property_id = property_id;
        }

        public String getProperty_id() {
            return property_id;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public void setRooms(List<Rooms> rooms) {
            this.rooms = rooms;
        }

        public List<Rooms> getRooms() {
            return rooms;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }
    }


    public static class Rooms {

        private String id;
        private String room_name;
        private List<Rates> rates;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setRoom_name(String room_name) {
            this.room_name = room_name;
        }

        public String getRoom_name() {
            return room_name;
        }

        public void setRates(List<Rates> rates) {
            this.rates = rates;
        }

        public List<Rates> getRates() {
            return rates;
        }

    }

    public static class Rates {

        private String id;
        private String status;
        private int available_rooms;
        private boolean refundable;
        private boolean member_deal_available;
        private Sale_scenario sale_scenario;
        private String merchant_of_record;
        private Map<String, Amenity> amenities;
        private Map<String, Bed_groups> bed_groups;
        private List<CancelPolicy> cancel_penalties;
        private List<CancelPolicy> nonrefundable_date_ranges;
        private Map<String, Occupancy_pricing> occupancy_pricing;
        private Promotions promotions;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public void setAvailable_rooms(int available_rooms) {
            this.available_rooms = available_rooms;
        }

        public int getAvailable_rooms() {
            return available_rooms;
        }

        public void setRefundable(boolean refundable) {
            this.refundable = refundable;
        }

        public boolean getRefundable() {
            return refundable;
        }

        public void setMember_deal_available(boolean member_deal_available) {
            this.member_deal_available = member_deal_available;
        }

        public boolean getMember_deal_available() {
            return member_deal_available;
        }

        public void setSale_scenario(Sale_scenario sale_scenario) {
            this.sale_scenario = sale_scenario;
        }

        public Sale_scenario getSale_scenario() {
            return sale_scenario;
        }

        public void setMerchant_of_record(String merchant_of_record) {
            this.merchant_of_record = merchant_of_record;
        }

        public String getMerchant_of_record() {
            return merchant_of_record;
        }

        public Map<String, Amenity> getAmenities() {
            return amenities;
        }

        public void setAmenities(Map<String, Amenity> amenities) {
            this.amenities = amenities;
        }

        public Map<String, Bed_groups> getBed_groups() {
            return bed_groups;
        }

        public void setBed_groups(Map<String, Bed_groups> bed_groups) {
            this.bed_groups = bed_groups;
        }

        public List<CancelPolicy> getCancel_penalties() {
            return cancel_penalties;
        }

        public void setCancel_penalties(List<CancelPolicy> cancel_penalties) {
            this.cancel_penalties = cancel_penalties;
        }

        public List<CancelPolicy> getNonrefundable_date_ranges() {
            return nonrefundable_date_ranges;
        }

        public void setNonrefundable_date_ranges(List<CancelPolicy> nonrefundable_date_ranges) {
            this.nonrefundable_date_ranges = nonrefundable_date_ranges;
        }

        public Map<String, Occupancy_pricing> getOccupancy_pricing() {
            return occupancy_pricing;
        }

        public void setOccupancy_pricing(Map<String, Occupancy_pricing> occupancy_pricing) {
            this.occupancy_pricing = occupancy_pricing;
        }

        public void setPromotions(Promotions promotions) {
            this.promotions = promotions;
        }

        public Promotions getPromotions() {
            return promotions;
        }
    }

    public static class Sale_scenario {

        private boolean member;
        private boolean corporate;
        private boolean distribution;

        public void setMember(boolean member) {
            this.member = member;
        }

        public boolean getMember() {
            return member;
        }

        public void setCorporate(boolean corporate) {
            this.corporate = corporate;
        }

        public boolean getCorporate() {
            return corporate;
        }

        public void setDistribution(boolean distribution) {
            this.distribution = distribution;
        }

        public boolean getDistribution() {
            return distribution;
        }

    }

    public static class Amenity {

        private String id;
        private String name;
        private String value;
        private String categories;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getCategories() {
            return categories;
        }

        public void setCategories(String categories) {
            this.categories = categories;
        }
    }

    public static class Payment_options {

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

    public static class Bed_groups {


        private String id;
        private String description;
        private Links links;
        private List<Configuration> configuration;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public void setLinks(Links links) {
            this.links = links;
        }

        public Links getLinks() {
            return links;
        }

        public void setConfiguration(List<Configuration> configuration) {
            this.configuration = configuration;
        }

        public List<Configuration> getConfiguration() {
            return configuration;
        }

    }

    public static class Links {

        private Price_check price_check;

        public void setPrice_check(Price_check price_check) {
            this.price_check = price_check;
        }

        public Price_check getPrice_check() {
            return price_check;
        }

    }

    public static class Price_check {

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

    public static class Configuration {

        private String type;
        private String size;
        private int quantity;

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public String getSize() {
            return size;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public int getQuantity() {
            return quantity;
        }

    }

    public static class CancelPolicy {

        private String start;
        private String end;
        private String nights;
        private String amount;
        private String percent;
        private String currency;

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }

        public String getNights() {
            return nights;
        }

        public void setNights(String nights) {
            this.nights = nights;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getPercent() {
            return percent;
        }

        public void setPercent(String percent) {
            this.percent = percent;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
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

    public static class Promotions {

        private Deal deal;
        private Value_adds value_adds;

        public void setDeal(Deal deal) {
            this.deal = deal;
        }

        public Deal getDeal() {
            return deal;
        }

        public void setValue_adds(Value_adds value_adds) {
            this.value_adds = value_adds;
        }

        public Value_adds getValue_adds() {
            return value_adds;
        }

    }

    public static class Deal {

        private String id;
        private String description;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class Value_adds {

        private String id;
        private String description;
        private String category;
        private String offer_type;
        private String frequency;
        private int person_count;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getCategory() {
            return category;
        }

        public void setOffer_type(String offer_type) {
            this.offer_type = offer_type;
        }

        public String getOffer_type() {
            return offer_type;
        }

        public void setFrequency(String frequency) {
            this.frequency = frequency;
        }

        public String getFrequency() {
            return frequency;
        }

        public void setPerson_count(int person_count) {
            this.person_count = person_count;
        }

        public int getPerson_count() {
            return person_count;
        }

    }
}
