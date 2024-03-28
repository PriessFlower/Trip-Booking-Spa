package com.bingo.hotel.spa.intl.core.util.wx.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderNotifyContent {

	private String hotelName;
	private String checkInDate;
	private String checkOutDate;
	private String checkInPerson;
	private String notifyTime;
	private String roomNum;
	private String orderProviderCode;
}
