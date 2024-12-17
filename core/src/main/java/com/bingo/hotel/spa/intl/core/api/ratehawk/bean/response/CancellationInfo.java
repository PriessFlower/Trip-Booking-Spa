package com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 取消规则信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/17
 * @since : 1.0
 **/

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CancellationInfo {

    private List<QueryProductResponse.Policies> policies;
    private String free_cancellation_before;
}
