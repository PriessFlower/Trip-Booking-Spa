/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.core.placeholder.hotelbase.response;

public class GetCityInfoBySupplierHotelIdResponse {
    private String cityName;
    private String countryName;

    public String getCityName() {
        return this.cityName;
    }

    public String getCountryName() {
        return this.countryName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof GetCityInfoBySupplierHotelIdResponse)) {
            return false;
        }
        GetCityInfoBySupplierHotelIdResponse other = (GetCityInfoBySupplierHotelIdResponse)o;
        if (!other.canEqual(this)) {
            return false;
        }
        String this$cityName = this.getCityName();
        String other$cityName = other.getCityName();
        if (this$cityName == null ? other$cityName != null : !this$cityName.equals(other$cityName)) {
            return false;
        }
        String this$countryName = this.getCountryName();
        String other$countryName = other.getCountryName();
        return !(this$countryName == null ? other$countryName != null : !this$countryName.equals(other$countryName));
    }

    protected boolean canEqual(Object other) {
        return other instanceof GetCityInfoBySupplierHotelIdResponse;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        String $cityName = this.getCityName();
        result = result * 59 + ($cityName == null ? 43 : $cityName.hashCode());
        String $countryName = this.getCountryName();
        result = result * 59 + ($countryName == null ? 43 : $countryName.hashCode());
        return result;
    }

    public String toString() {
        return "GetCityInfoBySupplierHotelIdResponse(cityName=" + this.getCityName() + ", countryName=" + this.getCountryName() + ")";
    }

    public GetCityInfoBySupplierHotelIdResponse(String cityName, String countryName) {
        this.cityName = cityName;
        this.countryName = countryName;
    }

    public GetCityInfoBySupplierHotelIdResponse() {
    }
}
