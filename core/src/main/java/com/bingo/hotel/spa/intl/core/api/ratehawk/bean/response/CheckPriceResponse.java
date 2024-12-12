package com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request.QueryProductRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 验价信息反参.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/10
 * @since : 1.0
 **/

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CheckPriceResponse implements BaseResponse {

    private List<QueryProductResponse.Hotels> hotels;

    private Original_request_params original_request_params;

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public class Original_request_params {

        private String checkin;
        private String checkout;
        private List<QueryProductRequest.Guests> guests;
        private String residency;

        public String getCheckin() {
            return checkin;
        }

        public void setCheckin(String checkin) {
            this.checkin = checkin;
        }

        public String getCheckout() {
            return checkout;
        }

        public void setCheckout(String checkout) {
            this.checkout = checkout;
        }

        public List<QueryProductRequest.Guests> getGuests() {
            return guests;
        }

        public void setGuests(List<QueryProductRequest.Guests> guests) {
            this.guests = guests;
        }

        public String getResidency() {
            return residency;
        }

        public void setResidency(String residency) {
            this.residency = residency;
        }
    }
}
