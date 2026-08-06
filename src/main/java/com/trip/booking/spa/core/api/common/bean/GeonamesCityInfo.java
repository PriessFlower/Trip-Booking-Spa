package com.trip.booking.spa.core.api.common.bean;

import lombok.Data;

import java.io.Serializable;

/**
 * @description:Geonames城市信息
 * @author: dick_w
 * @date: 2025/1/15 10:19
 * @param:
 * @return:
 **/
@Data
public class GeonamesCityInfo implements Serializable {

//    {
//        "totalResultsCount": 7797,
//         "geonames": [{
//                "adminCode1": "28",
//                "lng": "135.80485",
//                "geonameId": 1855612,
//                "toponymName": "Nara-shi",
//                "countryId": "1861060",
//                "fcl": "P",
//                "population": 367353,
//                "countryCode": "JP",
//                "name": "Nara",
//                "fclName": "city, village,...",
//                "adminCodes1": {
//                    "ISO3166_2": "29"
//                },
//                "countryName": "Japan",
//                "fcodeName": "seat of a first-order administrative division",
//                "adminName1": "Nara",
//                "lat": "34.68505",
//                "fcode": "PPLA"
//          }]
//    }

    String toponymName;
    String name;
    String countryName;
    String lng;
    String lat;


}
