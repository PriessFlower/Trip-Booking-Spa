package com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.single;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;

import java.util.List;

public class SingleHotelResponse implements BaseResponse {

    /**
     * result : {"return_status":{"success":"true","exception":""}}
     * hotel_id : 116729
     * hotel_data : {"name":"总部广场莫里斯敦凯悦酒店","name_en":"Hyatt Morristown at Headquarters Plaza","star":4,"pic":"https://s2.imgs.aichotels.net.cn/public/hotels/1184/116729/supplier/0.jpg","intro":"<p><strong>酒店设施<br/>酒店诚挚欢迎客人入住在一共有256间的客房之中。酒店提供保险柜。通过无线网络客人可在公共区域便利上网。餐饮设施有：餐厅、用餐区、咖啡厅和酒吧。旅游纪念品可在纪念品商店选购。此外尚有其他便利设施，比如报刊店。驾车前来的客人，可将车辆停放于酒店的停车场。其他贴心服务还包含接送服务、翻译服务和送餐服务。<\/strong><\/p><p><strong><strong>客房设施<br/>房间里备有空调和浴室。房间备有一张双人床和一张沙发床。此外亦提供保险柜等设备。冰箱和煮茶/咖啡机让房客在住宿期间倍感舒适方便。互联网接口、电话、电视机和无线网络使房间设备更加完善。浴缸等设施为浴室更添舒适。吹风机和浴袍能满足日常所需。酒店提供家庭房和禁烟房。<\/strong><\/strong><\/p><p><strong><strong><strong>运动/休闲<br/>酒店拥有游泳池和室内游泳池。需要健身的旅客有多种休闲项目可选择，比如健身中心、SPA、桑拿和按摩理疗。<\/strong><\/strong><\/strong><\/p><p><strong><strong><strong><strong>餐饮<br/>特别提供早餐、午餐和晚餐。<\/strong><\/strong><\/strong><\/strong><\/p><p><\/p><p><\/p><p><\/p>","intro_en":"<p><strong>Facilities<br/>Guests are welcomed at the hotel, which has a total of 256 rooms. Amenities include a safe. Wireless internet access is available to travellers in the public areas. Among the culinary options available at the accommodation are a restaurant, a dining area, a caf&amp;eacute; and a bar. Guests can buy souvenirs at the gift shop. Additional facilities at the establishment include a newspaper stand. Those arriving in their own vehicles can leave them in the car park of the hotel. Available services and facilities include a transfer service, translation services and room service.<\/strong><\/p><p><strong><strong>Rooms<br/>Each of the rooms is appointed with air conditioning and a bathroom. The rooms have a double bed and a sofa bed. There is also a safe. A fridge and a tea/coffee station ensure a comfortable stay. Other features include internet access, a telephone, a TV and WiFi. The bathroom offers convenient facilities including a bathtub. A hairdryer and bathrobes are provided for everyday use. The accommodation offers family rooms and non-smoking rooms.<\/strong><\/strong><\/p><p><strong><strong><strong>Sports/Entertainment<br/>The establishment features a pool and an indoor pool. Active travellers can choose from a range of leisure activities, including a gym, a spa, a sauna and massage treatments.<\/strong><\/strong><\/strong><\/p><p><strong><strong><strong><strong>Meals<br/>The accommodation offers breakfast, lunch and dinner.<\/strong><\/strong><\/strong><\/strong><\/p><p><\/p><p><\/p><p><\/p>","address":"3 Speedwell Avenue","address_en":"3 Speedwell Avenue","currency":"USD","country_short":"US","country_name":"美国","country_name_en":"United States","country_id":233,"city":"莫瑞斯镇","city_en":"Morristown","city_id":"8771","state_name":"New Jersey","metro":"0","postcode":"07960","phone":"+19736471234","fax":"+19732927562","latitude":"40.80005","longitude":"-74.481124","flag":2,"is_bfc_free":-1}
     * otherimages : ["https://s5.imgs.aichotels.net.cn/public/hotels/1184/116729/supplier/0.jpg ","https://s5.imgs.aichotels.net.cn/public/hotels/1184/116729/supplier/12.jpg ","https://s5.imgs.aichotels.net.cn/public/hotels/1184/116729/supplier/0.jpg ","https://s2.imgs.aichotels.net.cn/public/hotels/1184/116729/supplier/0.jpg"]
     */

    private ResultBean result;
    private int hotel_id;
    private HotelDataBean hotel_data;
    private List<String> otherimages;

    public ResultBean getResult() {
        return result;
    }

    public void setResult(ResultBean result) {
        this.result = result;
    }

    public int getHotel_id() {
        return hotel_id;
    }

    public void setHotel_id(int hotel_id) {
        this.hotel_id = hotel_id;
    }

    public HotelDataBean getHotel_data() {
        return hotel_data;
    }

    public void setHotel_data(HotelDataBean hotel_data) {
        this.hotel_data = hotel_data;
    }

    public List<String> getOtherimages() {
        return otherimages;
    }

