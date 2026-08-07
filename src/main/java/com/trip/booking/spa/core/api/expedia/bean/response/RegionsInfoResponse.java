package com.trip.booking.spa.core.api.expedia.bean.response;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;

import java.util.List;

/**
 * 地域静态信息反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public class RegionsInfoResponse implements BaseResponse {

    private String id;
    private String type;
    private String name;
    private String name_full;
    private String country_code;
    private Coordinates coordinates;
    private Associations associations;
    private List<Ancestors> ancestors;
    private Descendants descendants;
    private List<String> property_ids;
    private List<String> categories;
    private List<String> tags;
    private List<HotelId> hotelIds;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName_full() {
        return name_full;
    }

    public void setName_full(String name_full) {
        this.name_full = name_full;
    }

    public String getCountry_code() {
        return country_code;
    }

    public void setCountry_code(String country_code) {
        this.country_code = country_code;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }

    public Associations getAssociations() {
        return associations;
    }

    public void setAssociations(Associations associations) {
        this.associations = associations;
    }

    public List<Ancestors> getAncestors() {
        return ancestors;
    }

    public void setAncestors(List<Ancestors> ancestors) {
        this.ancestors = ancestors;
    }

    public Descendants getDescendants() {
        return descendants;
    }

    public void setDescendants(Descendants descendants) {
        this.descendants = descendants;
    }

    public List<String> getProperty_ids() {
        return property_ids;
    }

    public void setProperty_ids(List<String> property_ids) {
        this.property_ids = property_ids;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<HotelId> getHotelIds() {
        return hotelIds;
    }

    public void setHotelIds(List<HotelId> hotelIds) {
        this.hotelIds = hotelIds;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class Coordinates {
        private String center_longitude;
        private String center_latitude;

        public String getCenter_longitude() {
            return center_longitude;
        }

        public void setCenter_longitude(String center_longitude) {
            this.center_longitude = center_longitude;
        }

        public String getCenter_latitude() {
            return center_latitude;
        }

        public void setCenter_latitude(String center_latitude) {
            this.center_latitude = center_latitude;
        }
    }

    public static class Descendants {
        private List<String> country;
        private List<String> province_state;
        private List<String> city;
        /**
         * 旧链路缺失的两种地区类型：multi_city_vicinity（都会区，如"曼谷及周边"）、
         * high_level_region（大区）。不解析它们会漏掉曼谷等都会区城市（旧系统缺陷，迁移时补齐）。
         */
        private List<String> multi_city_vicinity;
        private List<String> high_level_region;

        public List<String> getMulti_city_vicinity() { return multi_city_vicinity; }

        public void setMulti_city_vicinity(List<String> multi_city_vicinity) { this.multi_city_vicinity = multi_city_vicinity; }

        public List<String> getHigh_level_region() { return high_level_region; }

        public void setHigh_level_region(List<String> high_level_region) { this.high_level_region = high_level_region; }

        public List<String> getCountry() {
            return country;
        }

        public void setCountry(List<String> country) {
            this.country = country;
        }

        public List<String> getProvince_state() {
            return province_state;
        }

        public void setProvince_state(List<String> province_state) {
            this.province_state = province_state;
        }

        public List<String> getCity() {
            return city;
        }

        public void setCity(List<String> city) {
            this.city = city;
        }
    }

    public static class Associations {

        private List<String> point_of_interest;

        public void setPoint_of_interest(List<String> point_of_interest) {
            this.point_of_interest = point_of_interest;
        }

        public List<String> getPoint_of_interest() {
            return point_of_interest;
        }

    }

    public static class Ancestors {

        private String id;
        private String type;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

    }

    public static class HotelId {

        private String id;

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

    }

}
