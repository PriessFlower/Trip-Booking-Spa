package com.bingo.hotel.spa.intl.core.api.expedia.adaptor;

import com.bingo.hotel.base.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.base.intl.cli.dto.GlobalHotelBaseExtendDTO;
import com.bingo.hotel.base.intl.cli.dto.GlobalHotelPictureDTO;
import com.bingo.hotel.base.intl.cli.request.GlobalProductSupplierRequest;
import com.bingo.hotel.base.intl.cli.request.HotelDetailsRequest;
import com.bingo.hotel.base.intl.cli.request.RoomBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelStaticInfo;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ExpediaStaticInfoAdaptor {

    public static HotelDetailsRequest transformBaseHotelReq(HotelStaticInfo resultUS, HotelStaticInfo resultCN) {
        HotelDetailsRequest hotelDetailsRequest = new HotelDetailsRequest()
                .setHotelId(resultUS.getProperty_id())
                .setHotelName(resultUS.getName())
                .setHotelNameCN(resultCN.getName())
                .setTelephone(resultUS.getPhone())
                .setPostCode(resultUS.getAddress().getPostal_code())
                .setAddress(resultUS.getAddress().getLine_1())
                .setAddressCN(resultCN.getAddress().getLine_1())
                .setCountryCode(resultUS.getAddress().getCountry_code())
                .setCityName(resultUS.getAddress().getCity())
                .setCityNameCN(resultCN.getAddress().getCity())
                .setStar(null == resultUS.getRatings() || null == resultUS.getRatings().getProperty() || StringUtils.isBlank(resultUS.getRatings().getProperty().getRating()) ? "0" :
                        resultUS.getRatings().getProperty().getRating())
                .setScore(null == resultUS.getRatings() || null == resultUS.getRatings().getGuest() || StringUtils.isBlank(resultUS.getRatings().getGuest().getOverall()) ? "0" :
                        resultUS.getRatings().getGuest().getOverall())
                .setLongitude(String.valueOf(resultUS.getLocation().getCoordinates().getLongitude()))
                .setLatitude(String.valueOf(resultUS.getLocation().getCoordinates().getLatitude()))
                .setGroup(null == resultUS.getChain() ? "" : resultUS.getChain().getName())
                .setBrand(null == resultUS.getBrand() ? "" : resultUS.getBrand().getName());

        //图片集合
        List<GlobalHotelPictureDTO> globalHotelPictures = new ArrayList<>();
        //图片
        if (CollectionUtils.isNotEmpty(resultUS.getImages())) {
            resultUS.getImages().forEach(images -> {
                HotelStaticInfo.UrlInfo urlInfo = null == images.getLinks().get("1000px") ? images.getLinks().get("350px") : images.getLinks().get("1000px");
                if (null != urlInfo) {
                    GlobalHotelPictureDTO globalHotelPictureDTO = new GlobalHotelPictureDTO()
                            .setHotelId(resultUS.getProperty_id())
                            .setType("hotel")
                            .setName(images.getCaption())
                            .setSort(images.getHero_image() ? 0 : 1)
                            .setUrl(urlInfo.getHref());
                    globalHotelPictures.add(globalHotelPictureDTO);
                }
            });
        }
        hotelDetailsRequest.setGlobalHotelPictureDTOS(globalHotelPictures);
        //酒店附属信息集合
        List<GlobalHotelBaseExtendDTO> globalHotelBaseExtends = new ArrayList<>();
        //附属信息
        //英文
        Map<String, String> checkinUS = resultUS.getCheckin();
        GlobalHotelBaseExtendDTO globalHotelBaseExtendUS = new GlobalHotelBaseExtendDTO()
                .setHotelId(resultUS.getProperty_id())
                .setLanguage("USD")
                .setCheckIn(StringUtils.isBlank(checkinUS.get("24_hour")) ? checkinUS.get("begin_time") + "-" + checkinUS.get("end_time") : checkinUS.get(
                        "24_hour"))
                .setCheckOut(null == resultUS.getCheckout() ? "" : resultUS.getCheckout().getTime())
                .setInstructions(checkinUS.get("instructions") + checkinUS.get("special_instructions"))
                .setMinAge(checkinUS.containsKey("min_age") ? "Guests must be at least " + checkinUS.get("min_age") + " years old" : "No")
                .setFees(null == resultUS.getFees() ? "" : convertNull(resultUS.getFees().getMandatory()) + convertNull(resultUS.getFees().getOptional()))
                .setPolicies(null == resultUS.getPolicies() ? "" : resultUS.getPolicies().getKnow_before_you_go())
                .setDescriptions(null == resultUS.getDescriptions() ? "" : convertDescriptions(resultUS.getDescriptions()));
        globalHotelBaseExtends.add(globalHotelBaseExtendUS);
        //中文
        Map<String, String> checkinCN = resultCN.getCheckin();
        GlobalHotelBaseExtendDTO globalHotelBaseExtendCN = new GlobalHotelBaseExtendDTO()
                .setHotelId(resultCN.getProperty_id())
                .setLanguage("CNY")
                .setCheckIn(StringUtils.isBlank(checkinCN.get("24_hour")) ? checkinCN.get("begin_time") + "-" + checkinCN.get("end_time") : checkinCN.get("24_hour"))
                .setCheckOut(null == resultCN.getCheckout() ? "" : resultCN.getCheckout().getTime())
                .setInstructions(checkinCN.get("instructions") + checkinCN.get("special_instructions"))
                .setMinAge(checkinCN.containsKey("min_age") ? "入住办理人需年满 " + checkinCN.get("min_age") + " 周岁" : "无")
                .setFees(null == resultCN.getFees() ? "" : convertNull(resultCN.getFees().getMandatory()) + convertNull(resultCN.getFees().getOptional()))
                .setPolicies(null == resultCN.getPolicies() ? "" : resultCN.getPolicies().getKnow_before_you_go())
                .setDescriptions(null == resultCN.getDescriptions() ? "" : convertDescriptions(resultCN.getDescriptions()));
        globalHotelBaseExtends.add(globalHotelBaseExtendCN);
        hotelDetailsRequest.setGlobalHotelBaseExtendDTOS(globalHotelBaseExtends);

        //房型信息
        List<RoomBaseRequest> roomBaseList = new ArrayList<>();
        Map<String, HotelStaticInfo.Room> roomUSMap = resultUS.getRooms();
        Map<String, HotelStaticInfo.Room> roomCNMap = resultCN.getRooms();
        if (null != roomUSMap && !roomUSMap.isEmpty()) {
            roomUSMap.keySet().forEach(roomId -> {
                HotelStaticInfo.Room roomUS = roomUSMap.get(roomId);
                HotelStaticInfo.Room roomCN = roomCNMap.get(roomId);
                RoomBaseRequest bedInfo = convertBedInfo(roomUS.getBed_groups(), roomCN.getBed_groups());
                RoomBaseRequest roomBaseRequest = new RoomBaseRequest()
                        .setHotelId(resultUS.getProperty_id())
                        .setRoomId(roomId)
                        .setRoomName(roomUS.getName())
                        .setRoomNameCN(convertNull(roomCN.getName()))
                        .setArea(null == roomUS.getArea() ? "0" : String.valueOf(roomUS.getArea().getSquare_meters()))
                        .setBroadnet(0)
                        .setBedType(bedInfo.getBedType())
                        .setBedName(bedInfo.getBedName())
                        .setBedNameCN(bedInfo.getBedNameCN())
                        .setBedDesc(bedInfo.getBedDesc())
                        .setCapacity(roomUS.getOccupancy().getMax_allowed().getTotal())
                        .setHasBathroom(0)
                        .setHasWindows(0)
                        .setIsSmoking(0);
                //房型图片
                List<GlobalHotelPictureDTO> globalRoomPictures = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(roomUS.getImages())) {
                    roomUS.getImages().forEach(images -> {
                        HotelStaticInfo.UrlInfo urlInfo = null == images.getLinks().get("1000px") ? images.getLinks().get("350px") : images.getLinks().get("1000px");
                        if (null != urlInfo) {
                            GlobalHotelPictureDTO globalRoomPictureDTO = new GlobalHotelPictureDTO()
                                    .setHotelId(resultUS.getProperty_id())
                                    .setRoomId(roomId)
                                    .setType("room")
                                    .setName(images.getCaption())
                                    .setSort(images.getHero_image() ? 0 : 1)
                                    .setUrl(urlInfo.getHref());
                            globalRoomPictures.add(globalRoomPictureDTO);
                        }
                    });
                }
                roomBaseRequest.setGlobalRoomPictureDTOS(globalRoomPictures);
                roomBaseList.add(roomBaseRequest);
            });
        }
        hotelDetailsRequest.setRoomBaseList(roomBaseList);
        return hotelDetailsRequest;
    }

    private static String convertDescriptions(HotelStaticInfo.HotelDescription description) {
        String descStr = "";
        if (StringUtils.isNotBlank(description.getAmenities())) {
            descStr += description.getAmenities();
        }
        if (StringUtils.isNotBlank(description.getBusiness_amenities())) {
            descStr += description.getBusiness_amenities();
        }
        if (StringUtils.isNotBlank(description.getRooms())) {
            descStr += description.getRooms();
        }
        if (StringUtils.isNotBlank(description.getAttractions())) {
            descStr += description.getAttractions();
        }
        if (StringUtils.isNotBlank(description.getLocation())) {
            descStr += description.getLocation();
        }
        if (StringUtils.isNotBlank(description.getHeadline())) {
            descStr += description.getHeadline();
        }
        return descStr;
    }


    private static RoomBaseRequest convertBedInfo(Map<String, HotelStaticInfo.BedGroup> bed_groups_us, Map<String, HotelStaticInfo.BedGroup> bed_groups_cn) {
        Set<String> bedTypeSet = new HashSet<>();
        AtomicReference<String> bedNameUS = new AtomicReference<>("");
        AtomicReference<String> bedNameCN = new AtomicReference<>("");
        List<List<BedInfoDTO>> bedInfosList = new ArrayList<>();
        if (null != bed_groups_us && !bed_groups_us.isEmpty()) {
            bed_groups_us.keySet().forEach(bedId -> {
                List<BedInfoDTO> bedInfoDTOS = new ArrayList<>();
                HotelStaticInfo.BedGroup bedGroupUS = bed_groups_us.get(bedId);
                HotelStaticInfo.BedGroup bedGroupCN = bed_groups_cn.get(bedId);
                bedNameUS.set(StringUtils.isBlank(bedNameUS.get()) ? bedGroupUS.getDescription() : bedNameUS + " or " + bedGroupUS.getDescription());
                bedNameCN.set(StringUtils.isBlank(bedNameCN.get()) ? bedGroupCN.getDescription() : bedNameCN + "或" + bedGroupCN.getDescription());
                bedGroupUS.getConfiguration().forEach(bedInfo -> {
                    bedTypeSet.add(bedInfo.getType());
                    BedInfoDTO bedInfoDTO = new BedInfoDTO()
                            .setBedNumber(bedInfo.getQuantity())
                            .setBedDesc(bedInfo.getType())
                            .setBedType(bedInfo.getSize());
                    bedInfoDTOS.add(bedInfoDTO);
                });
                bedInfosList.add(bedInfoDTOS);
            });
        }
        RoomBaseRequest bedInfo = new RoomBaseRequest()
                .setBedType(CollectionUtils.isEmpty(bedTypeSet) ? "" : bedTypeSet.toString())
                .setBedName(bedNameUS.get())
                .setBedNameCN(bedNameCN.get())
                .setBedDesc(JsonUtils.writeObject2Json(bedInfosList));
        return bedInfo;
    }

    public static List<GlobalProductSupplierRequest> transformBaseProductReq(QueryPriceResponse queryPriceResponse) {
        List<GlobalProductSupplierRequest> list = Lists.newArrayList();
        if (CollectionUtils.isEmpty(queryPriceResponse.getHotelPrices())) {
            return list;
        }
        queryPriceResponse.getHotelPrices().forEach(hotelPrice -> {
            if (CollectionUtils.isNotEmpty(hotelPrice.getRooms())) {
                hotelPrice.getRooms().forEach(roomListBean -> roomListBean.getRates().forEach(rate -> {
                    GlobalProductSupplierRequest request = new GlobalProductSupplierRequest()
                            .setSupplierId(10005)
                            .setSupplierHotelId(hotelPrice.getProperty_id())
                            .setSupplierRoomId(roomListBean.getId())
                            .setSupplierProductId(rate.getId())
                            .setSupplierProductName(roomListBean.getRoom_name())
                            .setHasWindow(0)
                            .setBreakfast(0)
                            .setCancelType(0);
                    list.add(request);
                }));
            }
        });
        return list;
    }

    public static SupplierHotelBaseRequest transformInfoHotelReq(HotelStaticInfo resultUS, HotelStaticInfo resultCN) {
        SupplierHotelBaseRequest request = new SupplierHotelBaseRequest()
                .setSupplierId(10005)
                .setSupplierHotelId(resultUS.getProperty_id())
                .setSupplierHotelName(resultUS.getName())
                .setSupplierHotelNameCN(resultCN.getName())
                .setAddress(resultUS.getAddress().getLine_1())
                .setAddressCN(resultCN.getAddress().getLine_1())
                .setCountryCode(resultUS.getAddress().getCountry_code())
                .setCityName(resultUS.getAddress().getCity())
                .setCityNameCN(resultCN.getAddress().getCity())
                .setTelephone(resultUS.getPhone())
                .setPostcode(resultUS.getAddress().getPostal_code())
                .setLatitude(String.valueOf(resultUS.getLocation().getCoordinates().getLongitude()))
                .setLongitude(String.valueOf(resultUS.getLocation().getCoordinates().getLatitude()))
                .setRecommendLevel(0);
        //房型信息
        List<SupplierRoomBaseRequest> roomBaseList = new ArrayList<>();
        Map<String, HotelStaticInfo.Room> roomUSMap = resultUS.getRooms();
        Map<String, HotelStaticInfo.Room> roomCNMap = resultCN.getRooms();
        if (null != roomUSMap && !roomUSMap.isEmpty()) {
            roomUSMap.keySet().forEach(roomId -> {
                HotelStaticInfo.Room roomUS = roomUSMap.get(roomId);
                HotelStaticInfo.Room roomCN = roomCNMap.get(roomId);
                SupplierRoomBaseRequest roomBaseRequest = new SupplierRoomBaseRequest()
                        .setSupplierId(10005)
                        .setSupplierHotelId(resultUS.getProperty_id())
                        .setSupplierRoomId(roomId)
                        .setSupplierRoomName(roomUS.getName())
                        .setSupplierRoomNameCN(convertNull(roomCN.getName()))
                        .setArea(null == roomUS.getArea() ? "0" : String.valueOf(roomUS.getArea().getSquare_meters()))
                        .setDescription("")
                        .setBroadNet(0)
                        .setBedInfoList(new ArrayList<>())
                        .setCapacity(roomUS.getOccupancy().getMax_allowed().getTotal())
                        .setHasBathroom(0)
                        .setHasWindows(0)
                        .setIsSmoking(0);
                roomBaseList.add(roomBaseRequest);
            });
        }
        request.setRoomList(roomBaseList);
        return request;
    }

    public static List<SupplierProductBaseRequest> transformInfoProductReq(QueryPriceResponse queryPriceResponse) {
        List<SupplierProductBaseRequest> list = Lists.newArrayList();
        if (CollectionUtils.isEmpty(queryPriceResponse.getHotelPrices())) {
            return list;
        }
        queryPriceResponse.getHotelPrices().forEach(hotelPrice -> {
            if (CollectionUtils.isNotEmpty(hotelPrice.getRooms())) {
                hotelPrice.getRooms().forEach(roomListBean -> roomListBean.getRates().forEach(rate -> {
                    SupplierProductBaseRequest request = new SupplierProductBaseRequest()
                            .setSupplierId(10005)
                            .setSupplierHotelId(hotelPrice.getProperty_id())
                            .setSupplierRoomId(roomListBean.getId())
                            .setSupplierProductId(rate.getId())
                            .setSupplierProductName(roomListBean.getRoom_name())
                            .setHasWindow(0)
                            .setBreakfast(0)
                            .setCancelType(0);
                    list.add(request);
                }));
            }
        });
        return list;
    }

    private static String convertNull(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return str;
    }
}
