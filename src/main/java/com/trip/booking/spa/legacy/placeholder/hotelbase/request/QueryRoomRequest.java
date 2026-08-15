/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  javax.validation.constraints.NotBlank
 *  javax.validation.constraints.NotEmpty
 *  javax.validation.constraints.NotNull
 *  javax.validation.constraints.Size
 */
package com.trip.booking.spa.legacy.placeholder.hotelbase.request;

import com.trip.booking.spa.legacy.placeholder.hotelbase.request.PageRequest;
import com.trip.booking.spa.legacy.placeholder.hotelbase.validted.ListGroup;
import com.trip.booking.spa.legacy.placeholder.hotelbase.validted.QueryGroup;
import java.io.Serializable;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class QueryRoomRequest
extends PageRequest
implements Serializable {
    @NotEmpty(message="\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={QueryGroup.class})
    @Size(min=1, max=100)
    private @NotEmpty(message="\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={QueryGroup.class}) @Size(min=1, max=100) List<String> hotelIds;
    @NotNull(message="\u4f9b\u5e94\u5546ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={ListGroup.class})
    private @NotNull(message="\u4f9b\u5e94\u5546ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={ListGroup.class}) Integer supplierId;
    @NotBlank(message="\u4f9b\u5e94\u5546\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={ListGroup.class})
    private @NotBlank(message="\u4f9b\u5e94\u5546\u9152\u5e97ID\u4e0d\u80fd\u4e3a\u7a7a!", groups={ListGroup.class}) String supplierHotelId;
    private String hotelId;
    private String hotelName;
    private String hotelNameCN;
    private Boolean status;

    public void setHotelIds(List<String> hotelIds) {
        this.hotelIds = hotelIds;
    }

    public void setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
    }

    public void setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
    }

    public void setHotelId(String hotelId) {
        this.hotelId = hotelId;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public void setHotelNameCN(String hotelNameCN) {
        this.hotelNameCN = hotelNameCN;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public List<String> getHotelIds() {
        return this.hotelIds;
    }

    public Integer getSupplierId() {
        return this.supplierId;
    }

    public String getSupplierHotelId() {
        return this.supplierHotelId;
    }

    public String getHotelId() {
        return this.hotelId;
    }

    public String getHotelName() {
        return this.hotelName;
    }

    public String getHotelNameCN() {
        return this.hotelNameCN;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public QueryRoomRequest(List<String> hotelIds, Integer supplierId, String supplierHotelId, String hotelId, String hotelName, String hotelNameCN, Boolean status) {
        this.hotelIds = hotelIds;
        this.supplierId = supplierId;
        this.supplierHotelId = supplierHotelId;
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.hotelNameCN = hotelNameCN;
        this.status = status;
    }

    public QueryRoomRequest() {
    }
}
