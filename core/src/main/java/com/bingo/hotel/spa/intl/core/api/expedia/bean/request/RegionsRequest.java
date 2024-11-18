package com.bingo.hotel.spa.intl.core.api.expedia.bean.request;

import lombok.Builder;

/**
 * 查询地理信息请求.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/

@Builder
public class RegionsRequest {

    private String include;

    private String type;

    private String region_id;

    private String ancestor_id;

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRegion_id() {
        return region_id;
    }

    public void setRegion_id(String region_id) {
        this.region_id = region_id;
    }

    public String getAncestor_id() {
        return ancestor_id;
    }

    public void setAncestor_id(String ancestor_id) {
        this.ancestor_id = ancestor_id;
    }
}
