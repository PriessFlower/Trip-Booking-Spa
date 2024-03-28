package com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.list;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;

import java.util.List;

public class HotelListResponse implements BaseResponse {

    private ResultBean result;
    private List<HotelinfoListBean> hotelinfo_list;

    public ResultBean getResult() {
        return result;
    }

    public void setResult(ResultBean result) {
        this.result = result;
    }

    public List<HotelinfoListBean> getHotelinfo_list() {
        return hotelinfo_list;
    }

    public void setHotelinfo_list(List<HotelinfoListBean> hotelinfo_list) {
        this.hotelinfo_list = hotelinfo_list;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class ResultBean {
        /**
         * return_status : {"success":"true","exception":""}
         */

        private ReturnStatusBean return_status;

        public ReturnStatusBean getReturn_status() {
            return return_status;
        }

        public void setReturn_status(ReturnStatusBean return_status) {
            this.return_status = return_status;
        }

        public static class ReturnStatusBean {
            /**
             * success : true
             * exception :
             */

            private String success;
            private String exception;

            public String getSuccess() {
                return success;
            }

            public void setSuccess(String success) {
                this.success = success;
            }

            public String getException() {
                return exception;
            }

            public void setException(String exception) {
                this.exception = exception;
            }
        }
    }

    public static class HotelinfoListBean {
        /**
         * hotel_id : 116228
         * star : 4
         * pic : https://img.hotels-content.com/public/hotels/1184/116228/supplier/4.jpg
         * address :  101 Harborside Drive Boston Massachusetts 02128
         * currency : USD
         * country_short : US
         * country_id : 233
         * city : Boston
         * city_id : 660
         * state_name : Massachusetts
         * postcode : 02128
         * phone : +16175681234
         * fax : +16175686080
         * latitude : 42.35910006493
         * longitude : -71.027150920238
         */

        private int hotel_id;
        private int star;
        private String pic;
        private String address;
        private String currency;
        private String country_short;
        private int country_id;
        private String city;
        private String city_id;
        private String state_name;
        private String postcode;
        private String phone;
        private String fax;
        private String latitude;
        private String longitude;

        public int getHotel_id() {
            return hotel_id;
        }

        public void setHotel_id(int hotel_id) {
            this.hotel_id = hotel_id;
        }

        public int getStar() {
            return star;
        }

        public void setStar(int star) {
            this.star = star;
        }

        public String getPic() {
            return pic;
        }

        public void setPic(String pic) {
            this.pic = pic;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getCountry_short() {
            return country_short;
        }

        public void setCountry_short(String country_short) {
            this.country_short = country_short;
        }

        public int getCountry_id() {
            return country_id;
        }

        public void setCountry_id(int country_id) {
            this.country_id = country_id;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCity_id() {
            return city_id;
        }

        public void setCity_id(String city_id) {
            this.city_id = city_id;
        }

        public String getState_name() {
            return state_name;
        }

        public void setState_name(String state_name) {
            this.state_name = state_name;
        }

        public String getPostcode() {
            return postcode;
        }

        public void setPostcode(String postcode) {
            this.postcode = postcode;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getFax() {
            return fax;
        }

        public void setFax(String fax) {
            this.fax = fax;
        }

        public String getLatitude() {
            return latitude;
        }

        public void setLatitude(String latitude) {
            this.latitude = latitude;
        }

        public String getLongitude() {
            return longitude;
        }

        public void setLongitude(String longitude) {
            this.longitude = longitude;
        }
    }
}
