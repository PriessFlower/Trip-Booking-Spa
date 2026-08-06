/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.core.placeholder.hotelinfo.request;

import com.trip.booking.spa.core.placeholder.hotelinfo.dto.BedInfoDTO;
import java.io.Serializable;
import java.util.List;

public class SupplierRoomBaseRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Integer supplierId;
    private String supplierRoomId;
    private String supplierHotelId;
    private String supplierRoomName;
    private String supplierRoomNameCN;
    private String description;
    private String area;
    private String floor;
    private Integer broadNet;
    private List<List<BedInfoDTO>> bedInfoList;
    private Integer capacity;
    private Integer hasBathroom;
    private Integer hasWindows;
    private Integer isSmoking;
    private Integer isAddBed;
    private String service;
    private String remarks;
    private Integer merger;
    private Boolean status;
    private String operator;

    public Long getId() {
        return this.id;
    }

    public Integer getSupplierId() {
        return this.supplierId;
    }

    public String getSupplierRoomId() {
        return this.supplierRoomId;
    }

    public String getSupplierHotelId() {
        return this.supplierHotelId;
    }

    public String getSupplierRoomName() {
        return this.supplierRoomName;
    }

    public String getSupplierRoomNameCN() {
        return this.supplierRoomNameCN;
    }

    public String getDescription() {
        return this.description;
    }

    public String getArea() {
        return this.area;
    }

    public String getFloor() {
        return this.floor;
    }

    public Integer getBroadNet() {
        return this.broadNet;
    }

    public List<List<BedInfoDTO>> getBedInfoList() {
        return this.bedInfoList;
    }

    public Integer getCapacity() {
        return this.capacity;
    }

    public Integer getHasBathroom() {
        return this.hasBathroom;
    }

    public Integer getHasWindows() {
        return this.hasWindows;
    }

    public Integer getIsSmoking() {
        return this.isSmoking;
    }

    public Integer getIsAddBed() {
        return this.isAddBed;
    }

    public String getService() {
        return this.service;
    }

    public String getRemarks() {
        return this.remarks;
    }

    public Integer getMerger() {
        return this.merger;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public String getOperator() {
        return this.operator;
    }

    public SupplierRoomBaseRequest setId(Long id) {
        this.id = id;
        return this;
    }

    public SupplierRoomBaseRequest setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
        return this;
    }

    public SupplierRoomBaseRequest setSupplierRoomId(String supplierRoomId) {
        this.supplierRoomId = supplierRoomId;
        return this;
    }

    public SupplierRoomBaseRequest setSupplierHotelId(String supplierHotelId) {
        this.supplierHotelId = supplierHotelId;
        return this;
    }

    public SupplierRoomBaseRequest setSupplierRoomName(String supplierRoomName) {
        this.supplierRoomName = supplierRoomName;
        return this;
    }

    public SupplierRoomBaseRequest setSupplierRoomNameCN(String supplierRoomNameCN) {
        this.supplierRoomNameCN = supplierRoomNameCN;
        return this;
    }

    public SupplierRoomBaseRequest setDescription(String description) {
        this.description = description;
        return this;
    }

    public SupplierRoomBaseRequest setArea(String area) {
        this.area = area;
        return this;
    }

    public SupplierRoomBaseRequest setFloor(String floor) {
        this.floor = floor;
        return this;
    }

    public SupplierRoomBaseRequest setBroadNet(Integer broadNet) {
        this.broadNet = broadNet;
        return this;
    }

    public SupplierRoomBaseRequest setBedInfoList(List<List<BedInfoDTO>> bedInfoList) {
        this.bedInfoList = bedInfoList;
        return this;
    }

    public SupplierRoomBaseRequest setCapacity(Integer capacity) {
        this.capacity = capacity;
        return this;
    }

    public SupplierRoomBaseRequest setHasBathroom(Integer hasBathroom) {
        this.hasBathroom = hasBathroom;
        return this;
    }

    public SupplierRoomBaseRequest setHasWindows(Integer hasWindows) {
        this.hasWindows = hasWindows;
        return this;
    }

    public SupplierRoomBaseRequest setIsSmoking(Integer isSmoking) {
        this.isSmoking = isSmoking;
        return this;
    }

    public SupplierRoomBaseRequest setIsAddBed(Integer isAddBed) {
        this.isAddBed = isAddBed;
        return this;
    }

    public SupplierRoomBaseRequest setService(String service) {
        this.service = service;
        return this;
    }

    public SupplierRoomBaseRequest setRemarks(String remarks) {
        this.remarks = remarks;
        return this;
    }

    public SupplierRoomBaseRequest setMerger(Integer merger) {
        this.merger = merger;
        return this;
    }

    public SupplierRoomBaseRequest setStatus(Boolean status) {
        this.status = status;
        return this;
    }

    public SupplierRoomBaseRequest setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    public SupplierRoomBaseRequest(Long id, Integer supplierId, String supplierRoomId, String supplierHotelId, String supplierRoomName, String supplierRoomNameCN, String description, String area, String floor, Integer broadNet, List<List<BedInfoDTO>> bedInfoList, Integer capacity, Integer hasBathroom, Integer hasWindows, Integer isSmoking, Integer isAddBed, String service, String remarks, Integer merger, Boolean status, String operator) {
        this.id = id;
        this.supplierId = supplierId;
        this.supplierRoomId = supplierRoomId;
        this.supplierHotelId = supplierHotelId;
        this.supplierRoomName = supplierRoomName;
        this.supplierRoomNameCN = supplierRoomNameCN;
        this.description = description;
        this.area = area;
        this.floor = floor;
        this.broadNet = broadNet;
        this.bedInfoList = bedInfoList;
        this.capacity = capacity;
        this.hasBathroom = hasBathroom;
        this.hasWindows = hasWindows;
        this.isSmoking = isSmoking;
        this.isAddBed = isAddBed;
        this.service = service;
        this.remarks = remarks;
        this.merger = merger;
        this.status = status;
        this.operator = operator;
    }

    public SupplierRoomBaseRequest() {
    }
}
