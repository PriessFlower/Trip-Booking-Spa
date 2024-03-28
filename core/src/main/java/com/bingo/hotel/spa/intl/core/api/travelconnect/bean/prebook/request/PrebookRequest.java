package com.bingo.hotel.spa.intl.core.api.travelconnect.bean.prebook.request;

import lombok.Builder;

import java.util.List;

public class PrebookRequest {
    private String citycode;
    private String hotelcode;
    private String checkindate;
    private String checkoutdate;
    private String nationality;
    private String cachetoken;
    private String searchsource;
    private String clientcurrency;
    private String customersessionid;
    private String customeripaddress;
    private String customeruseragent;
    private List<RoomsBean> rooms;

    public String getCitycode() {
        return citycode;
    }

    public void setCitycode(String citycode) {
        this.citycode = citycode;
    }

    public String getHotelcode() {
        return hotelcode;
    }

    public void setHotelcode(String hotelcode) {
        this.hotelcode = hotelcode;
    }

    public String getCheckindate() {
        return checkindate;
    }

    public void setCheckindate(String checkindate) {
        this.checkindate = checkindate;
    }

    public String getCheckoutdate() {
        return checkoutdate;
    }

    public void setCheckoutdate(String checkoutdate) {
        this.checkoutdate = checkoutdate;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getCachetoken() {
        return cachetoken;
    }

    public void setCachetoken(String cachetoken) {
        this.cachetoken = cachetoken;
    }

    public String getSearchsource() {
        return searchsource;
    }

    public void setSearchsource(String searchsource) {
        this.searchsource = searchsource;
    }

    public String getClientcurrency() {
        return clientcurrency;
    }

    public void setClientcurrency(String clientcurrency) {
        this.clientcurrency = clientcurrency;
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

    public List<RoomsBean> getRooms() {
        return rooms;
    }

    public void setRooms(List<RoomsBean> rooms) {
        this.rooms = rooms;
    }

    public static class RoomsBean {
        /**
         * plansid : sample string 1
         * adultcount : 2
         * infantcount : 3
         * childages : [1,2]
         * optionids : ["sample string 1","sample string 2"]
         */

        private String plansid;
        private int adultcount;
        private int infantcount;
        private List<Integer> childages;
        private List<String> optionids;

        public String getPlansid() {
            return plansid;
        }

        public void setPlansid(String plansid) {
            this.plansid = plansid;
        }

        public int getAdultcount() {
            return adultcount;
        }

        public void setAdultcount(int adultcount) {
            this.adultcount = adultcount;
        }

        public int getInfantcount() {
            return infantcount;
        }

        public void setInfantcount(int infantcount) {
            this.infantcount = infantcount;
        }

        public List<Integer> getChildages() {
            return childages;
        }

        public void setChildages(List<Integer> childages) {
            this.childages = childages;
        }

        public List<String> getOptionids() {
            return optionids;
        }

        public void setOptionids(List<String> optionids) {
            this.optionids = optionids;
        }
    }
}
