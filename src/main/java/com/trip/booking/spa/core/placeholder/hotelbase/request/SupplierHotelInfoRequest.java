/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  javax.validation.constraints.NotBlank
 *  javax.validation.constraints.NotNull
 */
package com.trip.booking.spa.core.placeholder.hotelbase.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class SupplierHotelInfoRequest {
    @NotBlank
    private String supplierHotelId;
    @NotNull
    private Integer supplierId;

    public SupplierHotelInfoRequest setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
        return this;
    }

    public SupplierHotelInfoRequest setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
        return this;
    }

    public String getSupplierHotelId() {
        return this.supplierHotelId;
    }

    public Integer getSupplierId() {
        return this.supplierId;
    }

    public SupplierHotelInfoRequest(String supplierHotelId, Integer supplierId) {
        this.supplierHotelId = supplierHotelId;
        this.supplierId = supplierId;
    }

    public SupplierHotelInfoRequest() {
    }
}
