package com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 反参包装信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/06
 * @since : 1.0
 **/

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaseResult<T> {

    private T data;
    private String debug;
    private String error;
    private String status;
}
