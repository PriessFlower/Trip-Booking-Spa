package com.bingo.hotel.spa.intl.core.push.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @description 上架酒店列表
 * @author cxj
 * @date 2024-01-15
 */
@Getter
@Setter
@Builder
public class UpHotel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
    * 主键
    */
    private Long id;

    /**
    * 分销商ID
    */
    private Integer distributeId;

    /**
    * 供应商ID
    */
    private Integer supplierId;

    /**
    * BG酒店ID
    */
    private Long hotelId;

}