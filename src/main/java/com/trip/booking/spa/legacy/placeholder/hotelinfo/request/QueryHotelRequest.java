/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelinfo.request;

import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.PageRequest;
import java.io.Serializable;
import java.util.List;

public class QueryHotelRequest
extends PageRequest
implements Serializable {
    private List<String> supplierHotelIds;
    private String cityId;
    private String cityName;
    private String cityNameCN;
    private Integer supplierId;
    private String supplierHotelId;
    private String supplierHotelName;
    private String supplierHotelNameCN;
    private String address;
    private String addressCN;
    private String telephone;
    private Boolean status;
    private Integer merger;

    public List<String> getSupplierHotelIds() {
        return this.supplierHotelIds;
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

    public Integer getSupplierId() {
        return this.supplierId;
    }

    public String getSupplierHotelId() {
        return this.supplierHotelId;
    }

    public String getSupplierHotelName() {
        return this.supplierHotelName;
    }

    public String getSupplierHotelNameCN() {
        return this.supplierHotelNameCN;
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

    public Boolean getStatus() {
        return this.status;
    }

    public Integer getMerger() {
        return this.merger;
    }

    public QueryHotelRequest setSupplierHotelIds(List<String> supplierHotelIds) {
        this.supplierHotelIds = supplierHotelIds;
        return this;
    }

    public QueryHotelRequest setCityId(String cityId) {
        this.cityId = cityId;
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

    public QueryHotelRequest setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
        return this;
    }

    public QueryHotelRequest setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
        return this;
    }

    public QueryHotelRequest setSupplierHotelName(String supplierHotelName) {
        this.supplierHotelName = supplierHotelName;
        return this;
    }

    public QueryHotelRequest setSupplierHotelNameCN(String supplierHotelNameCN) {
        this.supplierHotelNameCN = supplierHotelNameCN;
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

    public QueryHotelRequest setStatus(Boolean status) {
        this.status = status;
        return this;
    }

    public QueryHotelRequest setMerger(Integer merger) {
        this.merger = merger;
        return this;
    }

    public QueryHotelRequest(List<String> supplierHotelIds, String cityId, String cityName, String cityNameCN, Integer supplierId, String supplierHotelId, String supplierHotelName, String supplierHotelNameCN, String address, String addressCN, String telephone, Boolean status, Integer merger) {
        this.supplierHotelIds = supplierHotelIds;
        this.cityId = cityId;
        this.cityName = cityName;
        this.cityNameCN = cityNameCN;
        this.supplierId = supplierId;
        this.supplierHotelId = supplierHotelId;
        this.supplierHotelName = supplierHotelName;
        this.supplierHotelNameCN = supplierHotelNameCN;
        this.address = address;
        this.addressCN = addressCN;
        this.telephone = telephone;
        this.status = status;
        this.merger = merger;
    }

    public QueryHotelRequest() {
    }
}
