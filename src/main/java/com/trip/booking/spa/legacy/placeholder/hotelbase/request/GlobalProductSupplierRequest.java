/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelbase.request;

import java.io.Serializable;

public class GlobalProductSupplierRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String productId;
    private String roomId;
    private String hotelId;
    private Integer supplierId;
    private String supplierHotelId;
    private String supplierRoomId;
    private String supplierProductId;
    private String supplierProductName;
    private String supplierProductNameCN;
    private String supplierBedDesc;
    private Integer hasWindow;
    private Integer breakfast;
    private Integer cancelType;
    private String operator;
    private Boolean del;

    public Long getId() {
        return this.id;
    }

    public String getProductId() {
        return this.productId;
    }

    public String getRoomId() {
        return this.roomId;
    }

    public String getHotelId() {
        return this.hotelId;
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

    public String getSupplierProductId() {
        return this.supplierProductId;
    }

    public String getSupplierProductName() {
        return this.supplierProductName;
    }

    public String getSupplierProductNameCN() {
        return this.supplierProductNameCN;
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

    public GlobalProductSupplierRequest setId(Long id) {
        this.id = id;
        return this;
    }

    public GlobalProductSupplierRequest setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public GlobalProductSupplierRequest setRoomId(String roomId) {
        this.roomId = roomId;
        return this;
    }

    public GlobalProductSupplierRequest setHotelId(String hotelId) {
        this.hotelId = hotelId;
        return this;
    }

    public GlobalProductSupplierRequest setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
        return this;
    }

    public GlobalProductSupplierRequest setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
        return this;
    }

    public GlobalProductSupplierRequest setSupplierRoomId(String supplierRoomId) {
        this.supplierRoomId = supplierRoomId;
        return this;
    }

    public GlobalProductSupplierRequest setSupplierProductId(String supplierProductId) {
        this.supplierProductId = supplierProductId;
        return this;
    }

    public GlobalProductSupplierRequest setSupplierProductName(String supplierProductName) {
        this.supplierProductName = supplierProductName;
        return this;
    }

    public GlobalProductSupplierRequest setSupplierProductNameCN(String supplierProductNameCN) {
        this.supplierProductNameCN = supplierProductNameCN;
        return this;
    }

    public GlobalProductSupplierRequest setSupplierBedDesc(String supplierBedDesc) {
        this.supplierBedDesc = supplierBedDesc;
        return this;
    }

    public GlobalProductSupplierRequest setHasWindow(Integer hasWindow) {
        this.hasWindow = hasWindow;
        return this;
    }

    public GlobalProductSupplierRequest setBreakfast(Integer breakfast) {
        this.breakfast = breakfast;
        return this;
    }

    public GlobalProductSupplierRequest setCancelType(Integer cancelType) {
        this.cancelType = cancelType;
        return this;
    }

    public GlobalProductSupplierRequest setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    public GlobalProductSupplierRequest setDel(Boolean del) {
        this.del = del;
        return this;
    }

    public GlobalProductSupplierRequest(Long id, String productId, String roomId, String hotelId, Integer supplierId, String supplierHotelId, String supplierRoomId, String supplierProductId, String supplierProductName, String supplierProductNameCN, String supplierBedDesc, Integer hasWindow, Integer breakfast, Integer cancelType, String operator, Boolean del) {
        this.id = id;
        this.productId = productId;
        this.roomId = roomId;
        this.hotelId = hotelId;
        this.supplierId = supplierId;
        this.supplierHotelId = supplierHotelId;
        this.supplierRoomId = supplierRoomId;
        this.supplierProductId = supplierProductId;
        this.supplierProductName = supplierProductName;
        this.supplierProductNameCN = supplierProductNameCN;
        this.supplierBedDesc = supplierBedDesc;
        this.hasWindow = hasWindow;
        this.breakfast = breakfast;
        this.cancelType = cancelType;
        this.operator = operator;
        this.del = del;
    }

    public GlobalProductSupplierRequest() {
    }
}
