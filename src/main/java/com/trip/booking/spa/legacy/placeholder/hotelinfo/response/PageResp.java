/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelinfo.response;

import java.util.List;

public class PageResp<T> {
    private List<T> list;
    private long count;

    public List<T> getList() {
        return this.list;
    }

    public long getCount() {
        return this.count;
    }

    public PageResp<T> setList(List<T> list) {
        this.list = list;
        return this;
    }

    public PageResp<T> setCount(long count) {
        this.count = count;
        return this;
    }

    public PageResp(List<T> list, long count) {
        this.list = list;
        this.count = count;
    }

    public PageResp() {
    }
}
