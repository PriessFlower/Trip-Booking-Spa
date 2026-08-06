/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.core.placeholder.hotelbase.dto;

import java.io.Serializable;

public class GlobalHotelBaseExtendDTO
implements Serializable {
    private static final long serialVersionUID = 1L;
    private String hotelId;
    private String roomId;
    private String language;
    private String checkIn;
    private String checkOut;
    private String instructions;
    private String minAge;
    private String fees;
    private String policies;
    private String descriptions;
    private Boolean del;

    public String getHotelId() {
        return this.hotelId;
    }

    public String getRoomId() {
        return this.roomId;
    }

    public String getLanguage() {
        return this.language;
    }

    public String getCheckIn() {
        return this.checkIn;
    }

    public String getCheckOut() {
        return this.checkOut;
    }

    public String getInstructions() {
        return this.instructions;
    }

    public String getMinAge() {
        return this.minAge;
    }

    public String getFees() {
        return this.fees;
    }

    public String getPolicies() {
        return this.policies;
    }

    public String getDescriptions() {
        return this.descriptions;
    }

    public Boolean getDel() {
        return this.del;
    }

    public GlobalHotelBaseExtendDTO setHotelId(String hotelId) {
        this.hotelId = hotelId;
        return this;
    }

    public GlobalHotelBaseExtendDTO setRoomId(String roomId) {
        this.roomId = roomId;
        return this;
    }

    public GlobalHotelBaseExtendDTO setLanguage(String language) {
        this.language = language;
        return this;
    }

    public GlobalHotelBaseExtendDTO setCheckIn(String checkIn) {
        this.checkIn = checkIn;
        return this;
    }

    public GlobalHotelBaseExtendDTO setCheckOut(String checkOut) {
        this.checkOut = checkOut;
        return this;
    }

    public GlobalHotelBaseExtendDTO setInstructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    public GlobalHotelBaseExtendDTO setMinAge(String minAge) {
        this.minAge = minAge;
        return this;
    }

    public GlobalHotelBaseExtendDTO setFees(String fees) {
        this.fees = fees;
        return this;
    }

    public GlobalHotelBaseExtendDTO setPolicies(String policies) {
        this.policies = policies;
        return this;
    }

    public GlobalHotelBaseExtendDTO setDescriptions(String descriptions) {
        this.descriptions = descriptions;
        return this;
    }

    public GlobalHotelBaseExtendDTO setDel(Boolean del) {
        this.del = del;
        return this;
    }

    public GlobalHotelBaseExtendDTO(String hotelId, String roomId, String language, String checkIn, String checkOut, String instructions, String minAge, String fees, String policies, String descriptions, Boolean del) {
        this.hotelId = hotelId;
        this.roomId = roomId;
        this.language = language;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.instructions = instructions;
        this.minAge = minAge;
        this.fees = fees;
        this.policies = policies;
        this.descriptions = descriptions;
        this.del = del;
    }

    public GlobalHotelBaseExtendDTO() {
    }
}
