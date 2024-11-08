package com.bingo.hotel.spa.intl.core.push.fliggy.service.impl;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.spa.intl.core.push.fliggy.service.FliggyPushService;
import org.springframework.beans.factory.annotation.Autowired;

public class FliggyPushServiceImpl implements FliggyPushService {
    @Autowired
    private HotelBaseIntlClient hotelBaseClient;

    @Override
    public void pushFliggyHotel() {

    }

    @Override
    public void pushFliggyRoom() {

    }
}
