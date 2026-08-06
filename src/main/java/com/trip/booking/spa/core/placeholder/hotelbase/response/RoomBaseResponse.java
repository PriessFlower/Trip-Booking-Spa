/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonFormat
 */
package com.trip.booking.spa.core.placeholder.hotelbase.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;

public class RoomBaseResponse
implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String roomId;
    private String hotelId;
    private String hotelName;
    private String roomName;
    private String roomNameCN;
    private String area;
    private String floor;
    private Integer broadnet;
    private String bedType;
    private String bedDesc;
    private String bedName;
    private String bedNameCN;
    private Integer bedTypeStatus;
    private String bedNumber;
    private Integer capacity;
    private Integer hasBathroom;
    private Integer hasWindows;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private boolean status;
    private String operator;

    public void setId(Long id) {
        this.id = id;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public void setHotelId(String hotelId) {
        this.hotelId = hotelId;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public void setRoomNameCN(String roomNameCN) {
        this.roomNameCN = roomNameCN;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public void setBroadnet(Integer broadnet) {
        this.broadnet = broadnet;
    }

    public void setBedType(String bedType) {
        this.bedType = bedType;
    }

    public void setBedDesc(String bedDesc) {
        this.bedDesc = bedDesc;
    }

    public void setBedName(String bedName) {
        this.bedName = bedName;
    }

    public void setBedNameCN(String bedNameCN) {
        this.bedNameCN = bedNameCN;
    }

    public void setBedTypeStatus(Integer bedTypeStatus) {
        this.bedTypeStatus = bedTypeStatus;
    }

    public void setBedNumber(String bedNumber) {
        this.bedNumber = bedNumber;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public void setHasBathroom(Integer hasBathroom) {
        this.hasBathroom = hasBathroom;
    }

    public void setHasWindows(Integer hasWindows) {
        this.hasWindows = hasWindows;
    }

    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Long getId() {
        return this.id;
    }

    public String getRoomId() {
        return this.roomId;
    }

    public String getHotelId() {
        return this.hotelId;
    }

    public String getHotelName() {
        return this.hotelName;
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

    public String getBedType() {
        return this.bedType;
    }

    public String getBedDesc() {
        return this.bedDesc;
    }

    public String getBedName() {
        return this.bedName;
    }

    public String getBedNameCN() {
        return this.bedNameCN;
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

    public Date getUpdateTime() {
        return this.updateTime;
    }

    public boolean isStatus() {
        return this.status;
    }

    public String getOperator() {
        return this.operator;
    }
}
