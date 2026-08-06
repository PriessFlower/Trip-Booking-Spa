/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  javax.validation.constraints.NotEmpty
 *  javax.validation.constraints.Size
 */
package com.trip.booking.spa.core.placeholder.hotelbase.request;

import com.trip.booking.spa.core.placeholder.hotelbase.request.PageRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.validted.QueryGroup;
import java.io.Serializable;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

public class QueryHotelRequest
extends PageRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotEmpty(message="\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={QueryGroup.class})
    @Size(min=1, max=10000)
    private @NotEmpty(message="\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={QueryGroup.class}) @Size(min=1, max=10000) List<String> hotelIds;
    private String cityId;
    private Integer supplierId;
    private String cityName;
    private String cityNameCN;
    private String countryId;
    private String hotelId;
    private String hotelName;
    private String hotelNameCN;
    private String address;
    private String addressCN;
    private String telephone;
    private String longitude;
    private String latitude;
    private Boolean status;

    public List<String> getHotelIds() {
        return this.hotelIds;
    }

    public String getCityId() {
        return this.cityId;
    }

    public Integer getSupplierId() {
        return this.supplierId;
    }

    public String getCityName() {
        return this.cityName;
    }

    public String getCityNameCN() {
        return this.cityNameCN;
    }

    public String getCountryId() {
        return this.countryId;
    }

    public String getHotelId() {
        return this.hotelId;
    }

    public String getHotelName() {
        return this.hotelName;
    }

    public String getHotelNameCN() {
        return this.hotelNameCN;
    }

    public String getAddress() {
        return this.address;
    }

    public String getAddressCN() {
        return this.addressCN;
    }

    public String getTelephone() {
        return this.telephone;
    }

    public String getLongitude() {
        return this.longitude;
    }

    public String getLatitude() {
        return this.latitude;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public QueryHotelRequest setHotelIds(List<String> hotelIds) {
        this.hotelIds = hotelIds;
        return this;
    }

    public QueryHotelRequest setCityId(String cityId) {
        this.cityId = cityId;
        return this;
    }

    public QueryHotelRequest setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
        return this;
    }

    public QueryHotelRequest setCityName(String cityName) {
        this.cityName = cityName;
        return this;
    }

    public QueryHotelRequest setCityNameCN(String cityNameCN) {
        this.cityNameCN = cityNameCN;
        return this;
    }

    public QueryHotelRequest setCountryId(String countryId) {
        this.countryId = countryId;
        return this;
    }

    public QueryHotelRequest setHotelId(String hotelId) {
        this.hotelId = hotelId;
        return this;
    }

    public QueryHotelRequest setHotelName(String hotelName) {
        this.hotelName = hotelName;
        return this;
    }

    public QueryHotelRequest setHotelNameCN(String hotelNameCN) {
        this.hotelNameCN = hotelNameCN;
        return this;
    }

    public QueryHotelRequest setAddress(String address) {
        this.address = address;
        return this;
    }

    public QueryHotelRequest setAddressCN(String addressCN) {
        this.addressCN = addressCN;
        return this;
    }

    public QueryHotelRequest setTelephone(String telephone) {
        this.telephone = telephone;
        return this;
    }

    public QueryHotelRequest setLongitude(String longitude) {
        this.longitude = longitude;
        return this;
    }

    public QueryHotelRequest setLatitude(String latitude) {
        this.latitude = latitude;
        return this;
    }

    public QueryHotelRequest setStatus(Boolean status) {
        this.status = status;
        return this;
    }

    public QueryHotelRequest(List<String> hotelIds, String cityId, Integer supplierId, String cityName, String cityNameCN, String countryId, String hotelId, String hotelName, String hotelNameCN, String address, String addressCN, String telephone, String longitude, String latitude, Boolean status) {
        this.hotelIds = hotelIds;
        this.cityId = cityId;
        this.supplierId = supplierId;
        this.cityName = cityName;
        this.cityNameCN = cityNameCN;
        this.countryId = countryId;
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.hotelNameCN = hotelNameCN;
        this.address = address;
        this.addressCN = addressCN;
        this.telephone = telephone;
        this.longitude = longitude;
        this.latitude = latitude;
        this.status = status;
    }

    public QueryHotelRequest() {
    }
}
