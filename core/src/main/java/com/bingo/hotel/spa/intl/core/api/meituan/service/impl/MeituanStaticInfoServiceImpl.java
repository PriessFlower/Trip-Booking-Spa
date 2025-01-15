package com.bingo.hotel.spa.intl.core.api.meituan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.bean.SupplierHotelIdList;
import com.bingo.hotel.spa.intl.core.api.common.constant.Constants;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.mapper.SupplierHotelIdListMapper;
import com.bingo.hotel.spa.intl.core.api.meituan.access.HotelInfoAccess;
import com.bingo.hotel.spa.intl.core.api.meituan.access.HotelListAccess;
import com.bingo.hotel.spa.intl.core.api.meituan.access.ProductInfoAccess;
import com.bingo.hotel.spa.intl.core.api.meituan.access.RoomInfoAccess;
import com.bingo.hotel.spa.intl.core.api.meituan.adaptor.MeiTuanStaticInfoAdaptor;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.request.HotelIdsReqBody;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.request.HotelInfoReqBody;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.request.ProductInfoReqBody;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.HotelIdsResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.HotelInfoResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.ProductInfoResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.RoomInfoResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.service.ISupplierHotelIdListService;
import com.bingo.hotel.spa.intl.core.api.meituan.service.MeituanStaticInfoService;
import com.bingo.hotel.spa.intl.core.exception.RedisLimitException;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class MeituanStaticInfoServiceImpl implements MeituanStaticInfoService {

    @Value("${meituan.url}")
    String host;

    @Value("${meituan.partner-id}")
    Integer partnerId;

    @Value("${meituan.key.public}")
    String publicKey;

    @Value("${meituan.key.secret}")
    String secretKey;

    @Value("${meituan.test}")
    String test;

    @Value("${meituan.language}")
    String language;

    @Value("${meituan.method.list.path}")
    String listPath;
    @Value("${meituan.method.list.version}")
    String listVersion;

    @Value("${meituan.method.detail.path}")
    String detailPath;
    @Value("${meituan.method.detail.version}")
    String detailVersion;

    @Value("${meituan.method.room.path}")
    String roomPath;
    @Value("${meituan.method.room.version}")
    String roomVersion;

    @Value("${meituan.method.goods.path}")
    String goodsPath;
    @Value("${meituan.method.goods.version}")
    String goodsVersion;

    @Resource
    private HotelInfoIntlClient hotelInfoIntlClient;
    @Resource
    private DistributedRateLimiter rateLimiter;
    @Resource
    private ISupplierHotelIdListService iSupplierHotelIdListService;
    @Resource
    private SupplierHotelIdListMapper supplierHotelIdListMapper;


    @Override
    public void queryHotelIdList(Long maxId, Integer pageSize) {

        while (true) {
            maxId = saveHotelIds(maxId, pageSize, Constants.SYSTEM);
            log.info("MeituanHotelIdsSyncTask maxID:{}", maxId);
            if (maxId == null) {
                break;
            }
        }
        log.info("MeituanHotelIdsSyncTask End");
    }

    private Long saveHotelIds(long maxId, int pageSize, String system) {
        try {
            if (!HotelIdsReqBody.checkBody(maxId, pageSize)) {
                log.error("getMeituanHotelIds param is error maxId:{}, pageSize:{} operator:{}", maxId, pageSize);
                return null;
            }
            HotelIdsReqBody hotelIdsReqBody = HotelIdsReqBody.builder().maxId(maxId).pageSize(pageSize).build();

            log.info("queryHotelIdList hotelIdsReqBody:{}", JsonUtils.writeObject2Json(hotelIdsReqBody));

            ResponseResult<HotelIdsResponse> response = new HotelListAccess(host, partnerId, publicKey, secretKey,
                    test, listPath, listVersion, rateLimiter).access(hotelIdsReqBody);

            if (response == null) {
                log.warn("queryHotelIdList response is null hotelIdsReqBody : {}",
                        JsonUtils.writeObject2Json(hotelIdsReqBody));
                return null;
            }

            HotelIdsResponse hotelIdsResponse = response.getData();

            if (hotelIdsResponse == null || hotelIdsResponse.isEmptyResult()) {
                log.error("queryHotelIdList response is error request:{}, result:{} ",
                        JsonUtils.writeObject2Json(hotelIdsReqBody), response.getOrigData());
                return null;
            }

            log.debug("queryHotelIdList response is success request:{}, result:{} ",
                    JsonUtils.writeObject2Json(hotelIdsReqBody), response.getOrigData());

            List<SupplierHotelIdList> list = Lists.newArrayList();
            for (Integer hotelId : hotelIdsResponse.getResult().getHotelIds()) {

                SupplierHotelIdList hotelIdList = new SupplierHotelIdList();

                hotelIdList.setOnline(1);
                hotelIdList.setSHotelId(hotelId.toString());
                hotelIdList.setSupplierId(String.valueOf(SupplierSourceEnum.MEITUAN.getCode()));
                hotelIdList.setOperator(system);

                list.add(hotelIdList);
            }
            iSupplierHotelIdListService.saveBatch(list);
            return hotelIdsResponse.getResult().getMaxId();
        } catch (RedisLimitException e) {
            log.error("queryHotelIdList limit is error e:{}", e);
        } catch (Exception e) {
            log.error("queryHotelIdList error e :", e);
        }
        return null;
    }

    @Override
    public void saveOrUpdateHotelInfo(Integer pageNumber, Integer pageSize, String type) {

        pageNumber = null == pageNumber ? 1 : pageNumber;
        pageSize = null == pageSize ? 1000 : pageSize;

        while (true) {
            log.info("开始第{}页酒店信息查询", pageNumber);
            Page<SupplierHotelIdList> page = new Page<>(pageNumber, pageSize);
            LambdaQueryWrapper<SupplierHotelIdList> idListQueryWrapper = new LambdaQueryWrapper<SupplierHotelIdList>()
                    .eq(SupplierHotelIdList::getSupplierId, SupplierSourceEnum.MEITUAN.getCode())
                    .orderByAsc(SupplierHotelIdList::getId);
            Page<SupplierHotelIdList> supplierHotelIdList = supplierHotelIdListMapper.selectPage(page, idListQueryWrapper);
            if (null == supplierHotelIdList || CollectionUtils.isEmpty(supplierHotelIdList.getRecords())) {
                log.error("查询供应商酒店ids为空");
                return;
            }
            List<SupplierHotelIdList> supplierHotelIds = supplierHotelIdList.getRecords();
            int batchSize = 20;
            int currentBatch = 0;
            //批量方式
            for (int i = 0; i < supplierHotelIds.size(); i += batchSize) {
                // 截取当前批次的数据
                List<SupplierHotelIdList> saveSupplierHotelIds = supplierHotelIds.subList(currentBatch * batchSize, Math.min(supplierHotelIds.size(),
                        (currentBatch + 1) * batchSize));
                //分批保存酒店房型信息
                List<Long> sHotelIds = saveSupplierHotelIds.stream().map(a -> Long.valueOf(a.getSHotelId())).collect(Collectors.toList());
                try {
                    if ("HOTEL".equals(type)) {
                        log.info("发起酒店请求：{}", JsonUtils.writeObject2Json(sHotelIds));
                        saveHotelInfo(sHotelIds);
                    } else if ("ROOM".equals(type)) {
                        log.info("发起房型请求：{}", JsonUtils.writeObject2Json(sHotelIds));
                        saveRoomInfo(sHotelIds);
                    } else if ("PRODUCT".equals(type)) {
                        log.info("发起产品请求：{}", JsonUtils.writeObject2Json(sHotelIds));
                        saveProductInfo(sHotelIds);
                    }
                } catch (Exception e) {
                    log.error("保存酒店房型信息异常 request:{}, 异常信息：", JsonUtils.writeObject2Json(sHotelIds), e);
                }
                // 每处理完一组，增加当前批次计数器
                currentBatch++;
            }
            pageNumber++;
        }
    }

    public void saveHotelInfo(List<Long> hotelIds) {

        try {
            HotelInfoReqBody hotelReqBody = HotelInfoReqBody.builder()
                    .hotelIds(hotelIds)
                    .strategy(1)
                    .build();

            log.info("getMeituanHotelDetail hotelReqBody:{}", JsonUtils.writeObject2Json(hotelReqBody));

            ResponseResult<HotelInfoResponse> response = new HotelInfoAccess(host, partnerId, publicKey, secretKey,
                    test, detailPath, detailVersion, rateLimiter).access(hotelReqBody);

            if (response == null) {
                log.warn("getMeituanHotelDetail response is null hotelReqBody : {}",
                        JsonUtils.writeObject2Json(hotelReqBody));
                return;
            }

            HotelInfoResponse hotelInfoResponse = response.getData();

            if (hotelInfoResponse == null || hotelInfoResponse.isEmptyResult()) {
                log.error("getMeituanHotelDetail response is error request:{}, result:{} ",
                        JsonUtils.writeObject2Json(hotelReqBody), response.getOrigData());
                return;
            }

            log.debug("getMeituanHotelDetail response is success request:{}, result:{} ",
                    JsonUtils.writeObject2Json(hotelReqBody), response.getOrigData());

            List<SupplierHotelBaseRequest> list = Lists.newArrayList();
            for (HotelInfoResponse.HotelInfoResult hotelInfoResult : hotelInfoResponse.getResult()) {
                SupplierHotelBaseRequest supplierHotelBaseRequest = MeiTuanStaticInfoAdaptor.transformInfoHotelReq(hotelInfoResult);
                list.add(supplierHotelBaseRequest);
            }

            hotelInfoIntlClient.saveHotelInfo(list);

        } catch (RedisLimitException e) {
            log.error("getMeituanHotelDetail limit is error e:", e);
        } catch (Exception e) {
            log.error("getMeituanHotelDetail error e :", e);
        }
    }

    public void saveRoomInfo(List<Long> hotelIds) {

        try {
            HotelInfoReqBody roomReqBody = HotelInfoReqBody.builder()
                    .hotelIds(hotelIds)
                    .build();

            log.info("getMeituanRoomInfo roomReqBody:{}", JsonUtils.writeObject2Json(roomReqBody));

            ResponseResult<RoomInfoResponse> response = new RoomInfoAccess(host, partnerId, publicKey, secretKey,
                    test, roomPath, roomVersion, rateLimiter).access(roomReqBody);

            if (response == null || null == response.getData()) {
                log.warn("getMeituanRoomInfo response is null roomReqBody : {}",
                        JsonUtils.writeObject2Json(roomReqBody));
                return;
            }

            RoomInfoResponse roomInfoResponse = response.getData();

            if (roomInfoResponse == null || roomInfoResponse.isEmptyResult()) {
                log.error("getMeituanRoomInfo response is error request:{}, result:{} ",
                        JsonUtils.writeObject2Json(roomReqBody), response.getOrigData());
                return;
            }

            log.debug("getMeituanRoomInfo response is success request:{}, result:{} ",
                    JsonUtils.writeObject2Json(roomReqBody), roomInfoResponse);

            Map<Long, List<RoomInfoResponse.RealRoomInfos>> realRoomInfoMap = roomInfoResponse.getResult().getRealRoomInfos();
            for (Long hotelId : hotelIds) {
                if (realRoomInfoMap.containsKey(hotelId)) {
                    List<SupplierRoomBaseRequest> supplierRoomBaseRequests = MeiTuanStaticInfoAdaptor.transformInfoRoomReq(realRoomInfoMap.get(hotelId), hotelId);
                    if (CollectionUtils.isNotEmpty(supplierRoomBaseRequests)) {
                        hotelInfoIntlClient.saveRoomInfo(supplierRoomBaseRequests);
                    }
                }
            }
        } catch (RedisLimitException e) {
            log.error("getMeituanRoomInfo limit is error e:", e);
        } catch (Exception e) {
            log.error("getMeituanRoomInfo error e :", e);
        }
    }

    public void saveProductInfo(List<Long> hotelIds) {

        try {
            ProductInfoReqBody productInfoReqBody = ProductInfoReqBody.builder()
                    .hotelIds(hotelIds)
                    .checkinDate(DateUtil.getFutureDay(null, 9))
                    .checkoutDate(DateUtil.getFutureDay(null, 10))
                    .numberOfAdults(1)
                    .currencyCode("CNY")
                    .clientNationality("CN")
                    .build();

            log.info("getMeituanProductInfo roomReqBody:{}", JsonUtils.writeObject2Json(productInfoReqBody));

            ResponseResult<ProductInfoResponse> response = new ProductInfoAccess(host, partnerId, publicKey, secretKey,
                    test, goodsPath, goodsVersion, rateLimiter).access(productInfoReqBody);

            if (response == null || null == response.getData()) {
                log.warn("getMeituanProductInfo response is null request : {}",
                        JsonUtils.writeObject2Json(productInfoReqBody));
                return;
            }

            ProductInfoResponse productInfoResponse = response.getData();

            if (productInfoResponse == null || productInfoResponse.isEmptyResult()) {
                log.error("getMeituanProductInfo response is error request:{}, result:{} ",
                        JsonUtils.writeObject2Json(productInfoReqBody), response.getOrigData());
                return;
            }

            log.debug("getMeituanProductInfo response is success request:{}, result:{} ",
                    JsonUtils.writeObject2Json(productInfoReqBody), response.getOrigData());

            List<ProductInfoResponse.Result> productInfoResult = productInfoResponse.getResult();

            List<SupplierProductBaseRequest> supplierProductBaseRequests = MeiTuanStaticInfoAdaptor.transformInfoProductReq(productInfoResult);
            if (CollectionUtils.isNotEmpty(supplierProductBaseRequests)) {
                int batchSize = 20;
                int currentBatch = 0;
                //批量方式
                for (int i = 0; i < supplierProductBaseRequests.size(); i += batchSize) {
                    // 截取当前批次的数据
                    List<SupplierProductBaseRequest> saveSupplierProductInfo = supplierProductBaseRequests.subList(currentBatch * batchSize,
                            Math.min(supplierProductBaseRequests.size(), (currentBatch + 1) * batchSize));
                    hotelInfoIntlClient.saveProductInfo(saveSupplierProductInfo);
                    currentBatch++;
                }
            }

        } catch (RedisLimitException e) {
            log.error("getMeituanProductInfo limit is error e:", e);
        } catch (Exception e) {
            log.error("getMeituanProductInfo error e :", e);
        }
    }

}
