package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 钉住 {@link ElongCheckPriceE2ETest} 反射到的那两个私有方法的签名。
 *
 * <p><b>为什么单列一条</b>：那个 e2e 由 {@code ELONG_E2E=1} 才启用，本地全量与 CI 都跳过它。
 * 于是它里面的反射调用<b>既躲过编译器、也躲过流水线</b>——签名一改就只剩"下次谁去打真艺龙"
 * 才会发现。2026-08-25 就是这么埋进去的：给 {@code queryHotelDetail} 加 {@code CallPurpose}
 * 参数的那次提交（97c9023）全量 408 全绿，直到在白名单机器上真跑 e2e 才炸出
 * NoSuchMethodException。
 *
 * <p>本条<b>不带任何环境开关</b>，也不打网络：只做签名查找。改了被反射的签名就地变红，
 * 修的成本落在改签名的那个人身上，而不是几周后那个想跑 e2e 的人身上。
 */
class E2EReflectionTargetsExistTest {

    @Test
    @DisplayName("e2e 反射到的私有方法签名必须仍然存在")
    void reflectedSignaturesStillExist() {
        assertDoesNotThrow(() -> ElongPriceServiceImpl.class.getDeclaredMethod("queryHotelDetail",
                        String.class, String.class, String.class, Integer.class, Integer.class, Integer.class,
                        List.class, CallPurpose.class),
                "ElongCheckPriceE2ETest 反射的 queryHotelDetail 签名已变。"
                        + "请同步改那条 e2e 里的 getDeclaredMethod 与 invoke 实参——"
                        + "它默认跳过，不改就等于那条用例永久坏掉且无人知道");

        assertDoesNotThrow(() -> ElongPriceServiceImpl.class.getDeclaredMethod("validate",
                        CheckPriceReq.class, String.class,
                        com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response
                                .ElongRatePlan.class,
                        List.class),
                "ElongCheckPriceE2ETest 反射的 validate 签名已变，同上");
    }
}
