package com.bingo.hotel.spa.intl.core.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRecord {
    // 访问的url
    String url;
    // code，类似于自关联表中的parentId
    int code;
    // 国家code
    String countryCode;
    // 区域code
    String regionCode;
    // 城市名称
    String name;
    // 所属省份
    String province;
    // 所属国家
    String country;
    // imageUrl
    String imageUrl;
    // 暂时没有用
    String location;
    // 暂时没用
    double value;
    // 暂时没用
    String type;

    @Override
    public String toString() {
        return "DataRecord{" +
                "url='" + url + '\'' +
                ", code=" + code +
                ", countryCode='" + countryCode + '\'' +
                ", regionCode='" + regionCode + '\'' +
                ", name='" + name + '\'' +
                ", province='" + province + '\'' +
                ", country='" + country + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", location='" + location + '\'' +
                ", value=" + value +
                ", type='" + type + '\'' +
                '}';
    }
}
