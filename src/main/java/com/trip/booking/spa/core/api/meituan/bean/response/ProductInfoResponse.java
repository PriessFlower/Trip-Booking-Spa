package com.trip.booking.spa.core.api.meituan.bean.response;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
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
public class ProductInfoResponse implements BaseResponse {

    private List<Result> result;
    private Integer code;
    private Integer partnerId;
    private String message;

    @Override
    public boolean isSucc() {
        return code == 0;
    }

    @Override
    public boolean isEmptyResult() {
        return CollectionUtils.isEmpty(result);
    }

    @Getter
    @Setter
    public static class Result {

        private List<GoodsList> goodsList;
        private Long hotelId;
    }

    @Getter
    @Setter
    public static class GoodsList {

        private List<PriceModelList> priceModelList;
        private Long goodsId;
        private MealType mealType;
        private Long hotelId;
        private Integer invoiceMode;
        private Integer goodsSource;
        private Integer minGuestAge;
        private Integer quotedOccupancy;
        private Long realRoomId;
        private Integer rateOccupancy;
        private List<CpApply> cpApply;
        private List<List<OtaBeds>> otaBeds;
        private List<String> giftPackageList;
        private String checkPolicy;
        private Integer refundable;
        private Integer averagePrice;
        private String goodsName;
        private TargetUser targetUser;
        private Integer confirmType;
        private Integer smokingPreferences;
    }

    @Getter
    @Setter
    public static class PriceModelList {

        private String date;
        private Integer price;
    }

    @Getter
    @Setter
    public static class MealType {

        private Integer count;
        private String desc;
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

    @Getter
    @Setter
    public static class CpApply {

        private String endDate;
        private Integer refound;
        private Integer penalty;
        private String refoundStd;
        private String penaltyStd;
        private String endDateLocal;
    }
}
