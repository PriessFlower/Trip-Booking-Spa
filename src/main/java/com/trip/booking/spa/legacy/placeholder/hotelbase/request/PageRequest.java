/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelbase.request;

import java.io.Serializable;

public class PageRequest
implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer pageSize = 10;
    private Integer pageNum = 1;

    public Integer getPageSize() {
        return this.pageSize;
    }

    public Integer getPageNum() {
        return this.pageNum;
    }

    public PageRequest setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public PageRequest setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
        return this;
    }

    public PageRequest(Integer pageSize, Integer pageNum) {
        this.pageSize = pageSize;
        this.pageNum = pageNum;
    }

    public PageRequest() {
    }
}