    public void setOtherimages(List<String> otherimages) {
        this.otherimages = otherimages;
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

    public static class HotelDataBean {
        /**
         * name : 总部广场莫里斯敦凯悦酒店
         * name_en : Hyatt Morristown at Headquarters Plaza
         * star : 4
         * pic : https://s2.imgs.aichotels.net.cn/public/hotels/1184/116729/supplier/0.jpg
         * intro : <p><strong>酒店设施<br/>酒店诚挚欢迎客人入住在一共有256间的客房之中。酒店提供保险柜。通过无线网络客人可在公共区域便利上网。餐饮设施有：餐厅、用餐区、咖啡厅和酒吧。旅游纪念品可在纪念品商店选购。此外尚有其他便利设施，比如报刊店。驾车前来的客人，可将车辆停放于酒店的停车场。其他贴心服务还包含接送服务、翻译服务和送餐服务。</strong></p><p><strong><strong>客房设施<br/>房间里备有空调和浴室。房间备有一张双人床和一张沙发床。此外亦提供保险柜等设备。冰箱和煮茶/咖啡机让房客在住宿期间倍感舒适方便。互联网接口、电话、电视机和无线网络使房间设备更加完善。浴缸等设施为浴室更添舒适。吹风机和浴袍能满足日常所需。酒店提供家庭房和禁烟房。</strong></strong></p><p><strong><strong><strong>运动/休闲<br/>酒店拥有游泳池和室内游泳池。需要健身的旅客有多种休闲项目可选择，比如健身中心、SPA、桑拿和按摩理疗。</strong></strong></strong></p><p><strong><strong><strong><strong>餐饮<br/>特别提供早餐、午餐和晚餐。</strong></strong></strong></strong></p><p></p><p></p><p></p>
         * intro_en : <p><strong>Facilities<br/>Guests are welcomed at the hotel, which has a total of 256 rooms. Amenities include a safe. Wireless internet access is available to travellers in the public areas. Among the culinary options available at the accommodation are a restaurant, a dining area, a caf&amp;eacute; and a bar. Guests can buy souvenirs at the gift shop. Additional facilities at the establishment include a newspaper stand. Those arriving in their own vehicles can leave them in the car park of the hotel. Available services and facilities include a transfer service, translation services and room service.</strong></p><p><strong><strong>Rooms<br/>Each of the rooms is appointed with air conditioning and a bathroom. The rooms have a double bed and a sofa bed. There is also a safe. A fridge and a tea/coffee station ensure a comfortable stay. Other features include internet access, a telephone, a TV and WiFi. The bathroom offers convenient facilities including a bathtub. A hairdryer and bathrobes are provided for everyday use. The accommodation offers family rooms and non-smoking rooms.</strong></strong></p><p><strong><strong><strong>Sports/Entertainment<br/>The establishment features a pool and an indoor pool. Active travellers can choose from a range of leisure activities, including a gym, a spa, a sauna and massage treatments.</strong></strong></strong></p><p><strong><strong><strong><strong>Meals<br/>The accommodation offers breakfast, lunch and dinner.</strong></strong></strong></strong></p><p></p><p></p><p></p>
         * address : 3 Speedwell Avenue
         * address_en : 3 Speedwell Avenue
         * currency : USD
         * country_short : US
         * country_name : 美国
         * country_name_en : United States
         * country_id : 233
         * city : 莫瑞斯镇
         * city_en : Morristown
         * city_id : 8771
         * state_name : New Jersey
         * metro : 0
         * postcode : 07960
         * phone : +19736471234
         * fax : +19732927562
         * latitude : 40.80005
         * longitude : -74.481124
         * flag : 2
         * is_bfc_free : -1
         */

        private String name;
        private String name_en;
        private int star;
        private String pic;
        private String intro;
        private String intro_en;
        private String address;
        private String address_en;
        private String currency;
        private String country_short;
        private String country_name;
        private String country_name_en;
        private int country_id;
        private String city;
        private String city_en;
        private String city_id;
        private String state_name;
        private String metro;
        private String postcode;
        private String phone;
        private String fax;
        private String latitude;
        private String longitude;
        private int flag;
        private int is_bfc_free;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName_en() {
            return name_en;
        }

        public void setName_en(String name_en) {
            this.name_en = name_en;
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

        public String getIntro() {
            return intro;
        }

        public void setIntro(String intro) {
            this.intro = intro;
        }

        public String getIntro_en() {
            return intro_en;
        }

        public void setIntro_en(String intro_en) {
            this.intro_en = intro_en;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getAddress_en() {
            return address_en;
        }

        public void setAddress_en(String address_en) {
            this.address_en = address_en;
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

        public String getCountry_name() {
            return country_name;
        }

        public void setCountry_name(String country_name) {
            this.country_name = country_name;
        }

        public String getCountry_name_en() {
            return country_name_en;
        }

        public void setCountry_name_en(String country_name_en) {
            this.country_name_en = country_name_en;
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

        public String getCity_en() {
            return city_en;
        }

        public void setCity_en(String city_en) {
            this.city_en = city_en;
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

        public String getMetro() {
            return metro;
        }

        public void setMetro(String metro) {
            this.metro = metro;
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

        public int getFlag() {
            return flag;
        }

        public void setFlag(int flag) {
            this.flag = flag;
        }

        public int getIs_bfc_free() {
            return is_bfc_free;
        }

        public void setIs_bfc_free(int is_bfc_free) {
            this.is_bfc_free = is_bfc_free;
        }
    }
}
