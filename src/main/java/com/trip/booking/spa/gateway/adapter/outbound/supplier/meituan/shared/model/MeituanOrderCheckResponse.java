package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 下单前校验响应（官方 hotel.oversea.order.check）。
 *
 * <p>失败一律 HTTP 200 + 体内码：2026-09-14 实测 {@code code=3}（房态不满足预订）就是
 * "这个产品订不到这么多间"的表达，{@code result} 为 null。这类码是<b>明确的不可订</b>，
 * 与"没问出结果"必须分开——判据在 {@code MeituanCheckPriceServiceImpl}。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeituanOrderCheckResponse implements BaseResponse {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private CheckResult result;

    @Override
    public boolean isSucc() {
        return Integer.valueOf(MeituanCodes.SUCCESS).equals(code);
    }

    @Override
    public boolean isEmptyResult() {
        return result == null || result.getPriceModelList() == null || result.getPriceModelList().isEmpty();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckResult {

        @JsonProperty("realRoomId")
        private Long realRoomId;

        @JsonProperty("goodsName")
        private String goodsName;

        /** 逐日价（分），口径仍是「每间每晚」——实测 1 间与 2 间返回同样的逐日价 */
        @JsonProperty("priceModelList")
        private List<MeituanPriceModel> priceModelList;

        /**
         * 阶梯退改。<b>这里的金额是"全部间数"口径</b>：2026-09-14 实测同一产品两晚，
         * 1 间回 15098/25164，2 间回 30146/50244，恰为两倍——与报价档的单间口径不同，
         * 混用即按一间的罚金去承诺多间的单子。
         */
        @JsonProperty("cpApply")
        private List<MeituanCpApply> cpApply;

        /** 1 不可取消；2 限时取消 */
        @JsonProperty("refundable")
        private Integer refundable;

        @JsonProperty("confirmType")
        private Integer confirmType;

        @JsonProperty("immediateConfirm")
        private Boolean immediateConfirm;

        @JsonProperty("mealType")
        private MeituanMealType mealType;

        @JsonProperty("checkPolicy")
        private String checkPolicy;
    }
}
