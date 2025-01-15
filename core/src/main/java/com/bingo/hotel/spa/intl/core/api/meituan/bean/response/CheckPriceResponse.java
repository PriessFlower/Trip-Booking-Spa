package com.bingo.hotel.spa.intl.core.api.meituan.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * 产品信息反参结构.
 *
 * @author : hanJH
 * @version : 1.0 2025/01/07
 * @since : 1.0
 **/

@Getter
@Setter
public class CheckPriceResponse implements BaseResponse {

    private Result result;
    private Integer code;
    private Integer partnerId;
    private String message;

    @Override
    public boolean isSucc() {
        return code == 0;
    }

    @Override
    public boolean isEmptyResult() {
        return null == result;
    }

    @Getter
    @Setter
    public static class Result {

        private List<ProductInfoResponse.PriceModelList> priceModelList;
        private String preferenceGroupList;
        private String goodsName;
        private List<List<OtaBeds>> otaBeds;
        private ProductInfoResponse.MealType mealType;
        private Long realRoomId;
        private Integer refundable;
        private List<ProductInfoResponse.CpApply> cpApply;
        private String checkPolicy;
        private boolean immediateConfirm;
        private Integer confirmType;
        private OhTargetUser ohTargetUser;
        private String fxOrderId;
    }

    @Getter
    @Setter
    public static class OhTargetUser {

        private int targetUserRule;
        private List<String> targetUserRestrictionList;
    }

    @Getter
    @Setter
    public static class OtaBeds {

        private Integer otaBedCount;
        private String otaBedDesc;
        private String otaBedType;
    }

    @Getter
    @Setter
    public static class TargetUser {

        private List<TargetUserRestrictionList> targetUserRestrictionList;
        private Integer targetUserRule;
    }

    @Getter
    @Setter
    public static class TargetUserRestrictionList {

        private Integer restrictionType;
        private String restrictionName;
        private String restrictionNameEn;
        private String restrictionNameCode;
    }
}
