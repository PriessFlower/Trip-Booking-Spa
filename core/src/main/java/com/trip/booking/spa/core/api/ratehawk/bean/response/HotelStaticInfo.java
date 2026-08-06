package com.trip.booking.spa.core.api.ratehawk.bean.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 酒店信息反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/09
 * @since : 1.0
 **/

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class HotelStaticInfo {


    private String address;
//    private List<Amenity_groups> amenity_groups;
    private String check_in_time;
    private String check_out_time;
//    private List<Description_struct> description_struct;
    private String id;
    private long hid;
    //    private List<String> images;
//    private List<Images_ext> images_ext;
    private String kind;
    private double latitude;
    private double longitude;
    private String name;
    private String phone;
    private List<Policy_struct> policy_struct;
    private String postal_code;
    private List<Room_groups> room_groups;
    private Region region;
    private int star_rating;
    private String email;
    private List<String> serp_filters;
    private boolean deleted;
    private boolean is_closed;
    private boolean is_gender_specification_required;
//    private Metapolicy_struct metapolicy_struct;
    private String metapolicy_extra_info;
//    private Facts facts;
    private List<String> payment_methods;
    private String hotel_chain;
    private String front_desk_time_start;
    private String front_desk_time_end;
//    private Keys_pickup keys_pickup;



//    public static class Amenity_groups {
//
//        private List<String> amenities;
//        private List<String> non_free_amenities;
//        private String group_name;
//    }

//    public static class Description_struct {
//
//        private List<String> paragraphs;
//        private String title;
//    }

    public static class Room_groups {

        private int room_group_id;
        private List<String> images;
        private String name;
        private List<String> room_amenities;
        private Rg_ext rg_ext;
        private Name_struct name_struct;

        public int getRoom_group_id() {
            return room_group_id;
        }

        public void setRoom_group_id(int room_group_id) {
            this.room_group_id = room_group_id;
        }

        public List<String> getImages() {
            return images;
        }

        public void setImages(List<String> images) {
            this.images = images;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getRoom_amenities() {
            return room_amenities;
        }

        public void setRoom_amenities(List<String> room_amenities) {
            this.room_amenities = room_amenities;
        }

        public Rg_ext getRg_ext() {
            return rg_ext;
        }

        public void setRg_ext(Rg_ext rg_ext) {
            this.rg_ext = rg_ext;
        }

        public Name_struct getName_struct() {
            return name_struct;
        }

        public void setName_struct(Name_struct name_struct) {
            this.name_struct = name_struct;
        }
    }

    public static class Rg_ext {

        private int _class;
        private int quality;
        private int sex;
        private int bathroom;
        private int bedding;
        private int family;
        private int capacity;
        private int club;
        private int bedrooms;
        private int balcony;
        private int floor;
        private int view;

        public int get_class() {
            return _class;
        }

        public void set_class(int _class) {
            this._class = _class;
        }

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

        public int getFloor() {
            return floor;
        }

        public void setFloor(int floor) {
            this.floor = floor;
        }

        public int getView() {
            return view;
        }

        public void setView(int view) {
            this.view = view;
        }
    }

    public static class Name_struct {

        private String bathroom;
        private String bedding_type;
        private String main_name;

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

        public String getMain_name() {
            return main_name;
        }

        public void setMain_name(String main_name) {
            this.main_name = main_name;
        }
    }

    public static class Region {

        private long id;
        private String country_code;
        private String iata;
        private String name;
        private String type;
        private String type_v2;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getCountry_code() {
            return country_code;
        }

        public void setCountry_code(String country_code) {
            this.country_code = country_code;
        }

        public String getIata() {
            return iata;
        }

        public void setIata(String iata) {
            this.iata = iata;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType_v2() {
            return type_v2;
        }

        public void setType_v2(String type_v2) {
            this.type_v2 = type_v2;
        }
    }

//    public static class Metapolicy_struct {
//
//        private List<String> internet;
//        private List<String> meal;
//        private List<String> children_meal;
//        private List<String> extra_bed;
//        private List<String> cot;
//        private List<String> pets;
//        private List<String> shuttle;
//        private List<String> parking;
//        private List<String> children;
//        private Visa visa;
//        private List<String> deposit;
//        private No_show no_show;
//        private List<String> add_fee;
//        private List<String> check_in_check_out;
//    }

//    public static class Visa {
//
//        private String visa_support;
//    }
//
//    public static class No_show {
//
//        private String availability;
//        private String time;
//        private String day_period;
//    }

//    public static class Facts {
//
//        private String floors_number;
//        private int rooms_number;
//        private String year_built;
//        private String year_renovated;
//        private Electricity electricity;
//    }
//
//    public static class Electricity {
//
//        private List<Integer> frequency;
//        private List<Integer> voltage;
//        private List<String> sockets;
//    }
//
//    public static class Keys_pickup {
//
//        private String type;
//        private String phone;
//        private String email;
//        private String apartment_office_address;
//        private String apartment_extra_information;
//        private boolean is_contactless;
//    }

    public static class Policy_struct {

        private List<String> paragraphs;
        private String title;
        public void setParagraphs(List<String> paragraphs) {
            this.paragraphs = paragraphs;
        }
        public List<String> getParagraphs() {
            return paragraphs;
        }

        public void setTitle(String title) {
            this.title = title;
        }
        public String getTitle() {
            return title;
        }

    }
}
