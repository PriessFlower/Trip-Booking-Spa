/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  javax.validation.constraints.NotBlank
 *  javax.validation.constraints.NotNull
 */
package com.trip.booking.spa.legacy.placeholder.hotelbase.request;

import com.trip.booking.spa.legacy.placeholder.hotelbase.dto.GlobalHotelBaseExtendDTO;
import com.trip.booking.spa.legacy.placeholder.hotelbase.dto.GlobalHotelPictureDTO;
import com.trip.booking.spa.legacy.placeholder.hotelbase.validted.EditGroup;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class RoomBaseRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotNull(message="\u4fee\u6539\u65f6ID\u4e0d\u53ef\u4e3a\u7a7a!", groups={EditGroup.class})
    private @NotNull(message="\u4fee\u6539\u65f6ID\u4e0d\u53ef\u4e3a\u7a7a!", groups={EditGroup.class}) Long id;
    private String roomId;
    @NotNull(message="\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotNull(message="\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!") String hotelId;
    @NotBlank(message="\u623f\u578b\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotBlank(message="\u623f\u578b\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a!") String roomName;
    private String roomNameCN;
    @NotBlank(message="\u623f\u95f4\u9762\u79ef\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotBlank(message="\u623f\u95f4\u9762\u79ef\u4e0d\u80fd\u4e3a\u7a7a!") String area;
    @NotBlank(message="\u623f\u95f4\u697c\u5c42\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotBlank(message="\u623f\u95f4\u697c\u5c42\u4e0d\u80fd\u4e3a\u7a7a!") String floor;
    private Integer broadnet;
    private String bedName;
    private String bedNameCN;
    @NotBlank(message="\u5e8a\u578b\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotBlank(message="\u5e8a\u578b\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a!") String bedType;
    @NotBlank(message="\u5e8a\u578b\u63cf\u8ff0\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotBlank(message="\u5e8a\u578b\u63cf\u8ff0\u4e0d\u80fd\u4e3a\u7a7a!") String bedDesc;
    @NotNull(message="\u5e8a\u578b\u72b6\u6001\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotNull(message="\u5e8a\u578b\u72b6\u6001\u4e0d\u80fd\u4e3a\u7a7a!") Integer bedTypeStatus;
    @NotBlank(message="\u5e8a\u6570\u4e0d\u80fd\u4e3a\u7a7a!")
    private @NotBlank(message="\u5e8a\u6570\u4e0d\u80fd\u4e3a\u7a7a!") String bedNumber;
    private Integer capacity;
    private Integer hasBathroom;
    private Integer hasWindows;
    private Integer isSmoking;
    private Date updateTime;
    private Boolean status;
    private String operator;
    private Boolean del;
    private List<GlobalHotelPictureDTO> globalRoomPictureDTOS;
    private List<GlobalHotelBaseExtendDTO> globalRoomBaseExtendDTOS;

    public Long getId() {
        return this.id;
    }

    public String getRoomId() {
        return this.roomId;
    }

    public String getHotelId() {
        return this.hotelId;
    }

    public String getRoomName() {
        return this.roomName;
    }

    public String getRoomNameCN() {
        return this.roomNameCN;
    }

    public String getArea() {
        return this.area;
    }

    public String getFloor() {
        return this.floor;
    }

    public Integer getBroadnet() {
        return this.broadnet;
    }

    public String getBedName() {
        return this.bedName;
    }

    public String getBedNameCN() {
        return this.bedNameCN;
    }

    public String getBedType() {
        return this.bedType;
    }

    public String getBedDesc() {
        return this.bedDesc;
    }

    public Integer getBedTypeStatus() {
        return this.bedTypeStatus;
    }

    public String getBedNumber() {
        return this.bedNumber;
    }

    public Integer getCapacity() {
        return this.capacity;
    }

    public Integer getHasBathroom() {
        return this.hasBathroom;
    }

    public Integer getHasWindows() {
        return this.hasWindows;
    }

    public Integer getIsSmoking() {
        return this.isSmoking;
    }

    public Date getUpdateTime() {
        return this.updateTime;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public String getOperator() {
        return this.operator;
    }

    public Boolean getDel() {
        return this.del;
    }

    public List<GlobalHotelPictureDTO> getGlobalRoomPictureDTOS() {
        return this.globalRoomPictureDTOS;
    }

    public List<GlobalHotelBaseExtendDTO> getGlobalRoomBaseExtendDTOS() {
        return this.globalRoomBaseExtendDTOS;
    }

    public RoomBaseRequest setId(Long id) {
        this.id = id;
        return this;
    }

    public RoomBaseRequest setRoomId(String roomId) {
        this.roomId = roomId;
        return this;
    }

    public RoomBaseRequest setHotelId(String hotelId) {
        this.hotelId = hotelId;
        return this;
    }

    public RoomBaseRequest setRoomName(String roomName) {
        this.roomName = roomName;
        return this;
    }

    public RoomBaseRequest setRoomNameCN(String roomNameCN) {
        this.roomNameCN = roomNameCN;
        return this;
    }

    public RoomBaseRequest setArea(String area) {
        this.area = area;
        return this;
    }

    public RoomBaseRequest setFloor(String floor) {
        this.floor = floor;
        return this;
    }

    public RoomBaseRequest setBroadnet(Integer broadnet) {
        this.broadnet = broadnet;
        return this;
    }

    public RoomBaseRequest setBedName(String bedName) {
        this.bedName = bedName;
        return this;
    }

    public RoomBaseRequest setBedNameCN(String bedNameCN) {
        this.bedNameCN = bedNameCN;
        return this;
    }

    public RoomBaseRequest setBedType(String bedType) {
        this.bedType = bedType;
        return this;
    }

    public RoomBaseRequest setBedDesc(String bedDesc) {
        this.bedDesc = bedDesc;
        return this;
    }

    public RoomBaseRequest setBedTypeStatus(Integer bedTypeStatus) {
        this.bedTypeStatus = bedTypeStatus;
        return this;
    }

    public RoomBaseRequest setBedNumber(String bedNumber) {
        this.bedNumber = bedNumber;
        return this;
    }

    public RoomBaseRequest setCapacity(Integer capacity) {
        this.capacity = capacity;
        return this;
    }

    public RoomBaseRequest setHasBathroom(Integer hasBathroom) {
        this.hasBathroom = hasBathroom;
        return this;
    }

    public RoomBaseRequest setHasWindows(Integer hasWindows) {
        this.hasWindows = hasWindows;
        return this;
    }

    public RoomBaseRequest setIsSmoking(Integer isSmoking) {
        this.isSmoking = isSmoking;
        return this;
    }

    public RoomBaseRequest setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    public RoomBaseRequest setStatus(Boolean status) {
        this.status = status;
        return this;
    }

    public RoomBaseRequest setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    public RoomBaseRequest setDel(Boolean del) {
        this.del = del;
        return this;
    }

    public RoomBaseRequest setGlobalRoomPictureDTOS(List<GlobalHotelPictureDTO> globalRoomPictureDTOS) {
        this.globalRoomPictureDTOS = globalRoomPictureDTOS;
        return this;
    }

    public RoomBaseRequest setGlobalRoomBaseExtendDTOS(List<GlobalHotelBaseExtendDTO> globalRoomBaseExtendDTOS) {
        this.globalRoomBaseExtendDTOS = globalRoomBaseExtendDTOS;
        return this;
    }

    public RoomBaseRequest(Long id, String roomId, String hotelId, String roomName, String roomNameCN, String area, String floor, Integer broadnet, String bedName, String bedNameCN, String bedType, String bedDesc, Integer bedTypeStatus, String bedNumber, Integer capacity, Integer hasBathroom, Integer hasWindows, Integer isSmoking, Date updateTime, Boolean status, String operator, Boolean del, List<GlobalHotelPictureDTO> globalRoomPictureDTOS, List<GlobalHotelBaseExtendDTO> globalRoomBaseExtendDTOS) {
        this.id = id;
        this.roomId = roomId;
        this.hotelId = hotelId;
        this.roomName = roomName;
        this.roomNameCN = roomNameCN;
        this.area = area;
        this.floor = floor;
        this.broadnet = broadnet;
        this.bedName = bedName;
        this.bedNameCN = bedNameCN;
        this.bedType = bedType;
        this.bedDesc = bedDesc;
        this.bedTypeStatus = bedTypeStatus;
        this.bedNumber = bedNumber;
        this.capacity = capacity;
        this.hasBathroom = hasBathroom;
        this.hasWindows = hasWindows;
        this.isSmoking = isSmoking;
        this.updateTime = updateTime;
        this.status = status;
        this.operator = operator;
        this.del = del;
        this.globalRoomPictureDTOS = globalRoomPictureDTOS;
        this.globalRoomBaseExtendDTOS = globalRoomBaseExtendDTOS;
    }

    public RoomBaseRequest() {
    }
}
