package com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request;

import lombok.Builder;

import java.io.Serializable;

/**
 * 验价入参.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/10
 * @since : 1.0
 **/

@Builder
public class CheckPriceRequest implements Serializable {

    private String book_hash;
    private String language;

    public void setBook_hash(String book_hash) {
        this.book_hash = book_hash;
    }

    public String getBook_hash() {
        return book_hash;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }
}
