
package com.bingo.hotel.spa.intl.cli.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPriceRespDTO {

    /**
     * 验价结果
     */
    private Boolean checkStatus;
    /**
     * 售卖价格
     */
    private Integer salePrice;
    /**
     * 佣金
     */
    private Integer subPrice;
    /**
     * 剩余库存
     */
    private Integer remainRoomNum;
    /**
     * 验价信息
     */
    private String message;
}
