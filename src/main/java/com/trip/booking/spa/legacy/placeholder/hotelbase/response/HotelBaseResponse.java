/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelbase.response;

import java.io.Serializable;
import java.util.Date;

public class HotelBaseResponse
implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String hotelId;
    private String supplierHotelId;
    private String hotelName;
    private String hotelNameCN;
    private String telephone;
    private String address;
    private String addressCN;
    private String postCode;
    private String cityId;
    private String cityName;
    private String cityNameCN;
    private String stateName;
    private String countryId;
    private String bannerUrl;
    private String fax;
    private String longitude;
    private String latitude;
    private String expediaHotelId;
    private Boolean status;
    private Date createTime;
    private Date updateTime;
    private Boolean del;
    private String operator;
    private String countryCode;
    private String countryName;
    private String countryNameCn;

    public Long getId() {
        return this.id;
    }

    public String getHotelId() {
        return this.hotelId;
    }

    public String getSupplierHotelId() {
        return this.supplierHotelId;
    }

    public String getHotelName() {
        return this.hotelName;
    }

    public String getHotelNameCN() {
        return this.hotelNameCN;
    }

    public String getTelephone() {
        return this.telephone;
    }

    public String getAddress() {
        return this.address;
    }

    public String getAddressCN() {
        return this.addressCN;
    }

    public String getPostCode() {
        return this.postCode;
    }

    public String getCityId() {
        return this.cityId;
    }

    public String getCityName() {
        return this.cityName;
    }

    public String getCityNameCN() {
        return this.cityNameCN;
    }

    public String getStateName() {
        return this.stateName;
    }

    public String getCountryId() {
        return this.countryId;
    }

    public String getBannerUrl() {
        return this.bannerUrl;
    }

    public String getFax() {
        return this.fax;
    }

    public String getLongitude() {
        return this.longitude;
    }

    public String getLatitude() {
        return this.latitude;
    }

    public String getExpediaHotelId() {
        return this.expediaHotelId;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public Date getCreateTime() {
        return this.createTime;
    }

    public Date getUpdateTime() {
        return this.updateTime;
    }

    public Boolean getDel() {
        return this.del;
    }

    public String getOperator() {
        return this.operator;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public String getCountryName() {
        return this.countryName;
    }

    public String getCountryNameCn() {
        return this.countryNameCn;
    }

    public HotelBaseResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public HotelBaseResponse setHotelId(String hotelId) {
        this.hotelId = hotelId;
        return this;
    }

    public HotelBaseResponse setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
        return this;
    }

    public HotelBaseResponse setHotelName(String hotelName) {
        this.hotelName = hotelName;
        return this;
    }

    public HotelBaseResponse setHotelNameCN(String hotelNameCN) {
        this.hotelNameCN = hotelNameCN;
        return this;
    }

    public HotelBaseResponse setTelephone(String telephone) {
        this.telephone = telephone;
        return this;
    }

    public HotelBaseResponse setAddress(String address) {
        this.address = address;
        return this;
    }

    public HotelBaseResponse setAddressCN(String addressCN) {
        this.addressCN = addressCN;
        return this;
    }

    public HotelBaseResponse setPostCode(String postCode) {
        this.postCode = postCode;
        return this;
    }

    public HotelBaseResponse setCityId(String cityId) {
        this.cityId = cityId;
        return this;
    }

    public HotelBaseResponse setCityName(String cityName) {
        this.cityName = cityName;
        return this;
    }

    public HotelBaseResponse setCityNameCN(String cityNameCN) {
        this.cityNameCN = cityNameCN;
        return this;
    }

    public HotelBaseResponse setStateName(String stateName) {
        this.stateName = stateName;
        return this;
    }

    public HotelBaseResponse setCountryId(String countryId) {
        this.countryId = countryId;
        return this;
    }

    public HotelBaseResponse setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
        return this;
    }

    public HotelBaseResponse setFax(String fax) {
        this.fax = fax;
        return this;
    }

    public HotelBaseResponse setLongitude(String longitude) {
        this.longitude = longitude;
        return this;
    }

    public HotelBaseResponse setLatitude(String latitude) {
        this.latitude = latitude;
        return this;
    }

    public HotelBaseResponse setExpediaHotelId(String expediaHotelId) {
        this.expediaHotelId = expediaHotelId;
        return this;
    }

    public HotelBaseResponse setStatus(Boolean status) {
        this.status = status;
        return this;
    }

    public HotelBaseResponse setCreateTime(Date createTime) {
        this.createTime = createTime;
        return this;
    }

    public HotelBaseResponse setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    public HotelBaseResponse setDel(Boolean del) {
        this.del = del;
        return this;
    }

    public HotelBaseResponse setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    public HotelBaseResponse setCountryCode(String countryCode) {
        this.countryCode = countryCode;
        return this;
    }

    public HotelBaseResponse setCountryName(String countryName) {
        this.countryName = countryName;
        return this;
    }

    public HotelBaseResponse setCountryNameCn(String countryNameCn) {
        this.countryNameCn = countryNameCn;
        return this;
    }

    public HotelBaseResponse(Long id, String hotelId, String supplierHotelId, String hotelName, String hotelNameCN, String telephone, String address, String addressCN, String postCode, String cityId, String cityName, String cityNameCN, String stateName, String countryId, String bannerUrl, String fax, String longitude, String latitude, String expediaHotelId, Boolean status, Date createTime, Date updateTime, Boolean del, String operator, String countryCode, String countryName, String countryNameCn) {
        this.id = id;
        this.hotelId = hotelId;
        this.supplierHotelId = supplierHotelId;
        this.hotelName = hotelName;
        this.hotelNameCN = hotelNameCN;
        this.telephone = telephone;
        this.address = address;
        this.addressCN = addressCN;
        this.postCode = postCode;
        this.cityId = cityId;
        this.cityName = cityName;
        this.cityNameCN = cityNameCN;
        this.stateName = stateName;
        this.countryId = countryId;
        this.bannerUrl = bannerUrl;
        this.fax = fax;
        this.longitude = longitude;
        this.latitude = latitude;
        this.expediaHotelId = expediaHotelId;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.del = del;
        this.operator = operator;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.countryNameCn = countryNameCn;
    }

    public HotelBaseResponse() {
    }
}
