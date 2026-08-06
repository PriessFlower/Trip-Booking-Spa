package com.trip.booking.spa.core.api.common.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author hanJH
 * @since 2025-01-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("supplier_hotel_id_list")
public class SupplierHotelIdList implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    //"供应商ID"
    @TableField("supplier_id")
    private String supplierId;

    //"供应商酒店ID"
    @TableField("s_hotel_id")
    private String sHotelId;

    //"酒店上下线 0下线,1上线"
    private Integer online;

    //"业务数据更新时间"
    @TableField("last_time")
    private LocalDateTime lastTime;

    //"创建时间"
    @TableField("create_time")
    private LocalDateTime createTime;

    //"修改时间"
    @TableField("update_time")
    private LocalDateTime updateTime;

    //"操作人"
    private String operator;


}
