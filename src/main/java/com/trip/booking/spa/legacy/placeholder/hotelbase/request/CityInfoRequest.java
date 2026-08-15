/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.annotation.TableField
 *  javax.validation.constraints.NotNull
 */
package com.trip.booking.spa.legacy.placeholder.hotelbase.request;

import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.math.BigDecimal;
import javax.validation.constraints.NotNull;

public class CityInfoRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotNull(message="\u56fd\u5bb6ID\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotNull(message="\u56fd\u5bb6ID\u4e0d\u80fd\u4e3a\u7a7a!") String countryId;
    private String cityId;
    private String cityName;
    private String cityNameCN;
    private String stateId;
    private String stateName;
    @TableField(value="state_name_cn")
    private String stateNameCN;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String note;

    public String getCountryId() {
        return this.countryId;
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

    public String getStateId() {
        return this.stateId;
    }

    public String getStateName() {
        return this.stateName;
    }

    public String getStateNameCN() {
        return this.stateNameCN;
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

    public CityInfoRequest setCountryId(String countryId) {
        this.countryId = countryId;
        return this;
    }

    public CityInfoRequest setCityId(String cityId) {
        this.cityId = cityId;
        return this;
    }

    public CityInfoRequest setCityName(String cityName) {
        this.cityName = cityName;
        return this;
    }

    public CityInfoRequest setCityNameCN(String cityNameCN) {
        this.cityNameCN = cityNameCN;
        return this;
    }

    public CityInfoRequest setStateId(String stateId) {
        this.stateId = stateId;
        return this;
    }

    public CityInfoRequest setStateName(String stateName) {
        this.stateName = stateName;
        return this;
    }

    public CityInfoRequest setStateNameCN(String stateNameCN) {
        this.stateNameCN = stateNameCN;
        return this;
    }

    public CityInfoRequest setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
        return this;
    }

    public CityInfoRequest setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
        return this;
    }

    public CityInfoRequest setNote(String note) {
        this.note = note;
        return this;
    }

    public CityInfoRequest(String countryId, String cityId, String cityName, String cityNameCN, String stateId, String stateName, String stateNameCN, BigDecimal longitude, BigDecimal latitude, String note) {
        this.countryId = countryId;
        this.cityId = cityId;
        this.cityName = cityName;
        this.cityNameCN = cityNameCN;
        this.stateId = stateId;
        this.stateName = stateName;
        this.stateNameCN = stateNameCN;
        this.longitude = longitude;
        this.latitude = latitude;
        this.note = note;
    }

    public CityInfoRequest() {
    }
}
