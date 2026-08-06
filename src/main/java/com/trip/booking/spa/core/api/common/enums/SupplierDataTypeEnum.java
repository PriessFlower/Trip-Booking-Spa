package com.trip.booking.spa.core.api.common.enums;

/**
 * 代理商的数据类型
 * @author pengtao.han
 *
 */
public enum SupplierDataTypeEnum {
	STATIC_DATA("静态数据"),
	PRODUCT_PRICE("产品报价"),
	CHECK_PRICE("order报价"),
	CREATE_ORDER("创建订单"),
	CANCEL_ORDER("取消订单"),
	QUERY_ORDER("查询订单"),
	CHANNEL_CALLBACK("渠道接口回调")
	;
	
	private String desc;
	private SupplierDataTypeEnum(String desc) {
		this.desc = desc;
	}
}
