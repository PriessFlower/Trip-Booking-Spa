package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 钉死验价模板的兜底语义。
 *
 * <p>这些用例守的是一条判断防线：<b>「我们不知道」不能表达成「供应商说不可订」</b>。
 * 四种成因（超时、供应商故障、产品下架、真满房）的正确处置互不相同，塌成一态后上游
 * 只能一律当作验不过，于是本可重新查价救回的点击被当成满房告知旅客。
 */
class AbstractCheckPriceSyncSupportServiceTest {

    private static CheckPriceReq req() {
        return CheckPriceReq.builder()
                .supplierId(10005)
                .sHotelId("10970375")
                .sProductId("211857685")
                .checkIn("2026-09-25")
                .checkOut("2026-09-26")
                .roomNum(1)
                .totalPrice(188012)
                .build();
    }

    /** 供应商无响应不等于不可订 */
    @Test
    void nullSupplierResponseIsReportedAsIndeterminate() {
        CheckPriceRespDTO resp = new StubCheckPriceService(Behaviour.RETURN_NULL).checkPrice(req());

        assertNotNull(resp, "兜底禁止返回 null：控制层会把它表达为接口错误，上游只能笼统当作验不过");
        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
    }

    /** 抛异常同样不足以断定不可订 */
    @Test
    void exceptionIsReportedAsIndeterminate() {
        CheckPriceRespDTO resp = new StubCheckPriceService(Behaviour.THROW).checkPrice(req());

        assertNotNull(resp);
        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
    }

    /** 转换器返回空属不可判 */
    @Test
    void unconvertibleResponseIsReportedAsIndeterminate() {
        CheckPriceRespDTO resp = new StubCheckPriceService(Behaviour.CONVERT_TO_NULL).checkPrice(req());

        assertNotNull(resp);
        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
    }

    /** 实现漏填分态时按 INDETERMINATE 兜底，避免默认值悄悄退化成「可订」 */
    @Test
    void missingOutcomeDefaultsToIndeterminate() {
        CheckPriceRespDTO resp = new StubCheckPriceService(Behaviour.OMIT_OUTCOME).checkPrice(req());

        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome(),
                "漏填时若默认成可订，上游会去下一个必定失败的单");
    }

    /** 供应商明确说满房时，实现有权判 SOLD_OUT，模板不得篡改 */
    @Test
    void soldOutIsPreserved() {
        CheckPriceRespDTO resp = new StubCheckPriceService(Behaviour.SOLD_OUT).checkPrice(req());

        assertEquals(CheckPriceOutcome.SOLD_OUT, resp.getOutcome());
    }

    /**
     * 陈码必须原样透出为 RATE_DEAD，不得被模板折叠成 INDETERMINATE。
     * 二者的处置相反：前者应重新查价，后者应稍后重试同一请求。
     */
    @Test
    void rateDeadIsNotFoldedIntoIndeterminate() {
        CheckPriceRespDTO resp = new StubCheckPriceService(Behaviour.RATE_DEAD).checkPrice(req());

        assertEquals(CheckPriceOutcome.RATE_DEAD, resp.getOutcome(),
                "折叠成不确定会让上游反复重试一个必定失败的产品标识");
    }

    @Test
    void bookableIsPassedThrough() {
        CheckPriceRespDTO resp = new StubCheckPriceService(Behaviour.BOOKABLE).checkPrice(req());

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertEquals("of_stub", resp.getOfferId());
    }

    private enum Behaviour {
        RETURN_NULL, THROW, CONVERT_TO_NULL, OMIT_OUTCOME, SOLD_OUT, RATE_DEAD, BOOKABLE
    }

    private static final class StubCheckPriceService extends AbstractCheckPriceSyncSupportService<String> {

        private final Behaviour behaviour;

        private StubCheckPriceService(Behaviour behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public String doCheckPrice(CheckPriceReq checkPriceReq) {
            switch (behaviour) {
                case RETURN_NULL:
                    return null;
                case THROW:
                    throw new IllegalStateException("read timed out");
                default:
                    return "raw-supplier-response";
            }
        }

        @Override
        public CheckPriceRespDTO checkPriceRespConvert(String raw) {
            switch (behaviour) {
                case CONVERT_TO_NULL:
                    return null;
                case OMIT_OUTCOME:
                    return CheckPriceRespDTO.builder().salePrice(188012).build();
                case SOLD_OUT:
                    return CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.SOLD_OUT).build();
                case RATE_DEAD:
                    return CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.RATE_DEAD).build();
                default:
                    return CheckPriceRespDTO.builder()
                            .outcome(CheckPriceOutcome.BOOKABLE)
                            .offerId("of_stub")
                            .build();
            }
        }
    }
}
