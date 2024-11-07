package com.bingo.hotel.spa.intl.cli;

import com.bingo.hotel.spa.intl.cli.dto.BookingRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.CancelRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.OrderRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ResponseDTO;
import com.bingo.hotel.spa.intl.cli.fallback.SPAFeignClientFallbackFactory;
import com.bingo.hotel.spa.intl.cli.seq.BookingReq;
import com.bingo.hotel.spa.intl.cli.seq.CancelReq;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.OrderQueryReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PushProductsReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "bingo-hotel-spa-intl",
        path = "/client/spa",
        fallbackFactory = SPAFeignClientFallbackFactory.class)
public interface SPAFeignClient {

    /**
     * 价格数据
     *
     * @param priceReq
     * @return
     */
    @PostMapping(value = "/price")
    ResponseDTO<List<ProductRespDTO>> queryPrice(@RequestBody @Validated PriceReq priceReq);

    /**
     * 验价
     *
     * @param checkPriceReq
     * @return
     */
    @PostMapping(value = "/check")
    ResponseDTO<CheckPriceRespDTO> checkPrice(@RequestBody @Validated CheckPriceReq checkPriceReq);

    /**
     * 下单
     *
     * @param bookingReq
     * @return
     */
    @PostMapping(value = "/booking")
    ResponseDTO<BookingRespDTO> booking(@RequestBody @Validated BookingReq bookingReq);

    /**
     * 取消
     *
     * @param cancelReq
     * @return
     */
    @PostMapping(value = "/cancel")
    ResponseDTO<CancelRespDTO> cancel(@RequestBody @Validated CancelReq cancelReq);

    /**
     * 查询订单
     *
     * @param orderQueryReq
     * @return
     */
    @PostMapping(value = "/order")
    ResponseDTO<OrderRespDTO> orderQuery(@RequestBody @Validated OrderQueryReq orderQueryReq);

    /**
     * 推送产品价格和库存
     *
     * @param pushProductsReq
     * @return
     */
    @PostMapping(value = "/push/priceAndInventory")
    ResponseDTO pushPriceAndInventory(@RequestBody @Validated PushProductsReq pushProductsReq);

    /**
     * expedia查询某个城市下所有酒店id
     *
     * @param cityId
     * @return
     */
    @PostMapping(value = "/query/expediaHotelIdByCity")
    ResponseDTO<List<String>> queryExpediaHotelIdByCity(String cityId);
}
