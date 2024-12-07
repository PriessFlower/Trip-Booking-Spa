package com.bingo.hotel.spa.intl.core.api.ratehawk.service.impl;


import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.ratehawk.access.HotelFileAccess;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request.HotelInfoRequest;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.HotelFileResponse;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkService;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.FileDealUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Base64;


@Service
@Slf4j
public class RateHawkServiceImpl implements RateHawkService {

    @Value("${ratehawk.key_id}")
    private String keyId;

    @Value("${ratehawk.api_key}")
    private String apiKey;

    @Value("${ratehawk.url}")
    private String url;

    @Value("${system.localFilePath}")
    private String LOCAL_FILE_PATH;

    @Resource
    private DistributedRateLimiter redisRateLimiter;

    @Override
    public void queryAndSaveStaticInfo(String staticType, String startTime, String endTime, int startNum, int endNum, boolean downloadFlag) {

        //1.获取酒店文件
        ResponseResult<HotelFileResponse> hotelFileResult = new HotelFileAccess(url, generateBasicAuth(), redisRateLimiter).access(HotelInfoRequest.builder()
                .inventory("all")
                .language("en")
                .build());

        if (null == hotelFileResult || null == hotelFileResult.getData() || StringUtils.isBlank(hotelFileResult.getData().getUrl())) {
            log.info("酒店文件查询异常:{}", JsonUtils.writeObject2Json(hotelFileResult));
        }
        //2.分片存储文件
        String fileUrl = hotelFileResult.getData().getUrl();
        String localFilePath = LOCAL_FILE_PATH + "rateHawk/" + fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        if (downloadFlag) {
            FileDealUtils.downloadFile(fileUrl, localFilePath);
            FileDealUtils.zstdFiles(localFilePath, localFilePath.replace(".zst", ""));
        }
        //3.读取文件并推送info落库






//        //3.解析文件数据并推送保存静态数据
//        if (CollectionUtils.isNotEmpty(supplierHotelIds)) {
//            pushHotelByHotelId(supplierHotelIds);
//        } else {
//            parseFile(localFilePath.replace(".gz", ""), allPushFlag, updateDays, startLine);
//        }


    }

    public String generateBasicAuth() {
        String credentials = keyId + ":" + apiKey;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encodedCredentials;
    }

    public static void main(String[] args) {
        String fileUrl = "https://partner-feedora.s3.eu-central-1.amazonaws.com/feed/partner_feed_en_v3.jsonl.zst";
        String localFilePath = "D:\\working\\file\\导出文件\\hotel_info" + fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        FileDealUtils.downloadFile(fileUrl, localFilePath);
        FileDealUtils.zstdFiles(localFilePath, localFilePath.replace(".zst", ""));
    }
}
