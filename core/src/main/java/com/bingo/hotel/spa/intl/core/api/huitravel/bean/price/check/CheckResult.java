package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check;

import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.NightlyRate;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CheckResult {
    private int check_code;
    private int hid;
    private int rid;
    private int rpid;
    private String room_name;
    private String room_en_name;
    private String checkin;
    private String checkout;
    private String cancel_policy;
    private String new_cancel_policy;
    private List<NightlyRate> nightlyrate;
}
