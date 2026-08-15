/*
 * Fastpayhotels Booking API v2
 *  Fastpayhotels Booking API is a specification designed for API integration customers in order to connect to Fastpayhotels Booking Engine System.    Featuring multiple breaking changes into Booking API, v2 will reside in its own service URL, so customer can switch from v1 at their own rate  As a rule, all URL for BookingAPI v2 are the same as legacy V1, just by adding v2 to URL address. Some API methods are totally interchangeable, like Catalogue and Booking folders, and will work seamlessly even if customer make cross calls from v2 to v1, but we do not ensure that this will be the same in the future, so a recommendation for full  migration to v2 is indeed the best road of action  Thus the API method url would be:  https://[ENVIRONMENT_URL]/api/v2/[controller][method]  For example for Search method in production environment, no custom customer URL:      ** https://avail.fastpayhotels.net/api/v2/booking/search **  Dev environment would follow the same URL scheme   # **What's new in 2.0.0 version?**  FPH BookingAPI v2 is a new evolution from legacy 1.x API, introducing a brand new workflow for search and book. Based on previous improvements, v2 increases speed and reliability further, and improves result quality  Main new features are:  - **Fast Search feature.**   - New method for fast searching multiple hotels.   - Accepting multiple hotel codes or area, return per-room/per-mealplan cheaper results per single hotel.   - Ideal for first high-speed low-detail preview in customer search   - Proceed to classic Avail Request after customer select suitable hotels    - **Reduced Product Token**   - To replace old too heavy Reservation Token in Availability Request, BookingAPIv2 will return a lighter token called \"Product Code\". This will improve speed and traffic size    - **New LiveCheck Method**    - To replace legacy Prebook method, a new Live Check method allowing Product Code is introduced.   - This new method will allow for much more detailed response than previous Prebook method, including booking rules and other product detailed info   - The result is guaranteed to be checked against supplier system (for selected chains) and ready to proceed to Book phase     **Workflows**  Fastpayhotels Booking API has three main workflows, hotel portfolio and catalogue, availability queries, and real time booking methods.  These three workflows are hosted in different URL endpoints so implementors should take into account that different security token retrieval must be performed for each workflow   As per development integration phase, URL endpoints would be:  - Portfolio and catalogue endpoint: *https://dev-catalogue.fastpayhotels.net/api/v2* - Availability requests endpoint: *https://dev-avail.fastpayhotels.net/api/v2* - Booking engine access endpoint: *https://dev-booking.fastpayhotels.net/api/v2*   **Mandatory HTTPS protocol** All request to API must be performed through https:// protocol    **Security based on OAuth 2.0 technology**. The API has two main roles **Customer API** and **Agency** and the authentication protocol is token-based. Once you get a valid token, you retain access as long as the token remains valid.  The token is invalidated if the access credentials change or the token expires.  **Authentication URL:** .../security/token  For hotel portfolio and catalogue workflow you can get a Customer API role access token by using client credential **client Id** and **client secret**. **Example:** \"grant_type=client_credentials&scope=integrations_api&&client_id=example.customer.es&client_secret=wPEfpVQQDkRD3nbaktoR0g5a%2BXMfwBZvWv7wjGeKwVc%3D&version=1  For avail and real-time booking workflow you can get an Agency role access token using user credentials  **username** and **password** and client credential **client Id** and **client secret**. **Example:** \"grant_type=password&username=username&password=password&client_id=example.customer.es&client_secret=wPEfpVQQDkRD3nbaktoR0g5a%2BXMfwBZvWv7wjGeKwVc%3D&version=1\"  **To ensure max compatibility, make sure to use x-form-www-urlencoded post**  **The access token is valid for a year.**   The Agency role token can also access the hotel portfolio workflow too.  Credentials and environment URLs will be provided by Fastpayhotels during development.     **Cancellation Policies**    Cancellation policy defines that penalty will be charged when a guest cancels the booking at certain advance time range. The penalty is related to No-show or a time range before check-in.    Descriptive Penalty Type: when received as descriptive it means that we are working on codifying the penalty rules.    **Time Range**    No Show: The customer doesn’t cancel the booking or cancel it after arrival day. The first penalty without advance day is the penalty of no show. Deadline on arrival day or No-show: Many hotels have a deadline on arrival day and will apply different penalty when the customer cancels the booking after the deadline. The format of deadline is 4 AM/PM. Deadline always will appear after **#** Example: 100P_7D15P#3PM. If deadline does not appear, the default deadline time will be 12AM accommodation local time. Advance day: The format is 1D, 2D, 7D or AD. 1D means 24 hours before ‘Deadline’. AD means unlimited advance day.  Advance hour: The time format is 1H,75H or 150H. Means X hours before ‘Deadline’. Advance day and hour can be a time range: In this case is separate by a T. Example: 2DT1D10P. Timezone: The timezone of the deadline or advance day is accommodation local timezone.  **Penalty Type**  Percentage: will charge the percentage of: Booking Value 80P, Most expensive night MEN100P or Nights’ average AVG50P. Amount: will charge fix amount. The currency of this amount will be the same as booking rate. For example, 120, 245.75. Nights: night fee to be charged. For example, 1N, 2N.  **Examples** 1. 100P_AD100P => Non Refundable. 2. 0 or 0P or AD0_0 or AD0P_0P => Free cancellation. 3. 100P_0D80P_1D70P_5D2N_AD0#9PM (Booking Date on 2021-03-01, check-in on 2021-08-10)   All advance day/hour penalty will apply after 9PM in accommodation Time Zone.   100P => is the penalty of no show. It means 100 percent of the whole stay will be charged if customer doesn't cancel the booking or doesn't arrive at hotel.   0D80P => defines the penalty between 9PM on arrival day (0D) and no show. It means 80 percent of whole stay will be charge if booking is cancelled after 9PM, 2021-08-10.   1D70P => define the penalty between 9PM, 2021-08-09 and 9PM, 2021-08-10. It means 70 percent of whole stay will be charged.   5D2N => defines the penalty between 9PM, 2021-08-05 and 9PM, 2021-08-09. It means 2 nights will be charged.   AD0 => defines the penalty between booking date and 9PM, 2021-08-05. Free cancellation.
 *
 * OpenAPI spec version: 2.0.1
 * Contact: boarding@fastpayhotels.com
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package com.trip.booking.spa.legacy.fastpay.bean.response;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Data;

/**
 * HotelDetailResponse
 */

public class HotelDetailResponse implements BaseResponse {

    private String messageID;

    private HotelDetailInfo hotelDetail;

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    public HotelDetailInfo getHotelDetail() {
        return hotelDetail;
    }

    public void setHotelDetail(HotelDetailInfo hotelDetail) {
        this.hotelDetail = hotelDetail;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}

