package com.trip.booking.spa.core.api.travelconnect.bean.hotel;

import java.util.List;

public class HotelDetailRequest {

    /**
     * lang : zh-hk
     * hotelcodes : ["213848"]
     * customersessionid : 68335643e382493cbdfb81f12dfa3e02
     * customeripaddress : ::1
     * customeruseragent : Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36
     */

    private String lang;
    private String customersessionid;
    private String customeripaddress;
    private String customeruseragent;
    private List<String> hotelcodes;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getCustomersessionid() {
        return customersessionid;
    }

    public void setCustomersessionid(String customersessionid) {
        this.customersessionid = customersessionid;
    }

    public String getCustomeripaddress() {
        return customeripaddress;
    }

    public void setCustomeripaddress(String customeripaddress) {
        this.customeripaddress = customeripaddress;
    }

    public String getCustomeruseragent() {
        return customeruseragent;
    }

    public void setCustomeruseragent(String customeruseragent) {
        this.customeruseragent = customeruseragent;
    }

    public List<String> getHotelcodes() {
        return hotelcodes;
    }

    public void setHotelcodes(List<String> hotelcodes) {
        this.hotelcodes = hotelcodes;
    }
}
