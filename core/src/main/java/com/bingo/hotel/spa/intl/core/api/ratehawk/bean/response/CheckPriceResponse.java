package com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 验价信息反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/10
 * @since : 1.0
 **/

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CheckPriceResponse implements BaseResponse {

    private List<Hotels> hotels;

    private Original_request_params original_request_params;

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class Original_request_params {

        private String checkin;
        private String checkout;
        private List<Guests> guests;
        private String residency;

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

        public List<Guests> getGuests() {
            return guests;
        }

        public void setGuests(List<Guests> guests) {
            this.guests = guests;
        }

        public String getResidency() {
            return residency;
        }

        public void setResidency(String residency) {
            this.residency = residency;
        }
    }


    public static class Hotels {

        private String id;
        private long hid;
        private List<Rates> rates;
        private String bar_price_data;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public long getHid() {
            return hid;
        }

        public void setHid(long hid) {
            this.hid = hid;
        }

        public List<Rates> getRates() {
            return rates;
        }

        public void setRates(List<Rates> rates) {
            this.rates = rates;
        }

        public String getBar_price_data() {
            return bar_price_data;
        }

        public void setBar_price_data(String bar_price_data) {
            this.bar_price_data = bar_price_data;
        }
    }

    public static class Rates {

        private String book_hash;
        private String match_hash;
        private List<String> daily_prices;
        private String meal;
        private Meal_data meal_data;
        private Payment_options payment_options;
        private String bar_rate_price_data;
        private Rg_ext rg_ext;
        private String room_name;
        private String room_name_info;
        private List<String> serp_filters;
        private String sell_price_limits;
        private int allotment;
        private List<String> amenities_data;
        private boolean any_residency;
        private String deposit;
        private No_show no_show;
        private Room_data_trans room_data_trans;

        public String getBook_hash() {
            return book_hash;
        }

        public void setBook_hash(String book_hash) {
            this.book_hash = book_hash;
        }

        public String getMatch_hash() {
            return match_hash;
        }

        public void setMatch_hash(String match_hash) {
            this.match_hash = match_hash;
        }

        public List<String> getDaily_prices() {
            return daily_prices;
        }

        public void setDaily_prices(List<String> daily_prices) {
            this.daily_prices = daily_prices;
        }

        public String getMeal() {
            return meal;
        }

        public void setMeal(String meal) {
            this.meal = meal;
        }

        public Meal_data getMeal_data() {
            return meal_data;
        }

        public void setMeal_data(Meal_data meal_data) {
            this.meal_data = meal_data;
        }

        public Payment_options getPayment_options() {
            return payment_options;
        }

        public void setPayment_options(Payment_options payment_options) {
            this.payment_options = payment_options;
        }

        public String getBar_rate_price_data() {
            return bar_rate_price_data;
        }

        public void setBar_rate_price_data(String bar_rate_price_data) {
            this.bar_rate_price_data = bar_rate_price_data;
        }

        public Rg_ext getRg_ext() {
            return rg_ext;
        }

        public void setRg_ext(Rg_ext rg_ext) {
            this.rg_ext = rg_ext;
        }

        public String getRoom_name() {
            return room_name;
        }

        public void setRoom_name(String room_name) {
            this.room_name = room_name;
        }

        public String getRoom_name_info() {
            return room_name_info;
        }

        public void setRoom_name_info(String room_name_info) {
            this.room_name_info = room_name_info;
        }

        public List<String> getSerp_filters() {
            return serp_filters;
        }

        public void setSerp_filters(List<String> serp_filters) {
            this.serp_filters = serp_filters;
        }

        public String getSell_price_limits() {
            return sell_price_limits;
        }

        public void setSell_price_limits(String sell_price_limits) {
            this.sell_price_limits = sell_price_limits;
        }

        public int getAllotment() {
            return allotment;
        }

        public void setAllotment(int allotment) {
            this.allotment = allotment;
        }

        public List<String> getAmenities_data() {
            return amenities_data;
        }

        public void setAmenities_data(List<String> amenities_data) {
            this.amenities_data = amenities_data;
        }

        public boolean isAny_residency() {
            return any_residency;
        }

        public void setAny_residency(boolean any_residency) {
            this.any_residency = any_residency;
        }

        public String getDeposit() {
            return deposit;
        }

        public void setDeposit(String deposit) {
            this.deposit = deposit;
        }

        public No_show getNo_show() {
            return no_show;
        }

        public void setNo_show(No_show no_show) {
            this.no_show = no_show;
        }

        public Room_data_trans getRoom_data_trans() {
            return room_data_trans;
        }

        public void setRoom_data_trans(Room_data_trans room_data_trans) {
            this.room_data_trans = room_data_trans;
        }
    }

    public static class No_show {

        private String amount;
        private String currency_code;
        private String from_time;

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getAmount() {
            return amount;
        }

        public void setCurrency_code(String currency_code) {
            this.currency_code = currency_code;
        }

        public String getCurrency_code() {
            return currency_code;
        }

        public void setFrom_time(String from_time) {
            this.from_time = from_time;
        }

        public String getFrom_time() {
            return from_time;
        }

    }

    public static class Meal_data {

        private String value;
        private boolean has_breakfast;
        private boolean no_child_meal;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isHas_breakfast() {
            return has_breakfast;
        }

        public void setHas_breakfast(boolean has_breakfast) {
            this.has_breakfast = has_breakfast;
        }

        public boolean isNo_child_meal() {
            return no_child_meal;
        }

        public void setNo_child_meal(boolean no_child_meal) {
            this.no_child_meal = no_child_meal;
        }
    }

    public static class Payment_options {

        private List<Payment_types> payment_types;

        public List<Payment_types> getPayment_types() {
            return payment_types;
        }

        public void setPayment_types(List<Payment_types> payment_types) {
            this.payment_types = payment_types;
        }
    }

    public static class Payment_types {

        private String amount;
        private String show_amount;
        private String currency_code;
        private String show_currency_code;
        private String by;
        private boolean is_need_credit_card_data;
        private boolean is_need_cvc;
        private String type;
        private Vat_data vat_data;
        private Tax_data tax_data;
        private Perks perks;
        private Commission_info commission_info;
        private CancellationInfo cancellation_penalties;
        private String recommended_price;

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getShow_amount() {
            return show_amount;
        }

        public void setShow_amount(String show_amount) {
            this.show_amount = show_amount;
        }

        public String getCurrency_code() {
            return currency_code;
        }

        public void setCurrency_code(String currency_code) {
            this.currency_code = currency_code;
        }

        public String getShow_currency_code() {
            return show_currency_code;
        }

        public void setShow_currency_code(String show_currency_code) {
            this.show_currency_code = show_currency_code;
        }

        public String getBy() {
            return by;
        }

        public void setBy(String by) {
            this.by = by;
        }

        public boolean isIs_need_credit_card_data() {
            return is_need_credit_card_data;
        }

        public void setIs_need_credit_card_data(boolean is_need_credit_card_data) {
            this.is_need_credit_card_data = is_need_credit_card_data;
        }

        public boolean isIs_need_cvc() {
            return is_need_cvc;
        }

        public void setIs_need_cvc(boolean is_need_cvc) {
            this.is_need_cvc = is_need_cvc;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Vat_data getVat_data() {
            return vat_data;
        }

        public void setVat_data(Vat_data vat_data) {
            this.vat_data = vat_data;
        }

        public Tax_data getTax_data() {
            return tax_data;
        }

        public void setTax_data(Tax_data tax_data) {
            this.tax_data = tax_data;
        }

        public Perks getPerks() {
            return perks;
        }

        public void setPerks(Perks perks) {
            this.perks = perks;
        }

        public Commission_info getCommission_info() {
            return commission_info;
        }

        public void setCommission_info(Commission_info commission_info) {
            this.commission_info = commission_info;
        }

        public CancellationInfo getCancellation_penalties() {
            return cancellation_penalties;
        }

        public void setCancellation_penalties(CancellationInfo cancellation_penalties) {
            this.cancellation_penalties = cancellation_penalties;
        }

        public String getRecommended_price() {
            return recommended_price;
        }

        public void setRecommended_price(String recommended_price) {
            this.recommended_price = recommended_price;
        }
    }

    public static class Rg_ext {

        private int quality;
        private int sex;
        private int bathroom;
        private int bedding;
        private int family;
        private int capacity;
        private int club;
        private int bedrooms;
        private int balcony;
        private int view;
        private int floor;

        public int getQuality() {
            return quality;
        }

        public void setQuality(int quality) {
            this.quality = quality;
        }

        public int getSex() {
            return sex;
        }

        public void setSex(int sex) {
            this.sex = sex;
        }

        public int getBathroom() {
            return bathroom;
        }

        public void setBathroom(int bathroom) {
            this.bathroom = bathroom;
        }

        public int getBedding() {
            return bedding;
        }

        public void setBedding(int bedding) {
            this.bedding = bedding;
        }

        public int getFamily() {
            return family;
        }

        public void setFamily(int family) {
            this.family = family;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getClub() {
            return club;
        }

        public void setClub(int club) {
            this.club = club;
        }

        public int getBedrooms() {
            return bedrooms;
        }

        public void setBedrooms(int bedrooms) {
            this.bedrooms = bedrooms;
        }

        public int getBalcony() {
            return balcony;
        }

        public void setBalcony(int balcony) {
            this.balcony = balcony;
        }

        public int getView() {
            return view;
        }

        public void setView(int view) {
            this.view = view;
        }

        public int getFloor() {
            return floor;
        }

        public void setFloor(int floor) {
            this.floor = floor;
        }
    }

    public static class Room_data_trans {

        private String main_room_type;
        private String main_name;
        private String bathroom;
        private String bedding_type;
        private String misc_room_type;

        public String getMain_room_type() {
            return main_room_type;
        }

        public void setMain_room_type(String main_room_type) {
            this.main_room_type = main_room_type;
        }

        public String getMain_name() {
            return main_name;
        }

        public void setMain_name(String main_name) {
            this.main_name = main_name;
        }

        public String getBathroom() {
            return bathroom;
        }

        public void setBathroom(String bathroom) {
            this.bathroom = bathroom;
        }

        public String getBedding_type() {
            return bedding_type;
        }

        public void setBedding_type(String bedding_type) {
            this.bedding_type = bedding_type;
        }

        public String getMisc_room_type() {
            return misc_room_type;
        }

        public void setMisc_room_type(String misc_room_type) {
            this.misc_room_type = misc_room_type;
        }
    }

    public static class Vat_data {

        private boolean included;
        private boolean applied;
        private String amount;
        private String currency_code;
        private String value;
    }

    public static class Tax_data {

        private List<Taxes> taxes;

        public void setTaxes(List<Taxes> taxes) {
            this.taxes = taxes;
        }

        public List<Taxes> getTaxes() {
            return taxes;
        }

    }

    public static class Perks {

    }

    public static class Commission_info {

        private Show show;
        private Charge charge;

        public void setShow(Show show) {
            this.show = show;
        }

        public Show getShow() {
            return show;
        }

        public void setCharge(Charge charge) {
            this.charge = charge;
        }

        public Charge getCharge() {
            return charge;
        }

    }

    public static class Cancellation_penalties {

        private List<Policies> policies;
        private String free_cancellation_before;

        public void setPolicies(List<Policies> policies) {
            this.policies = policies;
        }

        public List<Policies> getPolicies() {
            return policies;
        }

        public void setFree_cancellation_before(String free_cancellation_before) {
            this.free_cancellation_before = free_cancellation_before;
        }

        public String getFree_cancellation_before() {
            return free_cancellation_before;
        }

    }

    public static class Taxes {

        private String name;
        private boolean included_by_supplier;
        private String amount;
        private String currency_code;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setIncluded_by_supplier(boolean included_by_supplier) {
            this.included_by_supplier = included_by_supplier;
        }

        public boolean getIncluded_by_supplier() {
            return included_by_supplier;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getAmount() {
            return amount;
        }

        public void setCurrency_code(String currency_code) {
            this.currency_code = currency_code;
        }

        public String getCurrency_code() {
            return currency_code;
        }

    }

    public static class Show {

        private String amount_gross;
        private String amount_net;
        private String amount_commission;

        public void setAmount_gross(String amount_gross) {
            this.amount_gross = amount_gross;
        }

        public String getAmount_gross() {
            return amount_gross;
        }

        public void setAmount_net(String amount_net) {
            this.amount_net = amount_net;
        }

        public String getAmount_net() {
            return amount_net;
        }

        public void setAmount_commission(String amount_commission) {
            this.amount_commission = amount_commission;
        }

        public String getAmount_commission() {
            return amount_commission;
        }

    }

    public static class Charge {

        private String amount_gross;
        private String amount_net;
        private String amount_commission;

        public void setAmount_gross(String amount_gross) {
            this.amount_gross = amount_gross;
        }

        public String getAmount_gross() {
            return amount_gross;
        }

        public void setAmount_net(String amount_net) {
            this.amount_net = amount_net;
        }

        public String getAmount_net() {
            return amount_net;
        }

        public void setAmount_commission(String amount_commission) {
            this.amount_commission = amount_commission;
        }

        public String getAmount_commission() {
            return amount_commission;
        }

    }

    public static class Policies {

        private String start_at;
        private String end_at;
        private String amount_charge;
        private String amount_show;
        private Commission_info commission_info;

        public void setStart_at(String start_at) {
            this.start_at = start_at;
        }

        public String getStart_at() {
            return start_at;
        }

        public void setEnd_at(String end_at) {
            this.end_at = end_at;
        }

        public String getEnd_at() {
            return end_at;
        }

        public void setAmount_charge(String amount_charge) {
            this.amount_charge = amount_charge;
        }

        public String getAmount_charge() {
            return amount_charge;
        }

        public void setAmount_show(String amount_show) {
            this.amount_show = amount_show;
        }

        public String getAmount_show() {
            return amount_show;
        }

        public void setCommission_info(Commission_info commission_info) {
            this.commission_info = commission_info;
        }

        public Commission_info getCommission_info() {
            return commission_info;
        }

    }

    public static class Guests {

        private int adults;
        private List<String> children;

        public void setAdults(int adults) {
            this.adults = adults;
        }

        public int getAdults() {
            return adults;
        }

        public void setChildren(List<String> children) {
            this.children = children;
        }

        public List<String> getChildren() {
            return children;
        }
    }
}
