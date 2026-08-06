/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.annotation.TableField
 */
package com.trip.booking.spa.core.placeholder.hotelbase.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;

public class GlobalHotelPictureDTO
implements Serializable {
    private static final long serialVersionUID = 1L;
    private String hotelId;
    private String roomId;
    private String type;
    private String name;
    @TableField(value="name_cn")
    private String nameCN;
    private Integer sort;
    private String url;

    public String getHotelId() {
        return this.hotelId;
    }

    public String getRoomId() {
        return this.roomId;
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }

    public String getNameCN() {
        return this.nameCN;
    }

    public Integer getSort() {
        return this.sort;
    }

    public String getUrl() {
        return this.url;
    }

    public GlobalHotelPictureDTO setHotelId(String hotelId) {
        this.hotelId = hotelId;
        return this;
    }

    public GlobalHotelPictureDTO setRoomId(String roomId) {
        this.roomId = roomId;
        return this;
    }

    public GlobalHotelPictureDTO setType(String type) {
        this.type = type;
        return this;
    }

    public GlobalHotelPictureDTO setName(String name) {
        this.name = name;
        return this;
    }

    public GlobalHotelPictureDTO setNameCN(String nameCN) {
        this.nameCN = nameCN;
        return this;
    }

    public GlobalHotelPictureDTO setSort(Integer sort) {
        this.sort = sort;
        return this;
    }

    public GlobalHotelPictureDTO setUrl(String url) {
        this.url = url;
        return this;
    }

    public GlobalHotelPictureDTO(String hotelId, String roomId, String type, String name, String nameCN, Integer sort, String url) {
        this.hotelId = hotelId;
        this.roomId = roomId;
        this.type = type;
        this.name = name;
        this.nameCN = nameCN;
        this.sort = sort;
        this.url = url;
    }

    public GlobalHotelPictureDTO() {
    }
}
