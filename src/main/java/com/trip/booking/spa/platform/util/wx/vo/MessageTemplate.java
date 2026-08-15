package com.trip.booking.spa.platform.util.wx.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageTemplate {
	/**
	 * 成员ID列表（消息接收者，多个接收者用‘|’分隔，最多支持1000个）。特殊情况：指定为@all，则向关注该企业应用的全部成员发送
	 */
	private String touser;
	
	/**
	 * 部门ID列表，多个接收者用‘|’分隔，最多支持100个。当touser为@all时忽略本参数
	 */
	private String toparty;
	
	/**
	 * 标签ID列表，多个接收者用‘|’分隔。当touser为@all时忽略本参数
	 */
	private String totag;
	
	/**
	 * 消息类型，此时固定为：text （支持消息型应用跟主页型应用）
	 */
	private String msgtype;
	
	private String agentid;
	
	private String safe;
}
