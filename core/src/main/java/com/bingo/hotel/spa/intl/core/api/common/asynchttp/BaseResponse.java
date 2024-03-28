package com.bingo.hotel.spa.intl.core.api.common.asynchttp;

/**
 * 接口返回解析结果抽象类
 * @author pengtao.han
 *
 */
public interface BaseResponse {
    
    /**
     * 响应结果是否OK
     * @return
     */
    public abstract boolean isSucc();
    
    /**
     * 判断结果是否为空
     * @return
     */
    public abstract boolean isEmptyResult();
}
