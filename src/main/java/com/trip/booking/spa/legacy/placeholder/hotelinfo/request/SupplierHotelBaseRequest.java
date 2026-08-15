/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.annotation.IdType
 *  com.baomidou.mybatisplus.annotation.TableField
 *  com.baomidou.mybatisplus.annotation.TableId
 */
package com.trip.booking.spa.legacy.placeholder.hotelinfo.request;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierRoomBaseRequest;
import java.io.Serializable;
import java.util.List;

public class SupplierHotelBaseRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value="id", type=IdType.AUTO)
    private Long id;
    private Integer supplierId;
    private String supplierHotelId;
    private String supplierHotelName;
    @TableField(value="supplier_hotel_name_cn")
    private String supplierHotelNameCN;
    private String telephone;
    private String postcode;
    private String currency;
    private String address;
    @TableField(value="address_cn")
    private String addressCN;
    private String countryCode;
    private String countryName;
    private String countryId;
    private String cityId;
    private String cityName;
    private String cityNameCN;
    private String stateName;
    private String stateNameCN;
    private String fax;
    private String longitude;
    private String latitude;
    private String hotelType;
    private Integer rooms;
    private String brandId;
    private String brandName;
    private String groupId;
    private String groupName;
    private Integer recommendLevel;
    private String score;
    private Boolean bookAble;
    private Boolean status;
    private Integer breakfast;
    private String descriptions;
    private String introduceInfo;
    private Boolean del;
    private String operator;
    private List<SupplierRoomBaseRequest> roomList;

    public Long getId() {
        return this.id;
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

    public String getTelephone() {
        return this.telephone;
    }

    public String getPostcode() {
        return this.postcode;
    }

    public String getCurrency() {
        return this.currency;
    }

    public String getAddress() {
        return this.address;
    }

    public String getAddressCN() {
        return this.addressCN;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public String getCountryName() {
        return this.countryName;
    }

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

    public String getStateName() {
        return this.stateName;
    }

    public String getStateNameCN() {
        return this.stateNameCN;
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

    public String getHotelType() {
        return this.hotelType;
    }

    public Integer getRooms() {
        return this.rooms;
    }

    public String getBrandId() {
        return this.brandId;
    }

    public String getBrandName() {
        return this.brandName;
    }

    public String getGroupId() {
        return this.groupId;
    }

    public String getGroupName() {
        return this.groupName;
    }

    public Integer getRecommendLevel() {
        return this.recommendLevel;
    }

    public String getScore() {
        return this.score;
    }

    public Boolean getBookAble() {
        return this.bookAble;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public Integer getBreakfast() {
        return this.breakfast;
    }

    public String getDescriptions() {
        return this.descriptions;
    }

    public String getIntroduceInfo() {
        return this.introduceInfo;
    }

    public Boolean getDel() {
        return this.del;
    }

    public String getOperator() {
        return this.operator;
    }

    public List<SupplierRoomBaseRequest> getRoomList() {
        return this.roomList;
    }

    public SupplierHotelBaseRequest setId(Long id) {
        this.id = id;
        return this;
    }

    public SupplierHotelBaseRequest setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
        return this;
    }

    public SupplierHotelBaseRequest setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
        return this;
    }

    public SupplierHotelBaseRequest setSupplierHotelName(String supplierHotelName) {
        this.supplierHotelName = supplierHotelName;
        return this;
    }

    public SupplierHotelBaseRequest setSupplierHotelNameCN(String supplierHotelNameCN) {
        this.supplierHotelNameCN = supplierHotelNameCN;
        return this;
    }

    public SupplierHotelBaseRequest setTelephone(String telephone) {
        this.telephone = telephone;
        return this;
    }

    public SupplierHotelBaseRequest setPostcode(String postcode) {
        this.postcode = postcode;
        return this;
    }

    public SupplierHotelBaseRequest setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public SupplierHotelBaseRequest setAddress(String address) {
        this.address = address;
        return this;
    }

    public SupplierHotelBaseRequest setAddressCN(String addressCN) {
        this.addressCN = addressCN;
        return this;
    }

    public SupplierHotelBaseRequest setCountryCode(String countryCode) {
        this.countryCode = countryCode;
        return this;
    }

    public SupplierHotelBaseRequest setCountryName(String countryName) {
        this.countryName = countryName;
        return this;
    }

    public SupplierHotelBaseRequest setCountryId(String countryId) {
        this.countryId = countryId;
        return this;
    }

    public SupplierHotelBaseRequest setCityId(String cityId) {
        this.cityId = cityId;
        return this;
    }

    public SupplierHotelBaseRequest setCityName(String cityName) {
        this.cityName = cityName;
        return this;
    }

    public SupplierHotelBaseRequest setCityNameCN(String cityNameCN) {
        this.cityNameCN = cityNameCN;
        return this;
    }

    public SupplierHotelBaseRequest setStateName(String stateName) {
        this.stateName = stateName;
        return this;
    }

    public SupplierHotelBaseRequest setStateNameCN(String stateNameCN) {
        this.stateNameCN = stateNameCN;
        return this;
    }

    public SupplierHotelBaseRequest setFax(String fax) {
        this.fax = fax;
        return this;
    }

    public SupplierHotelBaseRequest setLongitude(String longitude) {
        this.longitude = longitude;
        return this;
    }

    public SupplierHotelBaseRequest setLatitude(String latitude) {
        this.latitude = latitude;
        return this;
    }

    public SupplierHotelBaseRequest setHotelType(String hotelType) {
        this.hotelType = hotelType;
        return this;
    }

    public SupplierHotelBaseRequest setRooms(Integer rooms) {
        this.rooms = rooms;
        return this;
    }

    public SupplierHotelBaseRequest setBrandId(String brandId) {
        this.brandId = brandId;
        return this;
    }

    public SupplierHotelBaseRequest setBrandName(String brandName) {
        this.brandName = brandName;
        return this;
    }

    public SupplierHotelBaseRequest setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public SupplierHotelBaseRequest setGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public SupplierHotelBaseRequest setRecommendLevel(Integer recommendLevel) {
        this.recommendLevel = recommendLevel;
        return this;
    }

    public SupplierHotelBaseRequest setScore(String score) {
        this.score = score;
        return this;
    }

    public SupplierHotelBaseRequest setBookAble(Boolean bookAble) {
        this.bookAble = bookAble;
        return this;
    }

    public SupplierHotelBaseRequest setStatus(Boolean status) {
        this.status = status;
        return this;
    }

    public SupplierHotelBaseRequest setBreakfast(Integer breakfast) {
        this.breakfast = breakfast;
        return this;
    }

    public SupplierHotelBaseRequest setDescriptions(String descriptions) {
        this.descriptions = descriptions;
        return this;
    }

    public SupplierHotelBaseRequest setIntroduceInfo(String introduceInfo) {
        this.introduceInfo = introduceInfo;
        return this;
    }

    public SupplierHotelBaseRequest setDel(Boolean del) {
        this.del = del;
        return this;
    }

    public SupplierHotelBaseRequest setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    public SupplierHotelBaseRequest setRoomList(List<SupplierRoomBaseRequest> roomList) {
        this.roomList = roomList;
        return this;
    }

    public SupplierHotelBaseRequest(Long id, Integer supplierId, String supplierHotelId, String supplierHotelName, String supplierHotelNameCN, String telephone, String postcode, String currency, String address, String addressCN, String countryCode, String countryName, String countryId, String cityId, String cityName, String cityNameCN, String stateName, String stateNameCN, String fax, String longitude, String latitude, String hotelType, Integer rooms, String brandId, String brandName, String groupId, String groupName, Integer recommendLevel, String score, Boolean bookAble, Boolean status, Integer breakfast, String descriptions, String introduceInfo, Boolean del, String operator, List<SupplierRoomBaseRequest> roomList) {
        this.id = id;
        this.supplierId = supplierId;
        this.supplierHotelId = supplierHotelId;
        this.supplierHotelName = supplierHotelName;
        this.supplierHotelNameCN = supplierHotelNameCN;
        this.telephone = telephone;
        this.postcode = postcode;
        this.currency = currency;
        this.address = address;
        this.addressCN = addressCN;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.countryId = countryId;
        this.cityId = cityId;
        this.cityName = cityName;
        this.cityNameCN = cityNameCN;
        this.stateName = stateName;
        this.stateNameCN = stateNameCN;
        this.fax = fax;
        this.longitude = longitude;
        this.latitude = latitude;
        this.hotelType = hotelType;
        this.rooms = rooms;
        this.brandId = brandId;
        this.brandName = brandName;
        this.groupId = groupId;
        this.groupName = groupName;
        this.recommendLevel = recommendLevel;
        this.score = score;
        this.bookAble = bookAble;
        this.status = status;
        this.breakfast = breakfast;
        this.descriptions = descriptions;
        this.introduceInfo = introduceInfo;
        this.del = del;
        this.operator = operator;
        this.roomList = roomList;
    }

    public SupplierHotelBaseRequest() {
    }
}
