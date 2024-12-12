package com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 查询产品信息入参.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/09
 * @since : 1.0
 **/

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
public class QueryProductRequest {

    private String checkin;
    private String checkout;
    private String residency;
    private String language;
    private List<Guests> guests;
    private Integer hid;
    private String currency;

    @Builder
    public static class Guests {

        private int adults;
        private List<String> children;
        public void setAdults(int adults) {
            this.adults = adults;
        }
        public int getAdults() {
            return adults;
        }

        public void setChildren(List<String> children) {
            this.children = children;
        }
        public List<String> getChildren() {
            return children;
        }
    }
}
