package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;

import java.util.List;
import java.util.Map;

/**
 * expedia酒店基础静态信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/12
 * @since : 1.0
 **/
public class HotelStaticInfo implements BaseResponse {

    private String property_id;
    private String name;
    private Address address;
    private Ratings ratings;
    private Location location;
    private String phone;
    private Map<String, String> checkin;
    private Checkout checkout;
    private Fees fees;
    private Policies policies;
    private Attributes attributes;
    private Map<String, BasicInfo> amenities;
    private List<Images> images;
    private Map<String, Room> rooms;
    private Dates dates;
    private HotelDescription descriptions;
    private Map<String, BasicInfo> themes;
    private BasicInfo chain;
    private BasicInfo brand;
    private Map<String, BasicInfo> spoken_languages;
    private boolean multi_unit;
    private boolean payment_registration_recommended;
    private String supply_source;

    public String getProperty_id() {
        return property_id;
    }

    public void setProperty_id(String property_id) {
        this.property_id = property_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Ratings getRatings() {
        return ratings;
    }

    public void setRatings(Ratings ratings) {
        this.ratings = ratings;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Map<String, String> getCheckin() {
        return checkin;
    }

    public void setCheckin(Map<String, String> checkin) {
        this.checkin = checkin;
    }

    public Checkout getCheckout() {
        return checkout;
    }

    public void setCheckout(Checkout checkout) {
        this.checkout = checkout;
    }

    public Fees getFees() {
        return fees;
    }

    public void setFees(Fees fees) {
        this.fees = fees;
    }

    public Policies getPolicies() {
        return policies;
    }

    public void setPolicies(Policies policies) {
        this.policies = policies;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    public Map<String, BasicInfo> getAmenities() {
        return amenities;
    }

    public void setAmenities(Map<String, BasicInfo> amenities) {
        this.amenities = amenities;
    }

    public List<Images> getImages() {
        return images;
    }

    public void setImages(List<Images> images) {
        this.images = images;
    }

    public Map<String, Room> getRooms() {
        return rooms;
    }

    public void setRooms(Map<String, Room> rooms) {
        this.rooms = rooms;
    }

    public Dates getDates() {
        return dates;
    }

    public void setDates(Dates dates) {
        this.dates = dates;
    }

    public HotelDescription getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(HotelDescription descriptions) {
        this.descriptions = descriptions;
    }

    public Map<String, BasicInfo> getThemes() {
        return themes;
    }

    public void setThemes(Map<String, BasicInfo> themes) {
        this.themes = themes;
    }

    public BasicInfo getChain() {
        return chain;
    }

    public void setChain(BasicInfo chain) {
        this.chain = chain;
    }

    public BasicInfo getBrand() {
        return brand;
    }

    public void setBrand(BasicInfo brand) {
        this.brand = brand;
    }

    public Map<String, BasicInfo> getSpoken_languages() {
        return spoken_languages;
    }

    public void setSpoken_languages(Map<String, BasicInfo> spoken_languages) {
        this.spoken_languages = spoken_languages;
    }

    public boolean isMulti_unit() {
        return multi_unit;
    }

    public void setMulti_unit(boolean multi_unit) {
        this.multi_unit = multi_unit;
    }

    public boolean isPayment_registration_recommended() {
        return payment_registration_recommended;
    }

    public void setPayment_registration_recommended(boolean payment_registration_recommended) {
        this.payment_registration_recommended = payment_registration_recommended;
    }

    public String getSupply_source() {
        return supply_source;
    }

    public void setSupply_source(String supply_source) {
        this.supply_source = supply_source;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }


    public static class Address {
        private String line_1;
        private String line_2;
        private String city;
        private String state_province_name;
        private String postal_code;
        private String country_code;
        private boolean obfuscation_required;

        public String getLine_1() {
            return line_1;
        }

        public void setLine_1(String line_1) {
            this.line_1 = line_1;
        }

        public String getLine_2() {
            return line_2;
        }

        public void setLine_2(String line_2) {
            this.line_2 = line_2;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState_province_name() {
            return state_province_name;
        }

        public void setState_province_name(String state_province_name) {
            this.state_province_name = state_province_name;
        }

        public String getPostal_code() {
            return postal_code;
        }

        public void setPostal_code(String postal_code) {
            this.postal_code = postal_code;
        }

        public String getCountry_code() {
            return country_code;
        }

        public void setCountry_code(String country_code) {
            this.country_code = country_code;
        }

        public boolean isObfuscation_required() {
            return obfuscation_required;
        }

        public void setObfuscation_required(boolean obfuscation_required) {
            this.obfuscation_required = obfuscation_required;
        }
    }

    public static class Ratings {

        private Property property;
        private Guest guest;

        public Property getProperty() {
            return property;
        }

        public void setProperty(Property property) {
            this.property = property;
        }

        public Guest getGuest() {
            return guest;
        }

        public void setGuest(Guest guest) {
            this.guest = guest;
        }
    }

    public static class Property {

        private String rating;
        private String type;

        public void setRating(String rating) {
            this.rating = rating;
        }

        public String getRating() {
            return rating;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

    }

    public static class Guest {

        private int count;
        private String overall;
        private String cleanliness;
        private String service;
        private String comfort;
        private String condition;
        private String amenities;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getOverall() {
            return overall;
        }

        public void setOverall(String overall) {
            this.overall = overall;
        }

        public String getCleanliness() {
            return cleanliness;
        }

        public void setCleanliness(String cleanliness) {
            this.cleanliness = cleanliness;
        }

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public String getComfort() {
            return comfort;
        }

        public void setComfort(String comfort) {
            this.comfort = comfort;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public String getAmenities() {
            return amenities;
        }

        public void setAmenities(String amenities) {
            this.amenities = amenities;
        }
    }

    public static class Location {
        private Coordinates coordinates;

        public Coordinates getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(Coordinates coordinates) {
            this.coordinates = coordinates;
        }
    }

    public static class Coordinates {

        private double latitude;
        private double longitude;

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }

//    public static class Checkin {
//        private String 24_hour;
//        private String begin_time;
//        private String end_time;
//        private String instructions;
//        private String special_instructions;
//        private int min_age;
//    }

    public static class Checkout {

        private String time;

        public void setTime(String time) {
            this.time = time;
        }

        public String getTime() {
            return time;
        }

    }

    public static class Fees {

        private String mandatory;
        private String optional;

        public void setMandatory(String mandatory) {
            this.mandatory = mandatory;
        }

        public String getMandatory() {
            return mandatory;
        }

        public void setOptional(String optional) {
            this.optional = optional;
        }

        public String getOptional() {
            return optional;
        }

    }


    public static class Attributes {
        private Map<String, BasicInfo> pets;
        private Map<String, BasicInfo> general;

        public Map<String, BasicInfo> getPets() {
            return pets;
        }

        public void setPets(Map<String, BasicInfo> pets) {
            this.pets = pets;
        }

        public Map<String, BasicInfo> getGeneral() {
            return general;
        }

        public void setGeneral(Map<String, BasicInfo> general) {
            this.general = general;
        }
    }

    public static class BasicInfo {
        private String id;
        private String name;

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
    }

    public static class UrlInfo {
        private String method;
        private String href;

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getHref() {
            return href;
        }

        public void setHref(String href) {
            this.href = href;
        }
    }

    public static class Images {

        private boolean hero_image;
        private int category;
        private Map<String, UrlInfo> links;
        private String caption;

        public void setHero_image(boolean hero_image) {
            this.hero_image = hero_image;
        }

        public boolean getHero_image() {
            return hero_image;
        }

        public void setCategory(int category) {
            this.category = category;
        }

        public int getCategory() {
            return category;
        }

        public Map<String, UrlInfo> getLinks() {
            return links;
        }

        public void setLinks(Map<String, UrlInfo> links) {
            this.links = links;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public String getCaption() {
            return caption;
        }
    }

    public static class Room {
        private String id;
        private String name;
        private RoomDescription descriptions;
        private Map<String, BasicInfo> amenities;
        private List<Images> images;
        private Map<String, BedGroup> bed_groups;
        private Area area;
        private Occupancy occupancy;

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

        public RoomDescription getDescriptions() {
            return descriptions;
        }

        public void setDescriptions(RoomDescription descriptions) {
            this.descriptions = descriptions;
        }

        public Map<String, BasicInfo> getAmenities() {
            return amenities;
        }

        public void setAmenities(Map<String, BasicInfo> amenities) {
            this.amenities = amenities;
        }

        public List<Images> getImages() {
            return images;
        }

        public void setImages(List<Images> images) {
            this.images = images;
        }

        public Map<String, BedGroup> getBed_groups() {
            return bed_groups;
        }

        public void setBed_groups(Map<String, BedGroup> bed_groups) {
            this.bed_groups = bed_groups;
        }

        public Area getArea() {
            return area;
        }

        public void setArea(Area area) {
            this.area = area;
        }

        public Occupancy getOccupancy() {
            return occupancy;
        }

        public void setOccupancy(Occupancy occupancy) {
            this.occupancy = occupancy;
        }
    }

    public static class Dates {

        private String added;
        private String updated;

        public String getAdded() {
            return added;
        }

        public void setAdded(String added) {
            this.added = added;
        }

        public String getUpdated() {
            return updated;
        }

        public void setUpdated(String updated) {
            this.updated = updated;
        }
    }

    public static class HotelDescription {

        private String amenities;
        private String dining;
        private String renovations;
        private String national_ratings;
        private String business_amenities;
        private String rooms;
        private String attractions;
        private String location;
        private String headline;
        private String general;

        public String getAmenities() {
            return amenities;
        }

        public void setAmenities(String amenities) {
            this.amenities = amenities;
        }

        public String getDining() {
            return dining;
        }

        public void setDining(String dining) {
            this.dining = dining;
        }

        public String getRenovations() {
            return renovations;
        }

        public void setRenovations(String renovations) {
            this.renovations = renovations;
        }

        public String getNational_ratings() {
            return national_ratings;
        }

        public void setNational_ratings(String national_ratings) {
            this.national_ratings = national_ratings;
        }

        public String getBusiness_amenities() {
            return business_amenities;
        }

        public void setBusiness_amenities(String business_amenities) {
            this.business_amenities = business_amenities;
        }

        public String getRooms() {
            return rooms;
        }

        public void setRooms(String rooms) {
            this.rooms = rooms;
        }

        public String getAttractions() {
            return attractions;
        }

        public void setAttractions(String attractions) {
            this.attractions = attractions;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getHeadline() {
            return headline;
        }

        public void setHeadline(String headline) {
            this.headline = headline;
        }

        public String getGeneral() {
            return general;
        }

        public void setGeneral(String general) {
            this.general = general;
        }
    }

    public static class Policies {

        private String know_before_you_go;

        public void setKnow_before_you_go(String know_before_you_go) {
            this.know_before_you_go = know_before_you_go;
        }

        public String getKnow_before_you_go() {
            return know_before_you_go;
        }
    }

    public static class RoomDescription {
        private String overview;

        public void setOverview(String overview) {
            this.overview = overview;
        }

        public String getOverview() {
            return overview;
        }
    }


    public static class BedGroup {

        private String id;
        private String description;
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

        public void setConfiguration(List<Configuration> configuration) {
            this.configuration = configuration;
        }

        public List<Configuration> getConfiguration() {
            return configuration;
        }

    }

    public static class Configuration {

        private int quantity;
        private String size;
        private String type;

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public String getSize() {
            return size;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

    }

    public static class Area {

        private int square_meters;
        private int square_feet;

        public void setSquare_meters(int square_meters) {
            this.square_meters = square_meters;
        }

        public int getSquare_meters() {
            return square_meters;
        }

        public void setSquare_feet(int square_feet) {
            this.square_feet = square_feet;
        }

        public int getSquare_feet() {
            return square_feet;
        }

    }

    public static class Occupancy {

        private Max_allowed max_allowed;
        private Age_categories age_categories;

        public void setMax_allowed(Max_allowed max_allowed) {
            this.max_allowed = max_allowed;
        }

        public Max_allowed getMax_allowed() {
            return max_allowed;
        }

        public void setAge_categories(Age_categories age_categories) {
            this.age_categories = age_categories;
        }

        public Age_categories getAge_categories() {
            return age_categories;
        }

    }

    public static class Max_allowed {

        private int total;
        private int children;
        private int adults;

        public void setTotal(int total) {
            this.total = total;
        }

        public int getTotal() {
            return total;
        }

        public void setChildren(int children) {
            this.children = children;
        }

        public int getChildren() {
            return children;
        }

        public void setAdults(int adults) {
            this.adults = adults;
        }

        public int getAdults() {
            return adults;
        }

    }

    public static class Age_categories {

        private AgeLimit ChildAgeA;
        private AgeLimit Adult;
        private AgeLimit Infant;

        public AgeLimit getChildAgeA() {
            return ChildAgeA;
        }

        public void setChildAgeA(AgeLimit childAgeA) {
            ChildAgeA = childAgeA;
        }

        public AgeLimit getAdult() {
            return Adult;
        }

        public void setAdult(AgeLimit adult) {
            Adult = adult;
        }

        public AgeLimit getInfant() {
            return Infant;
        }

        public void setInfant(AgeLimit infant) {
            Infant = infant;
        }
    }

    public static class AgeLimit {

        private String name;
        private int minimum_age;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setMinimum_age(int minimum_age) {
            this.minimum_age = minimum_age;
        }

        public int getMinimum_age() {
            return minimum_age;
        }

    }
}
