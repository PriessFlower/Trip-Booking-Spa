package com.bingo.hotel.spa.intl.core.util.wx.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 接口凭证 
 * 
 *
 */

@Setter
@Getter
public class Token {
	private String accessToken;// 接口访问凭证
	private int expiresIn;// 凭证有效期，单位：秒

	//oauth2.0
	private String refreshToken;//刷新token
	private String openid;
	private String scope;
	private String userId;

	private String sessionKey;
	private String unionId;
	
	private String errcode;//错误编码
	private String errmsg;//错误消息

	public void setErrcode(String errcode) {
		this.errcode = errcode;
		this.errmsg = ErrCode.errMsg(errcode);
	}

}
