
package com.trip.booking.spa.legacy.travelconnect.bean.search.request;

import java.util.List;

public class SearchRequest {

    private String citycode;
    private String coutrycode;
    private String regioncode;
    private String keyword;
    private String placeid;
    private String nationality;
    private String checkindate;
    private String checkoutdate;
    private String clientguid;
    private int pageindex;
    private int pagesize;
    private String mulitstar;
    private String tripadrating;
    private String facility;
    private String landmarkids;
    private String regionids;
    private String price;
    private String orderby;
    private String searchcode;
    private boolean searchfromcache;
    private String clientcurrency;
    private boolean isdelayload;
    private boolean isspecialfilter;
    private String customersessionid;
    private String customeripaddress;
    private String customeruseragent;
    private Location location;
    private boolean issearchlist;
    private boolean isroommerge;
    private String searchguid;
    private List<String> hotelcodes;
    private List<Roomorders> roomorders;
    private List<Integer> loadModules;
    private List<String> specialhotelids;
    private List<String> clienthotelcodes;

    public String getCitycode() {
        return citycode;
    }

    public void setCitycode(String citycode) {
        this.citycode = citycode;
    }

    public String getCoutrycode() {
        return coutrycode;
    }

    public void setCoutrycode(String coutrycode) {
        this.coutrycode = coutrycode;
    }

    public String getRegioncode() {
        return regioncode;
    }

    public void setRegioncode(String regioncode) {
        this.regioncode = regioncode;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getPlaceid() {
        return placeid;
    }

    public void setPlaceid(String placeid) {
        this.placeid = placeid;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
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

    public String getClientguid() {
        return clientguid;
    }

    public void setClientguid(String clientguid) {
        this.clientguid = clientguid;
    }

    public int getPageindex() {
        return pageindex;
    }

    public void setPageindex(int pageindex) {
        this.pageindex = pageindex;
    }

    public int getPagesize() {
        return pagesize;
    }

    public void setPagesize(int pagesize) {
        this.pagesize = pagesize;
    }

    public String getMulitstar() {
        return mulitstar;
    }

    public void setMulitstar(String mulitstar) {
        this.mulitstar = mulitstar;
    }

    public String getTripadrating() {
        return tripadrating;
    }

    public void setTripadrating(String tripadrating) {
        this.tripadrating = tripadrating;
    }

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    public String getLandmarkids() {
        return landmarkids;
    }

    public void setLandmarkids(String landmarkids) {
        this.landmarkids = landmarkids;
    }

    public String getRegionids() {
        return regionids;
    }

    public void setRegionids(String regionids) {
        this.regionids = regionids;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getOrderby() {
        return orderby;
    }

    public void setOrderby(String orderby) {
        this.orderby = orderby;
    }

    public String getSearchcode() {
        return searchcode;
    }

    public void setSearchcode(String searchcode) {
        this.searchcode = searchcode;
    }

    public boolean isSearchfromcache() {
        return searchfromcache;
    }

    public void setSearchfromcache(boolean searchfromcache) {
        this.searchfromcache = searchfromcache;
    }

    public String getClientcurrency() {
        return clientcurrency;
    }

    public void setClientcurrency(String clientcurrency) {
        this.clientcurrency = clientcurrency;
    }

    public boolean isIsdelayload() {
        return isdelayload;
    }

    public void setIsdelayload(boolean isdelayload) {
        this.isdelayload = isdelayload;
    }

    public boolean isIsspecialfilter() {
        return isspecialfilter;
    }

    public void setIsspecialfilter(boolean isspecialfilter) {
        this.isspecialfilter = isspecialfilter;
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

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public boolean isIssearchlist() {
        return issearchlist;
    }

    public void setIssearchlist(boolean issearchlist) {
        this.issearchlist = issearchlist;
    }

    public boolean isIsroommerge() {
        return isroommerge;
    }

    public void setIsroommerge(boolean isroommerge) {
        this.isroommerge = isroommerge;
    }

    public String getSearchguid() {
        return searchguid;
    }

    public void setSearchguid(String searchguid) {
        this.searchguid = searchguid;
    }

    public List<String> getHotelcodes() {
        return hotelcodes;
    }

    public void setHotelcodes(List<String> hotelcodes) {
        this.hotelcodes = hotelcodes;
    }

    public List<Roomorders> getRoomorders() {
        return roomorders;
    }

    public void setRoomorders(List<Roomorders> roomorders) {
        this.roomorders = roomorders;
    }

    public List<Integer> getLoadModules() {
        return loadModules;
    }

    public void setLoadModules(List<Integer> loadModules) {
        this.loadModules = loadModules;
    }

    public List<String> getSpecialhotelids() {
        return specialhotelids;
    }

    public void setSpecialhotelids(List<String> specialhotelids) {
        this.specialhotelids = specialhotelids;
    }

    public List<String> getClienthotelcodes() {
        return clienthotelcodes;
    }

    public void setClienthotelcodes(List<String> clienthotelcodes) {
        this.clienthotelcodes = clienthotelcodes;
    }


}