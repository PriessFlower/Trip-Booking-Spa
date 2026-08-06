/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.core.placeholder.hotelinfo.request;

import java.io.Serializable;

public class SupplierProductBaseRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    private String productId;
    private String roomId;
    private Integer supplierId;
    private String supplierHotelId;
    private String supplierRoomId;
    private String supplierProductName;
    private String supplierProductNameCN;
    private String supplierProductId;
    private String supplierBedDesc;
    private Integer hasWindow;
    private Integer breakfast;
    private Integer cancelType;
    private String operator;
    private Boolean del;

    public String getProductId() {
        return this.productId;
    }

    public String getRoomId() {
        return this.roomId;
    }

    public Integer getSupplierId() {
        return this.supplierId;
    }

    public String getSupplierHotelId() {
        return this.supplierHotelId;
    }

    public String getSupplierRoomId() {
        return this.supplierRoomId;
    }

    public String getSupplierProductName() {
        return this.supplierProductName;
    }

    public String getSupplierProductNameCN() {
        return this.supplierProductNameCN;
    }

    public String getSupplierProductId() {
        return this.supplierProductId;
    }

    public String getSupplierBedDesc() {
        return this.supplierBedDesc;
    }

    public Integer getHasWindow() {
        return this.hasWindow;
    }

    public Integer getBreakfast() {
        return this.breakfast;
    }

    public Integer getCancelType() {
        return this.cancelType;
    }

    public String getOperator() {
        return this.operator;
    }

    public Boolean getDel() {
        return this.del;
    }

    public SupplierProductBaseRequest setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public SupplierProductBaseRequest setRoomId(String roomId) {
        this.roomId = roomId;
        return this;
    }

    public SupplierProductBaseRequest setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
        return this;
    }

    public SupplierProductBaseRequest setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
        return this;
    }

    public SupplierProductBaseRequest setSupplierRoomId(String supplierRoomId) {
        this.supplierRoomId = supplierRoomId;
        return this;
    }

    public SupplierProductBaseRequest setSupplierProductName(String supplierProductName) {
        this.supplierProductName = supplierProductName;
        return this;
    }

    public SupplierProductBaseRequest setSupplierProductNameCN(String supplierProductNameCN) {
        this.supplierProductNameCN = supplierProductNameCN;
        return this;
    }

    public SupplierProductBaseRequest setSupplierProductId(String supplierProductId) {
        this.supplierProductId = supplierProductId;
        return this;
    }

    public SupplierProductBaseRequest setSupplierBedDesc(String supplierBedDesc) {
        this.supplierBedDesc = supplierBedDesc;
        return this;
    }

    public SupplierProductBaseRequest setHasWindow(Integer hasWindow) {
        this.hasWindow = hasWindow;
        return this;
    }

    public SupplierProductBaseRequest setBreakfast(Integer breakfast) {
        this.breakfast = breakfast;
        return this;
    }

    public SupplierProductBaseRequest setCancelType(Integer cancelType) {
        this.cancelType = cancelType;
        return this;
    }

    public SupplierProductBaseRequest setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    public SupplierProductBaseRequest setDel(Boolean del) {
        this.del = del;
        return this;
    }

    public SupplierProductBaseRequest(String productId, String roomId, Integer supplierId, String supplierHotelId, String supplierRoomId, String supplierProductName, String supplierProductNameCN, String supplierProductId, String supplierBedDesc, Integer hasWindow, Integer breakfast, Integer cancelType, String operator, Boolean del) {
        this.productId = productId;
        this.roomId = roomId;
        this.supplierId = supplierId;
        this.supplierHotelId = supplierHotelId;
        this.supplierRoomId = supplierRoomId;
        this.supplierProductName = supplierProductName;
        this.supplierProductNameCN = supplierProductNameCN;
        this.supplierProductId = supplierProductId;
        this.supplierBedDesc = supplierBedDesc;
        this.hasWindow = hasWindow;
        this.breakfast = breakfast;
        this.cancelType = cancelType;
        this.operator = operator;
        this.del = del;
    }

    public SupplierProductBaseRequest() {
    }
}
