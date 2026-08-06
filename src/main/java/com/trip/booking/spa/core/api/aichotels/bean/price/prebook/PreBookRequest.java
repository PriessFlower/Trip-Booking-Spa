package com.trip.booking.spa.core.api.aichotels.bean.price.prebook;

import lombok.Builder;

import java.util.List;

@Builder
public class PreBookRequest {

    /**
     * hotel_id : 53365
     * check_in : 2020-07-27
     * check_out : 2020-07-28
     * room_number : 1
     * adult_number : 2
     * kids_number : 0
     * room_key : MjA5MDkzOS4uKkxQMS4uKjUzMzY1
     * nationality : CN
     * children_ages : [[]]
     */

    private int hotel_id;
    private String check_in;
    private String check_out;
    private int room_number;
    private int adult_number;
    private int kids_number;
    private String room_key;
    private String nationality;
    private List<List<String>> children_ages;

    public int getHotel_id() {
        return hotel_id;
    }

    public void setHotel_id(int hotel_id) {
        this.hotel_id = hotel_id;
    }

    public String getCheck_in() {
        return check_in;
    }

    public void setCheck_in(String check_in) {
        this.check_in = check_in;
    }

    public String getCheck_out() {
        return check_out;
    }

    public void setCheck_out(String check_out) {
        this.check_out = check_out;
    }

    public int getRoom_number() {
        return room_number;
    }

    public void setRoom_number(int room_number) {
        this.room_number = room_number;
    }

    public int getAdult_number() {
        return adult_number;
    }

    public void setAdult_number(int adult_number) {
        this.adult_number = adult_number;
    }

    public int getKids_number() {
        return kids_number;
    }

    public void setKids_number(int kids_number) {
        this.kids_number = kids_number;
    }

    public String getRoom_key() {
        return room_key;
    }

    public void setRoom_key(String room_key) {
        this.room_key = room_key;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public List<List<String>> getChildren_ages() {
        return children_ages;
    }

    public void setChildren_ages(List<List<String>> children_ages) {
        this.children_ages = children_ages;
    }
}
