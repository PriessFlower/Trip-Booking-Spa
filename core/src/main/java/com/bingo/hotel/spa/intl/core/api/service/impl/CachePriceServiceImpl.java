package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfoCache;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.dto.ProductRespCacheDTO;
import com.bingo.hotel.spa.intl.core.api.service.CachePriceService;
import com.bingo.hotel.spa.intl.core.redis.RedisUtils;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.bingo.hotel.spa.intl.core.util.ModelConverterUtils;
import com.bingo.hotel.spa.intl.core.util.RedisKeyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @description:查询价格缓存
 * @author: dick_w
 * @date: 2025/3/10 18:28
 * @param:
 * @return:
 **/
@Service
@Slf4j
public class CachePriceServiceImpl implements CachePriceService {

    @Autowired
    private RedisUtils redisUtils;

    private static final long ONE_DAY = 86400L;

    @Override
    public List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier) {
        List<ProductRespDTO> respDTOList = new ArrayList<>();
        List<String> checkList = DateUtil.getDatesBetween(priceReq.getCheckIn(), priceReq.getCheckout());
        Map<String, List<PriceInfoCache>> productMap = new HashMap<>();

        List<String> keyList = new ArrayList<>();
        checkList.forEach(c -> {
            //price:sHotelId:yyyy-MM-dd
            String priceKey = RedisKeyUtils.buildPriceKey(supplier.getSHotelId(), c);
            keyList.add(priceKey);
        });
        //productMap--sProductId,List<PriceInfo>
        fetchAndProcessPriceInfo(productMap, supplier, keyList);

        // 使用已经收集的价格信息构建响应对象
        productMap.forEach((key, value) -> {
            ProductRespDTO respDTO = new ProductRespDTO();
//            System.out.println("sProductId----"+key);
//            System.out.println("List<PriceInfo>----"+ JSON.toJSON(value));
            //计算报价总价 产品信息中totalprice可能是不正确的
            int totalPrice = value.stream().mapToInt(PriceInfoCache::getPrice).sum();
            //计算taxex总价
            Integer totalTaxes = value.stream().filter(a->!Objects.isNull(a.getTaxes())).mapToInt(PriceInfoCache::getTaxes).sum();
            //计算roomPrice总价
            Integer roomTotalPrice = value.stream().filter(a->!Objects.isNull(a.getRoomPrice())).mapToInt(PriceInfoCache::getRoomPrice).sum();
            //计算酒店一次性收取费用总价
            Integer stayPriceTotal = value.stream().filter(a->!Objects.isNull(a.getStayPrice())).mapToInt(PriceInfoCache::getStayPrice).sum();
            //计算线下支付金额总价
            Integer storePayPricePrice = value.stream().filter(a->!Objects.isNull(a.getStorePayPrice())).mapToInt(PriceInfoCache::getStorePayPrice).sum();
            //计算佣金总价
            Integer brokerage = value.stream().filter(a->!Objects.isNull(a.getBrokerage())).mapToInt(PriceInfoCache::getBrokerage).sum();
            //如果缓存的价格为0，则不返回这个产品价格信息
            if(totalPrice == 0){
                return;
            }
            //如果List<PriceInfo>的size不等于List<String> keyList的大小，就证明某一天没价格数据，则不返回该产品信息
            if(keyList.size() != value.size()){
                return;
            }
            //如果size相同，但某一天价格为0，则不返回该产品信息
            if(keyList.size() == value.size()){
                if(value.stream().anyMatch(priceInfo -> 0 == priceInfo.getPrice().intValue())){
                    return;
                }
            }
            //补充产品其他信息
            //key是product:sHotelId:sProductId,value是ProductRespCacheDTO
            String priceInfoKey = RedisKeyUtils.buildPriceInfoKey(supplier.getSHotelId(), key);
            String priceInfoJson = redisUtils.get(priceInfoKey);
            if (StringUtils.isNotBlank(priceInfoJson)) {
                ProductRespCacheDTO productRespCacheDTO = JsonUtils.decodeJson(priceInfoJson, new TypeReference<>() {
                });
                BeanUtils.copyProperties(productRespCacheDTO, respDTO);
                respDTO.setTotalPrice(totalPrice);
                respDTO.setTotalTaxes(totalTaxes);
                respDTO.setRoomTotalPrice(roomTotalPrice);
            }
            respDTO.setSupplierId(supplier.getSupplierId());
            respDTO.setProductId(key);
            respDTO.setHotelId(supplier.getSHotelId());
            List<PriceInfo> priceInfos = ModelConverterUtils.convert(value,PriceInfo.class);
            respDTO.setPriceInfos(priceInfos);
            respDTO.setStayPrice(stayPriceTotal);
            respDTO.setStorePayPrice(storePayPricePrice);
            respDTO.setBrokerage(brokerage);
            respDTOList.add(respDTO);
        });

        return respDTOList;
    }

    @Override
    public void productToCache(List<ProductRespDTO> list) {
        try {
            if (list == null || list.isEmpty()) {
//                log.info("productToCache list is empty");
                return;
            }

            Set<String> changePricesSet = new HashSet<>(); // 存储价格发生变化的酒店id
            Set<String> fullPricesSet = new HashSet<>(); // 满房变化的酒店id
            Set<String> upSet = new HashSet<>(); // 存储上价酒店的酒店id
            Set<String> downSet = new HashSet<>(); // 存储下架酒店的酒店id
            Set<String> downHotelKeySet = new HashSet<>(); // TODO 疑问 这个set集合的作用看代码只是用来删除redis里面的数据的，具体为什么这么做、redis里的某些数据为什么要删除，有点疑问

            // nowDateMap(key:priceKey,value(map):key:productId,value:price)，dataMap、nowDataMap、beforeDataMap这三个map里面的格式一样
            Map<String, Map<String, String>> dataMap = new HashMap<>(); // 存储今天之后的酒店价格
            Map<String, Map<String, String>> nowDataMap = new HashMap<>(); // 存储当天(今天)的酒店价格
            Map<String, Map<String, String>> beforeDataMap = new HashMap<>(); // 存储今天之前的酒店价格
            Map<String, ProductRespCacheDTO> productRespCacheDTOMap = new HashMap<>(); // key:product:hotelId:productId value:要缓存的数据
            list.stream()
                    .filter(product -> product.getPriceInfos() != null && !product.getPriceInfos().isEmpty())
                    .forEach(l -> {
                        String priceInfoKey = RedisKeyUtils.buildPriceInfoKey(l.getHotelId(), l.getProductId()); // product:hotelId:productId
                        if (!redisUtils.exists(priceInfoKey)) {
                            ProductRespCacheDTO dto = new ProductRespCacheDTO();
                            BeanUtils.copyProperties(l, dto);
                            productRespCacheDTOMap.put(priceInfoKey, dto);
                        } else {
                            String productInfoJson = redisUtils.get(priceInfoKey);
                            // 这里处理早餐变更的数据
                            if (StringUtils.isNotBlank(productInfoJson)) {
                                ProductRespCacheDTO respCacheDTO = JsonUtils.decodeJson(productInfoJson, new TypeReference<>() {
                                });
                                // 如果早餐有变化，则把发生早餐变化的产品id放入changeBreakfastSet，更新缓存productRespCacheDTOMap
                                if (respCacheDTO.getMeal() != null && !respCacheDTO.getMeal().count.equals(l.getMeal().getCount())) {
                                    //产品变更早餐
//                                changeBreakfastSet.add(l.getProductId());
                                    ProductRespCacheDTO dto = new ProductRespCacheDTO();
                                    BeanUtils.copyProperties(l, dto);
                                    productRespCacheDTOMap.put(priceInfoKey, dto);
                                }
                            }
                        }
                        List<PriceInfo> infos = l.getPriceInfos();
                        // 如果hotelId不再downHotelKeySet中，则放入downHotelKeySet，表示每次价格都要重新更新
                        if (!downHotelKeySet.contains(l.getHotelId())) {
                            deleteDownHotelKey(infos, l.getHotelId()); // 删除redis
                            downHotelKeySet.add(l.getHotelId());
                        }

                        // 这个for用来处理变价的逻辑
                        infos.forEach(i -> {
                            String priceKey = RedisKeyUtils.buildPriceKey(l.getHotelId(), i.getDate()); // rediskey:price:hotelid:date
                            upSet.add(priceKey + ":" + l.getProductId()); // price:hotelid:date:productId
                            String price = redisUtils.hmGet(priceKey, l.getProductId()); //获取大key为price:hotelid:date，小key为productId的数据，从下面代码看出，value存储的为price
                            //priceJson：{"brokerage":2317,"roomPrice":12728,"price":14658,"storePayPrice":null,"taxes":1930,"stayPrice":0}
                            if (StringUtils.isNotBlank(price)) {
                                Map<String, Integer> priceMap = JsonUtils.decodeJson(price, new TypeReference<>() {
                                });
                                if (!(priceMap.get("price").equals(i.getPrice()))
                                        || !(Optional.ofNullable(priceMap.get("taxes")).equals(Optional.ofNullable(i.getTaxes())))
                                        || !(Optional.ofNullable(priceMap.get("roomPrice")).equals(Optional.ofNullable(i.getRoomPrice())))
                                        || !(Optional.ofNullable(priceMap.get("stayPrice")).equals(Optional.ofNullable(l.getStayPrice())))
                                        || !(Optional.ofNullable(priceMap.get("storePayPrice")).equals(Optional.ofNullable(l.getStorePayPrice())))
                                        || !(Optional.ofNullable(priceMap.get("brokerage")).equals(Optional.ofNullable(l.getBrokerage())))) {
                                    // 如果缓存中的价格和新查出来的价格不一致，说明价格发生了变化，会把发生价格变化的酒店id放入changePricesSet集合中
                                    changePricesSet.add(l.getHotelId());
                                    if (i.getPrice() == 0) {
                                        fullPricesSet.add(l.getHotelId());
                                    }
                                    ProductRespCacheDTO dto = new ProductRespCacheDTO();
                                    BeanUtils.copyProperties(l, dto);
                                    productRespCacheDTOMap.put(priceInfoKey, dto); // 把整体缓存更新一下，放入productRespCacheDTOMap中
                                    log.info("产品:{}变价,原价格:{},现价格:{}," +
                                                    "原税额:{},现税额:{}," +
                                                    "原房间价格:{},现房间价格:{}," +
                                                    "原一次性支付费用:{},现一次性支付费用:{}," +
                                                    "原线下支付费用:{},现线下支付费用:{}," +
                                                    "原佣金:{},现佣金:{}," +
                                                    "info:{}",
                                            l.getProductId(),
                                            priceMap.get("price"),
                                            i.getPrice(),
                                            priceMap.get("taxes"),
                                            i.getTaxes(),
                                            priceMap.get("roomPrice"),
                                            i.getRoomPrice(),
                                            priceMap.get("stayPrice"),
                                            l.getStayPrice(),
                                            priceMap.get("storePayPrice"),
                                            l.getStorePayPrice(),
                                            priceMap.get("brokerage"),
                                            l.getBrokerage(),
                                            JsonUtils.writeObject2Json(dto));
                                }
                            } else {
                                // 如果缓存中价格为null，说明之前没有缓存过价格，直接把酒店id放入changePricesSet集合中
                                changePricesSet.add(l.getHotelId());
                            }
                            // 这批if else处理酒店价格，分别处理今天、今天之前、今天之后
                            if (DateUtil.getTodayYMD().trim().equals(i.getDate())) { // 如果价格是今天的，则把数据放入nowDateMap(key:priceKey,value(map):key:productId,value:price)
                                nowDataMap.computeIfAbsent(priceKey, k -> new HashMap<>())
                                        .put(l.getProductId(), convertPriceJsonStr(l,i));
                            } else if (DateUtil.getYesterdayYMD().trim().equals(i.getDate())) {
                                beforeDataMap.computeIfAbsent(priceKey, k -> new HashMap<>())
                                        .put(l.getProductId(), convertPriceJsonStr(l,i));
                            } else {
                                dataMap.computeIfAbsent(priceKey, k -> new HashMap<>()) // 如果价格日期不是今天的，也不是今天之前的，则放入dataMap中
                                        .put(l.getProductId(), convertPriceJsonStr(l,i));
                            }
                        });
                    });

            // 这里把上面代码处理完成的数据统一缓存到redis，这里采用批量缓存到redis，而不是一条一条缓存，减少和redis的IO次数
            // 存储到Redis
            if (dataMap.size() > 0) {
                // 把所有价格数据存储到redis，设置过期时间为1天，redis数据结构为hash
                redisUtils.batchHashMapSetWithExpire(dataMap, 1, TimeUnit.DAYS);
            }
            // 储存时效到第二天凌晨一点
            long untilTomorrowOneAM = DateUtil.getSecondsUntilTomorrowOneAM();
            if (nowDataMap.size() > 0) {
                // 存储nowDateMap到redis，有效时间到第二天凌晨一点，redis数据结构为hash
                redisUtils.batchHashMapSetWithExpire(nowDataMap, untilTomorrowOneAM, TimeUnit.SECONDS);
            }
            // 储存时效6个小时
            if (beforeDataMap.size() > 0) {
                // 存储nowDateMap到redis，有效时间为6个小时，redis数据结构为hash
                redisUtils.batchHashMapSetWithExpire(beforeDataMap, 6, TimeUnit.HOURS);
            }
            //储存除价格其他信
            // 遍历productRespCacheDTOMap，productRespCacheDTOMap里的数据存储到redis，redis数据类型为String，productRespCacheDTOMap存储的是整条产品的基本信息，有效时间为3天
            if (productRespCacheDTOMap.size() > 0) {
                productRespCacheDTOMap.forEach((key, value) -> {
                    redisUtils.setex(key, JsonUtils.writeObject2Json(value), ONE_DAY * 3);
                });
            }

            // 这个try里面处理下架
            try {
                for (ProductRespDTO respDTO : list){
                    //下架酒店处理逻辑
                    Set<String> dateSet = respDTO.getPriceInfos().stream().map(PriceInfo::getDate).collect(Collectors.toSet());

                    dateSet.forEach(d -> {
                        String priceKey = RedisKeyUtils.buildPriceKey(respDTO.getHotelId(), d); // price:hotelId:date
                        Map<String, String> mapGet = redisUtils.hashMapGet(priceKey);
                        mapGet.forEach((key, value) -> {
                            // 当缓存有多余的产品的时候，判断产品价格为0时再推下线，否则代表已经推送过了
                            Map<String, Object> priceMap = JsonUtils.decodeJson(value, new TypeReference<>() {
                            });
                            if (!"0".equals(priceMap.get("price").toString())) {
                                downSet.add(priceKey + ":" + key);
                            }
                        });
                    });
                    Set<String> differenceSet = downSet.stream()
                            .filter(element -> !upSet.contains(element))
                            .collect(Collectors.toSet());
                    Map<String, Map<String, String>> downDataMap = differenceSet.stream()
                            .map(difference -> difference.split(":"))
                            .collect(Collectors.toMap(
                                    parts -> RedisKeyUtils.buildPriceKey(parts[1], parts[2]),
                                    parts -> {
                                        changePricesSet.add(parts[1]);
                                        fullPricesSet.add(parts[1]);
                                        Map<String, String> priceMap = new HashMap<>();
                                        priceMap.put(parts[3], convertPriceJsonStr(list.get(0),null));
                                        return priceMap;
                                    },
                                    (existing, replacement) -> { // 如果有重复的key，合并它们的map
                                        existing.putAll(replacement);
                                        return existing;
                                    }
                            ));
                    // 存储到Redis，将多余的产品价格置0
                    if (!downDataMap.isEmpty()) {
//                        System.out.println("downDataMap----"+JSON.toJSONString(downDataMap));
                        redisUtils.batchHashMapSetWithExpire(downDataMap, 1, TimeUnit.DAYS);
                    }
                }
            } catch (Exception e) {
                log.error("查询价格下架产品逻辑异常:{}", e);
            }

        }catch (Exception e){
            log.error("productToCache error:",e);
        }
    }

    private void deleteDownHotelKey(List<PriceInfo> infos, String hotelId) {
        List<String> between = DateUtil.getDatesBetween(infos.get(0).getDate(), infos.get(infos.size() - 1).getDate());
        between.forEach(b -> {
            String downHotelKey = RedisKeyUtils.buildDownHotelKey(hotelId, b);// down:hotelID:date
            redisUtils.remove(downHotelKey);
        });
    }

    /**
     * @description:组装价格信息
     * @author: dick_w
     * @date: 2025/3/12 10:25
     * @param: [productMap, supplier, keySet]
     * @return: void
     **/
    private void fetchAndProcessPriceInfo(Map<String, List<PriceInfoCache>> productMap, Supplier supplier, List<String> keySet) {
        if (StringUtils.isNotBlank(supplier.getSProductId())) {
            //根据priceKey（price:sHotelId:yyyy-MM-dd）查询map（sProductId,price)
            keySet.forEach(priceKey -> {
                String price = redisUtils.hmGet(priceKey, supplier.getSProductId());
                if (StringUtils.isNotBlank(price)) {
                    String datePart = getDatePartFromPriceKey(priceKey);
                    // priceJson

//                                jsonMap.put("price", null != priceInfo.getPrice()?priceInfo.getPrice():0);
//                                jsonMap.put("taxes", null != priceInfo.getTaxes()?priceInfo.getTaxes():0);
//                                jsonMap.put("roomPrice", null != priceInfo.getRoomPrice()?priceInfo.getRoomPrice():0);
//                                jsonMap.put("stayPrice", productRespDTO.getStayPrice());
//                                jsonMap.put("storePayPrice",productRespDTO.getStorePayPrice());
//                                jsonMap.put("brokerage",productRespDTO.getBrokerage());
                    Map<String, Integer> priceMap = JsonUtils.decodeJson(price, new TypeReference<>() {
                    });
//                    String totalPrice = priceMap.get("price").toString();
//                    String taxes = Objects.isNull(priceMap.get("taxes"))?null:priceMap.get("taxes").toString();
//                    String roomPrice = Objects.isNull(priceMap.get("roomPrice"))?null:priceMap.get("roomPrice").toString();
//                    String stayPrice = Objects.isNull(priceMap.get("stayPrice"))?null:priceMap.get("stayPrice").toString();
//                    String storePayPrice = Objects.isNull(priceMap.get("storePayPrice"))?null:priceMap.get("storePayPrice").toString();
//                    String brokerage = Objects.isNull(priceMap.get("brokerage"))?null:priceMap.get("brokerage").toString();
                    Integer totalPrice = priceMap.get("price");
                    Integer taxes = Objects.isNull(priceMap.get("taxes"))?null:priceMap.get("taxes");
                    Integer roomPrice = Objects.isNull(priceMap.get("roomPrice"))?null:priceMap.get("roomPrice");
                    Integer stayPrice = Objects.isNull(priceMap.get("stayPrice"))?null:priceMap.get("stayPrice");
                    Integer storePayPrice = Objects.isNull(priceMap.get("storePayPrice"))?null:priceMap.get("storePayPrice");
                    Integer brokerage = Objects.isNull(priceMap.get("brokerage"))?null:priceMap.get("brokerage");
                    productMap.computeIfAbsent(supplier.getSProductId(), k -> new ArrayList<>())
                            .add(new PriceInfoCache(datePart,
                                    totalPrice,
                                    taxes,
                                    roomPrice,
                                    stayPrice,
                                    storePayPrice,
                                    brokerage));
                }
            });
        } else {
            //根据priceKey（price:sHotelId:yyyy-MM-dd）查询map（sProductId,price）
            Map<String, Map<String, String>> mapMap = redisUtils.hashMapListAndKey(keySet);
            mapMap.forEach((mKey, mValue) -> {
                //mKey--priceKey,mValue--map
                String datePart = getDatePartFromPriceKey(mKey);
                //key--sProductId,value--priceJson
//                mValue.forEach((key, value) -> productMap.computeIfAbsent(key, k -> new ArrayList<>())
//                        .add(new PriceInfoCache(datePart,
//                                Integer.parseInt(value.split("_")[0]),
//                                value.split("_")[1].equals("0")?null:Integer.parseInt(value.split("_")[1]),
//                                value.split("_")[2].equals("0")?null:Integer.parseInt(value.split("_")[2]))));
                for (Map.Entry<String, String> entry : mValue.entrySet()) {
                    String key = entry.getKey(); // 获取键
                    String value = entry.getValue(); // 获取值

                    // 如果 productMap 中不存在该键，则插入一个新的 ArrayList
                    if (!productMap.containsKey(key)) {
                        productMap.put(key, new ArrayList<>());
                    }
                    Map<String, Integer> priceMap = JsonUtils.decodeJson(value, new TypeReference<>() {
                    });
                    // 解析 value 并创建 PriceInfoCache 对象
                    Integer totalPrice = priceMap.get("price");
                    Integer taxes = Objects.isNull(priceMap.get("taxes"))?null:priceMap.get("taxes");
                    Integer roomPrice = Objects.isNull(priceMap.get("roomPrice"))?null:priceMap.get("roomPrice");
                    Integer stayPrice = Objects.isNull(priceMap.get("stayPrice"))?null:priceMap.get("stayPrice");
                    Integer storePayPrice = Objects.isNull(priceMap.get("storePayPrice"))?null:priceMap.get("storePayPrice");
                    Integer brokerage = Objects.isNull(priceMap.get("brokerage"))?null:priceMap.get("brokerage");
                    // 将 PriceInfoCache 对象添加到对应的 ArrayList 中
                    productMap.get(key).add(new PriceInfoCache(datePart, totalPrice, taxes, roomPrice, stayPrice, storePayPrice, brokerage));
                }
            });
        }
    }

    /**
     * @description:截取时间
     * @author: dick_w
     * @date: 2025/3/12 10:23
     * @param: [priceKey]
     * @return: java.lang.String
     **/
    private String getDatePartFromPriceKey(String priceKey) {
        return priceKey.split(":")[2];
    }

    /**
     * @description:转换价格jsonstr
     * @author: dick_w
     * @date: 2025/3/18 16:45
     * @param: [productRespDTO, priceInfo]
     * @return: java.lang.String
     **/
    private String convertPriceJsonStr(ProductRespDTO productRespDTO,PriceInfo priceInfo) {

        // 使用 Map 构建 JSON 数据
        Map<String, Integer> jsonMap = new HashMap<>();
        if(null == priceInfo){
            jsonMap.put("price", 0);
            jsonMap.put("taxes", null);
            jsonMap.put("roomPrice", null);
            jsonMap.put("stayPrice", null);
            jsonMap.put("storePayPrice",null);
            jsonMap.put("brokerage",0);
        }else{
            jsonMap.put("price", null != priceInfo.getPrice()?priceInfo.getPrice():0);
            jsonMap.put("taxes", priceInfo.getTaxes());
            jsonMap.put("roomPrice", priceInfo.getRoomPrice());
            jsonMap.put("stayPrice", productRespDTO.getStayPrice());
            jsonMap.put("storePayPrice",productRespDTO.getStorePayPrice());
            jsonMap.put("brokerage",productRespDTO.getBrokerage());
        }
        // 使用 Jackson 将 Map 转换为 JSON 字符串
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = null;
        try {
            jsonString = objectMapper.writeValueAsString(jsonMap);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return jsonString;
    }

}
