package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order.client.QueryOrderAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyOrderDetailResponse;
import com.trip.booking.spa.gateway.application.order.AbstractOrderQuerySyncSupportService;
import com.trip.booking.spa.gateway.domain.order.OrderQueryCommand;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 飞猪查单：{@code dis_order_id}（我方单号）足以定位（B5）。
 * <b>NOT_FOUND 现阶段永不判</b>——「订单不存在」的确定性码未实证前，
 * 把没查出来说成没有会招重复下单。
 */
@Slf4j
@Service("fliggyOrderQuerySyncService")
public class FliggyOrderQuerySyncServiceImpl extends AbstractOrderQuerySyncSupportService<FliggyOrderDetailResponse> {

    @Resource
    private FliggyProperties properties;

    @Override
    public FliggyOrderDetailResponse doOrderQuery(OrderQueryCommand command) {
        if (!properties.isConfigured() || StringUtils.isBlank(command.orderId())) {
            return null; // 模板兜底为「结果不确定」
        }
        ResponseResult<FliggyOrderDetailResponse> result = new QueryOrderAccess(properties)
                .access(QueryOrderAccess.callByOrderId(command.orderId(), properties.getDistributor()),
                        CallPurpose.ORDER);
        return result == null ? null : result.getData();
    }

    @Override
    public OrderQueryResult orderQueryRespConvert(FliggyOrderDetailResponse resp) {
        if (resp.isPlatformError()) {
            if (resp.isCredentialFailure()) {
                Monitor.recordOne(MetricNames.SUPPLIER_AUTH_CONFIG, MetricTags.of(SupplierSourceEnum.FLIGGY));
                log.error("[auth-config] 飞猪查单：我方凭据/配置病，需人工处理。platformError={}",
                        resp.platformError());
            }
            return OrderQueryResult.indeterminate("供应商平台拒绝了请求：" + resp.platformError());
        }
        if (!resp.isSucc()) {
            // 业务层失败：码义未核实，绝不判「不存在」——那是重复下单的入口
            log.warn("飞猪查单：业务层未通过,bizErrorCode={}", resp.bizErrorCode());
            return OrderQueryResult.indeterminate(
                    "供应商未返回订单详情（code=" + resp.bizErrorCode() + "），请稍后重试");
        }
        // order_status 枚举文档已列、各态真实报文未见（必测清单第 3 项）：状态映射待实测补齐，
        // 现阶段只回报「在」与供应商原文，不做任何状态翻译——识别不出的绝不猜
        return OrderQueryResult.found()
                .message(StringUtils.defaultIfBlank(resp.orderStatusDesc(), resp.orderStatus()))
                .build();
    }
}
