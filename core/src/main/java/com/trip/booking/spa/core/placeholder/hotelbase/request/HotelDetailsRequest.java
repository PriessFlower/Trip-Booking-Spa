/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  javax.validation.constraints.NotBlank
 *  javax.validation.constraints.NotNull
 */
package com.trip.booking.spa.core.placeholder.hotelbase.request;

import com.trip.booking.spa.core.placeholder.hotelbase.dto.GlobalHotelBaseExtendDTO;
import com.trip.booking.spa.core.placeholder.hotelbase.dto.GlobalHotelPictureDTO;
import com.trip.booking.spa.core.placeholder.hotelbase.request.RoomBaseRequest;
import com.trip.booking.spa.core.placeholder.hotelbase.validted.EditGroup;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class HotelDetailsRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotNull(message="\u4fee\u6539\u65f6ID\u4e0d\u53ef\u4e3a\u7a7a!", groups={EditGroup.class})
    private @NotNull(message="\u4fee\u6539\u65f6ID\u4e0d\u53ef\u4e3a\u7a7a!", groups={EditGroup.class}) Long id;
    private String hotelId;
    @NotBlank(message="\u9152\u5e97\u540d\u79f0\u4e0d\u53ef\u4e3a\u7a7a!")
    private @NotBlank(message="\u9152\u5e97\u540d\u79f0\u4e0d\u53ef\u4e3a\u7a7a!") String hotelName;
    private String hotelNameCN;
    @NotBlank(message="\u8054\u7cfb\u7535\u8bdd\u4e0d\u53ef\u4e3a\u7a7a!")
    private @NotBlank(message="\u8054\u7cfb\u7535\u8bdd\u4e0d\u53ef\u4e3a\u7a7a!") String telephone;
    @NotBlank(message="\u9152\u5e97\u5730\u5740\u4e0d\u53ef\u4e3a\u7a7a!")
    private @NotBlank(message="\u9152\u5e97\u5730\u5740\u4e0d\u53ef\u4e3a\u7a7a!") String address;
    private String addressCN;
    private String postCode;
    @NotBlank(message="\u57ce\u5e02id\u4e0d\u53ef\u4e3a\u7a7a!")
    private @NotBlank(message="\u57ce\u5e02id\u4e0d\u53ef\u4e3a\u7a7a!") String cityId;
    @NotBlank(message="\u57ce\u5e02\u540d\u79f0\u4e0d\u53ef\u4e3a\u7a7a!")
    private @NotBlank(message="\u57ce\u5e02\u540d\u79f0\u4e0d\u53ef\u4e3a\u7a7a!") String cityName;
    private String cityNameCN;
    private String stateName;
    @NotBlank(message="\u9152\u5e97\u6240\u5728\u56fd\u5bb6ID\u4e0d\u53ef\u4e3a\u7a7a!")
    private @NotBlank(message="\u9152\u5e97\u6240\u5728\u56fd\u5bb6ID\u4e0d\u53ef\u4e3a\u7a7a!") String countryId;
    private String countryCode;
    private String fax;
    private String star;
    private String score;
    private String longitude;
    private String latitude;
    private String group;
    private String brand;
    private Boolean status;
    private Date createTime;
    private Date updateTime;
    private Boolean del;
    private String operator;
    private List<GlobalHotelPictureDTO> globalHotelPictureDTOS;
    private List<GlobalHotelBaseExtendDTO> globalHotelBaseExtendDTOS;
    private List<RoomBaseRequest> roomBaseList;

    public Long getId() {
        return this.id;
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

    public String getCountryCode() {
        return this.countryCode;
    }

    public String getFax() {
        return this.fax;
    }

    public String getStar() {
        return this.star;
    }

    public String getScore() {
        return this.score;
    }

    public String getLongitude() {
        return this.longitude;
    }

    public String getLatitude() {
        return this.latitude;
    }

    public String getGroup() {
        return this.group;
    }

    public String getBrand() {
        return this.brand;
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

    public List<GlobalHotelPictureDTO> getGlobalHotelPictureDTOS() {
        return this.globalHotelPictureDTOS;
    }

    public List<GlobalHotelBaseExtendDTO> getGlobalHotelBaseExtendDTOS() {
        return this.globalHotelBaseExtendDTOS;
    }

    public List<RoomBaseRequest> getRoomBaseList() {
        return this.roomBaseList;
    }

    public HotelDetailsRequest setId(Long id) {
        this.id = id;
        return this;
    }

    public HotelDetailsRequest setHotelId(String hotelId) {
        this.hotelId = hotelId;
        return this;
    }

    public HotelDetailsRequest setHotelName(String hotelName) {
        this.hotelName = hotelName;
        return this;
    }

    public HotelDetailsRequest setHotelNameCN(String hotelNameCN) {
        this.hotelNameCN = hotelNameCN;
        return this;
    }

    public HotelDetailsRequest setTelephone(String telephone) {
        this.telephone = telephone;
        return this;
    }

    public HotelDetailsRequest setAddress(String address) {
        this.address = address;
        return this;
    }

    public HotelDetailsRequest setAddressCN(String addressCN) {
        this.addressCN = addressCN;
        return this;
    }

    public HotelDetailsRequest setPostCode(String postCode) {
        this.postCode = postCode;
        return this;
    }

    public HotelDetailsRequest setCityId(String cityId) {
        this.cityId = cityId;
        return this;
    }

    public HotelDetailsRequest setCityName(String cityName) {
        this.cityName = cityName;
        return this;
    }

    public HotelDetailsRequest setCityNameCN(String cityNameCN) {
        this.cityNameCN = cityNameCN;
        return this;
    }

    public HotelDetailsRequest setStateName(String stateName) {
        this.stateName = stateName;
        return this;
    }

    public HotelDetailsRequest setCountryId(String countryId) {
        this.countryId = countryId;
        return this;
    }

    public HotelDetailsRequest setCountryCode(String countryCode) {
        this.countryCode = countryCode;
        return this;
    }

    public HotelDetailsRequest setFax(String fax) {
        this.fax = fax;
        return this;
    }

    public HotelDetailsRequest setStar(String star) {
        this.star = star;
        return this;
    }

    public HotelDetailsRequest setScore(String score) {
        this.score = score;
        return this;
    }

    public HotelDetailsRequest setLongitude(String longitude) {
        this.longitude = longitude;
        return this;
    }

    public HotelDetailsRequest setLatitude(String latitude) {
        this.latitude = latitude;
        return this;
    }

    public HotelDetailsRequest setGroup(String group) {
        this.group = group;
        return this;
    }

    public HotelDetailsRequest setBrand(String brand) {
        this.brand = brand;
        return this;
    }

    public HotelDetailsRequest setStatus(Boolean status) {
        this.status = status;
        return this;
    }

    public HotelDetailsRequest setCreateTime(Date createTime) {
        this.createTime = createTime;
        return this;
    }

    public HotelDetailsRequest setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    public HotelDetailsRequest setDel(Boolean del) {
        this.del = del;
        return this;
    }

    public HotelDetailsRequest setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    public HotelDetailsRequest setGlobalHotelPictureDTOS(List<GlobalHotelPictureDTO> globalHotelPictureDTOS) {
        this.globalHotelPictureDTOS = globalHotelPictureDTOS;
        return this;
    }

    public HotelDetailsRequest setGlobalHotelBaseExtendDTOS(List<GlobalHotelBaseExtendDTO> globalHotelBaseExtendDTOS) {
        this.globalHotelBaseExtendDTOS = globalHotelBaseExtendDTOS;
        return this;
    }

    public HotelDetailsRequest setRoomBaseList(List<RoomBaseRequest> roomBaseList) {
        this.roomBaseList = roomBaseList;
        return this;
    }

    public HotelDetailsRequest(Long id, String hotelId, String hotelName, String hotelNameCN, String telephone, String address, String addressCN, String postCode, String cityId, String cityName, String cityNameCN, String stateName, String countryId, String countryCode, String fax, String star, String score, String longitude, String latitude, String group, String brand, Boolean status, Date createTime, Date updateTime, Boolean del, String operator, List<GlobalHotelPictureDTO> globalHotelPictureDTOS, List<GlobalHotelBaseExtendDTO> globalHotelBaseExtendDTOS, List<RoomBaseRequest> roomBaseList) {
        this.id = id;
        this.hotelId = hotelId;
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
        this.countryCode = countryCode;
        this.fax = fax;
        this.star = star;
        this.score = score;
        this.longitude = longitude;
        this.latitude = latitude;
        this.group = group;
        this.brand = brand;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.del = del;
        this.operator = operator;
        this.globalHotelPictureDTOS = globalHotelPictureDTOS;
        this.globalHotelBaseExtendDTOS = globalHotelBaseExtendDTOS;
        this.roomBaseList = roomBaseList;
    }

    public HotelDetailsRequest() {
    }
}
