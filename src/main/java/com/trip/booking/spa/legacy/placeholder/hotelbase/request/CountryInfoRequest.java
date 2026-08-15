/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelbase.request;

import java.io.Serializable;
import java.math.BigDecimal;

public class CountryInfoRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    private String countryId;
    private String countryCode;
    private String phoneCode;
    private String countryName;
    private String countryNameCN;
    private String continent;
    private String continentCN;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String note;

    public String getCountryId() {
        return this.countryId;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public String getPhoneCode() {
        return this.phoneCode;
    }

    public String getCountryName() {
        return this.countryName;
    }

    public String getCountryNameCN() {
        return this.countryNameCN;
    }

    public String getContinent() {
        return this.continent;
    }

    public String getContinentCN() {
        return this.continentCN;
    }

    public BigDecimal getLongitude() {
        return this.longitude;
    }

    public BigDecimal getLatitude() {
        return this.latitude;
    }

    public String getNote() {
        return this.note;
    }

    public CountryInfoRequest setCountryId(String countryId) {
        this.countryId = countryId;
        return this;
    }

    public CountryInfoRequest setCountryCode(String countryCode) {
        this.countryCode = countryCode;
        return this;
    }

    public CountryInfoRequest setPhoneCode(String phoneCode) {
        this.phoneCode = phoneCode;
        return this;
    }

    public CountryInfoRequest setCountryName(String countryName) {
        this.countryName = countryName;
        return this;
    }

    public CountryInfoRequest setCountryNameCN(String countryNameCN) {
        this.countryNameCN = countryNameCN;
        return this;
    }

    public CountryInfoRequest setContinent(String continent) {
        this.continent = continent;
        return this;
    }

    public CountryInfoRequest setContinentCN(String continentCN) {
        this.continentCN = continentCN;
        return this;
    }

    public CountryInfoRequest setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
        return this;
    }

    public CountryInfoRequest setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
        return this;
    }

    public CountryInfoRequest setNote(String note) {
        this.note = note;
        return this;
    }

    public CountryInfoRequest(String countryId, String countryCode, String phoneCode, String countryName, String countryNameCN, String continent, String continentCN, BigDecimal longitude, BigDecimal latitude, String note) {
        this.countryId = countryId;
        this.countryCode = countryCode;
        this.phoneCode = phoneCode;
        this.countryName = countryName;
        this.countryNameCN = countryNameCN;
        this.continent = continent;
        this.continentCN = continentCN;
        this.longitude = longitude;
        this.latitude = latitude;
        this.note = note;
    }

    public CountryInfoRequest() {
    }
}
