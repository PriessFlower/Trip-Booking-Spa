package com.bingo.hotel.spa.intl.core.api.travelconnect.service.impl;

import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.info.intl.cli.result.InfoResult;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.travelconnect.access.HotelDetailAccess;
import com.bingo.hotel.spa.intl.core.api.travelconnect.access.PreBookAccess;
import com.bingo.hotel.spa.intl.core.api.travelconnect.access.SearchAccess;
import com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor.TravelconnectHotelAdaptor;
import com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor.TravelconnectProductAdaptor;
import com.bingo.hotel.spa.intl.core.api.travelconnect.adaptor.TravelconnectRoomAdaptor;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.hotel.HotelDetailRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.hotel.HotelDetailResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.prebook.request.PrebookRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.prebook.response.PrebookResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.request.Roomorders;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.request.SearchRequest;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.service.TravelconnectHotelService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.utils.TravelConnectProductConvertUtil;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TravelconnectHotelServiceImpl implements TravelconnectHotelService {
    @Value("${travelconnect.url.search}")
    String host;

    @Value("${travelconnect.url.hotel}")
    String hotel;

    @Value("${travelconnect.url.prebook}")
    String prebook;

    @Value("${travelconnect.companyId}")
    String companyId;

    @Value("${travelconnect.signKey}")
    String signKey;

    @Autowired
    private HotelInfoIntlClient hotelInfoIntlClient;

    @Override
    public void getHotelCodeListByCity(String city) {
        int pageNum = 1;
        while (true) {
            SearchRequest hotelInfoReq = new SearchRequest();
            hotelInfoReq.setCitycode("184245");
            hotelInfoReq.setCheckindate("2024-07-14 00:00:00");
            hotelInfoReq.setCheckoutdate("2024-07-15 00:00:00");
            hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(2).build()));
            hotelInfoReq.setPageindex(pageNum);
            hotelInfoReq.setPagesize(50);
            hotelInfoReq.setClientcurrency("HKD");
            ResponseResult<SearchResponse> response = new SearchAccess(host, companyId, signKey).access(hotelInfoReq);
            int totalCount = response.getData().getData().getPagehotellist().getTotal_count();
            log.info("HotelList:" + response.getData().getData().getPagehotellist().getData_list().size());
            HotelDetailRequest hotelDetailRequest = new HotelDetailRequest();
            hotelDetailRequest.setLang("zh-cn");
            hotelDetailRequest.setHotelcodes(response.getData().getData().getPagehotellist().getData_list().stream().map(SearchResponse.DataBean.PagehotellistBean.DataListBean::getHotelcode).collect(Collectors.toList()));
            ResponseResult<HotelDetailResponse> hotelDetailResponse = new HotelDetailAccess(hotel, companyId, signKey).access(hotelDetailRequest);
            pageNum++;
            List<SupplierHotelBaseRequest> supplierHotelBaseRequests = TravelconnectHotelAdaptor.transform(hotelDetailResponse.getData());
            InfoResult infoResult = hotelInfoIntlClient.saveHotelInfo(supplierHotelBaseRequests);
            for (SearchResponse.DataBean.PagehotellistBean.DataListBean dataListBean : response.getData().getData().getPagehotellist().getData_list()) {
                hotelInfoReq.setCitycode("184245");
                hotelInfoReq.setHotelcodes(List.of(dataListBean.getHotelcode()));
                hotelInfoReq.setCheckindate("2024-07-14 00:00:00");
                hotelInfoReq.setCheckoutdate("2024-07-15 00:00:00");
                hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(2).build()));
                hotelInfoReq.setPageindex(pageNum);
                hotelInfoReq.setPagesize(50);
                hotelInfoReq.setClientcurrency("HKD");
                ResponseResult<SearchResponse> singleHotelResponse = new SearchAccess(host, companyId, signKey).access(hotelInfoReq);
                List<SupplierRoomBaseRequest> supplierRoomBaseRequest = TravelconnectRoomAdaptor.transform(singleHotelResponse.getData());
                InfoResult roomResult = hotelInfoIntlClient.saveRoomInfo(supplierRoomBaseRequest);
                List<SupplierProductBaseRequest> supplierProductBaseRequest = TravelconnectProductAdaptor.transform(singleHotelResponse.getData());
                InfoResult productResult = hotelInfoIntlClient.saveProductInfo(supplierProductBaseRequest);
            }
            log.info("推送次数：" + pageNum);
            if (pageNum == totalCount) {
                break;
            }
        }
    }

    @Override
    public SearchResponse getHotelPrice(PriceReq priceReq, String sHotelId) {
        SearchResponse response = new SearchResponse();
        SearchRequest hotelInfoReq = new SearchRequest();
        hotelInfoReq.setCitycode(priceReq.getSuppliers().get(0).getSCityCode());
        hotelInfoReq.setHotelcodes(List.of(sHotelId));
        hotelInfoReq.setCheckindate(priceReq.getCheckIn());
        hotelInfoReq.setCheckoutdate(priceReq.getCheckout());
        hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(2).build()));
        hotelInfoReq.setPageindex(1);
        hotelInfoReq.setPagesize(50);
        hotelInfoReq.setClientcurrency("HKD");
        ResponseResult<SearchResponse> singleHotelResponse = new SearchAccess(host, companyId, signKey).access(hotelInfoReq);
        response = singleHotelResponse.getData();
        return response;
    }

    @Override
    public SearchResponse checkPrice(CheckPriceReq priceReq) {
        SearchRequest hotelInfoReq = new SearchRequest();
        hotelInfoReq.setCitycode(priceReq.getSCityCode());
        hotelInfoReq.setHotelcodes(List.of(priceReq.getSHotelId()));
        hotelInfoReq.setCheckindate(priceReq.getCheckIn());
        hotelInfoReq.setCheckoutdate(priceReq.getCheckOut());
        hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(2).build()));
        hotelInfoReq.setPageindex(1);
        hotelInfoReq.setPagesize(50);
        hotelInfoReq.setClientcurrency("HKD");
        ResponseResult<SearchResponse> singleHotelResponse = new SearchAccess(host, companyId, signKey).access(hotelInfoReq);
        List<ProductRespDTO> respDTOList = TravelConnectProductConvertUtil.convertRatePlanVO(singleHotelResponse.getData());
        ProductRespDTO productRespDTO = respDTOList.stream().filter(productResp -> productResp.getProductId().equals(priceReq.getSProductId())).findFirst().orElse(null);
        PrebookRequest prebookRequest = new PrebookRequest();
        prebookRequest.setCitycode("184245");
        prebookRequest.setHotelcode(priceReq.getSHotelId());
        prebookRequest.setCheckindate(priceReq.getCheckIn());
        prebookRequest.setCheckoutdate(priceReq.getCheckOut());
        PrebookRequest.RoomsBean roomsBean = new PrebookRequest.RoomsBean();
        roomsBean.setAdultcount(2);
        roomsBean.setPlansid(productRespDTO.getPlanSession());
        prebookRequest.setRooms(List.of(roomsBean));
        ResponseResult<PrebookResponse> prebookResponse = new PreBookAccess(prebook, companyId, signKey).access(prebookRequest);
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setPrebookResponse(prebookResponse.getData());
        return searchResponse;
    }
}
