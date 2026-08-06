package com.trip.booking.spa.core.api.common.asynchttp;


import com.trip.booking.spa.core.api.common.exception.ParseException;

/**
 * 接口返回结果数据解析器
 * @author pengtao.han
 *
 * @param <T>
 */
public interface IParser<T>  {

    /**
     * 数据解析
     * @param data
     * @return
     */
    public T parse(String data) throws Exception;

    default public T parseError(String data) throws ParseException {return null;}
}
