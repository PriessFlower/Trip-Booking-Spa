package com.trip.booking.spa.legacy.travelconnect.service.impl;

import com.trip.booking.spa.legacy.placeholder.HotelInfoPlaceholderClient;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierHotelBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierProductBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.request.SupplierRoomBaseRequest;
import com.trip.booking.spa.legacy.placeholder.hotelinfo.result.InfoResult;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.legacy.travelconnect.access.HotelDetailAccess;
import com.trip.booking.spa.legacy.travelconnect.access.PreBookAccess;
import com.trip.booking.spa.legacy.travelconnect.access.SearchAccess;
import com.trip.booking.spa.legacy.travelconnect.adaptor.TravelconnectHotelAdaptor;
import com.trip.booking.spa.legacy.travelconnect.adaptor.TravelconnectProductAdaptor;
import com.trip.booking.spa.legacy.travelconnect.adaptor.TravelconnectRoomAdaptor;
import com.trip.booking.spa.legacy.travelconnect.bean.hotel.HotelDetailRequest;
import com.trip.booking.spa.legacy.travelconnect.bean.hotel.HotelDetailResponse;
import com.trip.booking.spa.legacy.travelconnect.bean.prebook.request.PrebookRequest;
import com.trip.booking.spa.legacy.travelconnect.bean.prebook.response.PrebookResponse;
import com.trip.booking.spa.legacy.travelconnect.bean.search.request.Roomorders;
import com.trip.booking.spa.legacy.travelconnect.bean.search.request.SearchRequest;
import com.trip.booking.spa.legacy.travelconnect.bean.search.response.SearchResponse;
import com.trip.booking.spa.legacy.travelconnect.service.TravelconnectHotelService;
import com.trip.booking.spa.legacy.travelconnect.utils.TravelConnectProductConvertUtil;
import com.trip.booking.spa.platform.util.JsonUtils;
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
    private HotelInfoPlaceholderClient hotelInfoPlaceholderClient;

    @Override
    public void getHotelCodeListByCity(String city, String checkIn, String checkOut) {
        int pageNum = 1;
        while (true) {
            SearchRequest hotelInfoReq = new SearchRequest();
            hotelInfoReq.setCitycode(city);
            hotelInfoReq.setCheckindate(checkIn);
            hotelInfoReq.setCheckoutdate(checkOut);
            hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(2).build()));
            hotelInfoReq.setPageindex(pageNum);
            hotelInfoReq.setPagesize(50);
            hotelInfoReq.setClientcurrency("HKD");
            String req = JsonUtils.writeObject2Json(hotelInfoReq);
            ResponseResult<SearchResponse> response = new SearchAccess(host, companyId, signKey).access(hotelInfoReq);
            int totalCount = response.getData().getData().getPagehotellist().getTotal_count();
            log.info("HotelList:" + response.getData().getData().getPagehotellist().getData_list().size());
            if (totalCount == 0) {
                break;
            }
            HotelDetailRequest hotelDetailRequest = new HotelDetailRequest();
            hotelDetailRequest.setLang("zh-cn");
            hotelDetailRequest.setHotelcodes(response.getData().getData().getPagehotellist().getData_list().stream().map(SearchResponse.DataBean.PagehotellistBean.DataListBean::getHotelcode).collect(Collectors.toList()));
            ResponseResult<HotelDetailResponse> hotelDetailResponse = new HotelDetailAccess(hotel, companyId, signKey).access(hotelDetailRequest);
            pageNum++;
            List<SupplierHotelBaseRequest> supplierHotelBaseRequests = TravelconnectHotelAdaptor.transform(hotelDetailResponse.getData());
            InfoResult infoResult = hotelInfoPlaceholderClient.saveHotelInfo(supplierHotelBaseRequests);
            for (SearchResponse.DataBean.PagehotellistBean.DataListBean dataListBean : response.getData().getData().getPagehotellist().getData_list()) {
                hotelInfoReq.setCitycode(city);
                hotelInfoReq.setHotelcodes(List.of(dataListBean.getHotelcode()));
                hotelInfoReq.setCheckindate(checkIn);
                hotelInfoReq.setCheckoutdate(checkOut);
                hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(2).build()));
                hotelInfoReq.setPageindex(pageNum);
                hotelInfoReq.setPagesize(50);
                hotelInfoReq.setClientcurrency("HKD");
                ResponseResult<SearchResponse> singleHotelResponse = new SearchAccess(host, companyId, signKey).access(hotelInfoReq);
                List<SupplierRoomBaseRequest> supplierRoomBaseRequest = TravelconnectRoomAdaptor.transform(singleHotelResponse.getData());
                InfoResult roomResult = hotelInfoPlaceholderClient.saveRoomInfo(supplierRoomBaseRequest);
                List<SupplierProductBaseRequest> supplierProductBaseRequest = TravelconnectProductAdaptor.transform(singleHotelResponse.getData());
                InfoResult productResult = hotelInfoPlaceholderClient.saveProductInfo(supplierProductBaseRequest);
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
        hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(priceReq.getAdultNum()).build()));
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
        hotelInfoReq.setRoomorders(List.of(Roomorders.builder().adultcount(priceReq.getAdultCount()).build()));
        hotelInfoReq.setPageindex(1);
        hotelInfoReq.setPagesize(50);
        hotelInfoReq.setClientcurrency("HKD");
        ResponseResult<SearchResponse> singleHotelResponse = new SearchAccess(host, companyId, signKey).access(hotelInfoReq);
        List<ProductRespDTO> respDTOList = TravelConnectProductConvertUtil.convertRatePlanVO(singleHotelResponse.getData());
        ProductRespDTO productRespDTO = respDTOList.stream().filter(productResp -> productResp.getProductId().equals(priceReq.getSProductId())).findFirst().orElse(null);
        PrebookRequest prebookRequest = new PrebookRequest();
        prebookRequest.setCitycode(priceReq.getSCityCode());
        prebookRequest.setHotelcode(priceReq.getSHotelId());
        prebookRequest.setCheckindate(priceReq.getCheckIn());
        prebookRequest.setCheckoutdate(priceReq.getCheckOut());
        PrebookRequest.RoomsBean roomsBean = new PrebookRequest.RoomsBean();
        roomsBean.setAdultcount(priceReq.getAdultCount());
        roomsBean.setPlansid(productRespDTO.getPlanSession());
        prebookRequest.setRooms(List.of(roomsBean));
        ResponseResult<PrebookResponse> prebookResponse = new PreBookAccess(prebook, companyId, signKey).access(prebookRequest);
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setPrebookResponse(prebookResponse.getData());
        searchResponse.setPlansId(productRespDTO.getPlanSession());
        return searchResponse;
    }
}
