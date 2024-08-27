package com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check;

import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.NightlyRate;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CheckResult {
    private Integer check_code;
    private Integer hid;
    private Integer rid;
    private String rpid;
    private String room_name;
    private String room_en_name;
    private String checkin;
    private String checkout;
    private String cancel_policy;
    private String new_cancel_policy;
    private List<NightlyRate> nightlyrate;
}
