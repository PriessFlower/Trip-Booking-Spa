package com.bingo.hotel.spa.intl.cli.fallback;


import com.bingo.hotel.spa.intl.cli.SPAFeignClient;
import com.bingo.hotel.spa.intl.cli.dto.BookingRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.CancelRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.OrderRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.ResponseDTO;
import com.bingo.hotel.spa.intl.cli.seq.BookingReq;
import com.bingo.hotel.spa.intl.cli.seq.CancelReq;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.OrderQueryReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PushProductsReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SPAFeignClientFallbackFactory implements FallbackFactory<SPAFeignClient> {

    @Override
    public SPAFeignClient create(Throwable cause) {

        return new SPAFeignClient() {

            @Override
            public ResponseDTO<List<ProductRespDTO>> queryPrice(PriceReq priceReq) {
                return ResponseDTO.error("服务异常，请稍等");
            }

            @Override
            public ResponseDTO<CheckPriceRespDTO> checkPrice(CheckPriceReq checkPriceReq) {
                return ResponseDTO.error("服务异常，请稍等");
            }

            @Override
            public ResponseDTO<BookingRespDTO> booking(BookingReq bookingReq) {
                return ResponseDTO.error("服务异常，请稍等");
            }

            @Override
            public ResponseDTO<CancelRespDTO> cancel(CancelReq cancelReq) {
                return ResponseDTO.error("服务异常，请稍等");
            }

            @Override
            public ResponseDTO<OrderRespDTO> orderQuery(OrderQueryReq orderQueryReq) {
                return ResponseDTO.error("服务异常，请稍等");
            }

            @Override
            public ResponseDTO pushPriceAndInventory(PushProductsReq pushProductsReq) {
                return ResponseDTO.error("服务异常，请稍等");
            }

            @Override
            public ResponseDTO<List<String>> queryExpediaHotelIdByCity(String cityId) {
                return ResponseDTO.error("服务异常，请稍等");
            }
        };
    }
}
