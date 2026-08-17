package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfoCache;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespCacheDTO;
import com.trip.booking.spa.gateway.application.pricing.CachePriceService;
import com.trip.booking.spa.platform.redis.RedisUtils;
import com.trip.booking.spa.platform.util.DateUtil;
import com.trip.booking.spa.platform.util.JsonUtils;
import com.trip.booking.spa.platform.util.ModelConverterUtils;
import com.trip.booking.spa.platform.util.RedisKeyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

    /** 异常价拦截（price-refresh.md F-7）；供应商通用，接一家新供应商即自动受保护 */
    @Autowired
    private AbnormalPriceGuard abnormalPriceGuard;

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
            Integer totalTaxes = value.stream().filter(a -> !Objects.isNull(a.getTaxes())).mapToInt(PriceInfoCache::getTaxes).sum();
            //计算roomPrice总价
            Integer roomTotalPrice = value.stream().filter(a -> !Objects.isNull(a.getRoomPrice())).mapToInt(PriceInfoCache::getRoomPrice).sum();
            //计算酒店一次性收取费用总价
            Integer stayPriceTotal = value.stream().filter(a -> !Objects.isNull(a.getStayPrice())).mapToInt(PriceInfoCache::getStayPrice).sum();
            //计算线下支付金额总价
            Integer storePayPricePrice = value.stream().filter(a -> !Objects.isNull(a.getStorePayPrice())).mapToInt(PriceInfoCache::getStorePayPrice).sum();
            //计算佣金总价
            Integer brokerage = value.stream().filter(a -> !Objects.isNull(a.getBrokerage())).mapToInt(PriceInfoCache::getBrokerage).sum();
            //如果缓存的价格为0，则不返回这个产品价格信息
            if (totalPrice == 0) {
                return;
            }
            //如果List<PriceInfo>的size不等于List<String> keyList的大小，就证明某一天没价格数据，则不返回该产品信息
            if (keyList.size() != value.size()) {
                return;
            }
            //如果size相同，但某一天价格为0，则不返回该产品信息
            if (keyList.size() == value.size()) {
                if (value.stream().anyMatch(priceInfo -> 0 == priceInfo.getPrice().intValue())) {
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
            List<PriceInfo> priceInfos = ModelConverterUtils.convert(value, PriceInfo.class);
            respDTO.setPriceInfos(priceInfos);
            respDTO.setStayPrice(stayPriceTotal);
            respDTO.setStorePayPrice(storePayPricePrice);
            respDTO.setBrokerage(brokerage);
            respDTOList.add(respDTO);
        });

        return respDTOList;
    }

    /**
     * 取缓存中该产品该日期的上一轮价（分），供 F-7 异常价拦截作基准。
     *
     * <p>取不到一律返回 null（首刷、键已过期、值为下架标记 0、内容不可解析），
     * 由 {@link AbnormalPriceGuard} 按"无基准即放行"处理——没有依据就不该拦，
     * 拦掉首刷会让新产品永远进不了缓存。
     */
    private Integer cachedPriceCents(String priceKey, String productId) {
        try {
            Map<String, String> cached = redisUtils.hashMapGet(priceKey);
            if (MapUtils.isEmpty(cached)) {
                return null;
            }
            String json = cached.get(productId);
            if (StringUtils.isBlank(json) || "0".equals(json)) {
                return null;
            }
            Map<String, Integer> priceMap = JsonUtils.decodeJson(json, new TypeReference<>() {
            });
            return priceMap == null ? null : priceMap.get("price");
        } catch (Exception e) {
            // 读不到基准不该阻断刷价：拿不到旧价就放行新价，等同于本次不做拦截
            log.warn("异常价拦截：读取基准价失败，本次放行,priceKey={},productId={}", priceKey, productId);
            return null;
        }
    }

    @Override
    public void productToCache(List<ProductRespDTO> list, PriceReq request) {
        try {
            if (list == null || list.isEmpty()) {
                log.info("productToCache list is empty");
                return;
            }

            // 产品信息
            Map<String, ProductRespCacheDTO> productRespCacheDTOMap = new HashMap<>();

            // 新报价数据 key:product:hotelId:productId value:价格
            Map<String, Map<String, String>> dataMap = Maps.newHashMap();
            // 缓存报价
            Map<String, Set<String>> cacheProductPriceMap = Maps.newHashMap();
            // 没有报价要下线集合
            Map<String, Map<String, String>> downDataMap = Maps.newHashMap();
            // 被 F-7 拦下的产品（priceKey → productId 集合）。它们不进 dataMap，
            // 但【绝不能被下架逻辑当成"本轮无价"而置 0】——那等于把"疑似错价"恶化成
            // "确定无货"，比不拦截更糟。故单独记一份，供下方下架判断排除
            Map<String, Set<String>> interceptedMap = Maps.newHashMap();

            List<String> dateSet = DateUtil.getDatesBetween(request.getCheckIn(), request.getCheckout());

            for (ProductRespDTO productRespDTO : list) {

                //获取全部日期报价
                dateSet.forEach(date -> {
                    String priceKey = RedisKeyUtils.buildPriceKey(productRespDTO.getHotelId(), date); // price:hotelId:date
                    //key 是产品Id value是价格
                    Map<String, String> mapGet = redisUtils.hashMapGet(priceKey);
                    if (MapUtils.isNotEmpty(mapGet)) {
                        mapGet.forEach((key, value) -> {
                            //只处理不等于0的 改下没下的
                            if (StringUtils.isNotBlank(value) && !value.equals("0")) {
                                if (cacheProductPriceMap.containsKey(priceKey)) {
                                    cacheProductPriceMap.get(priceKey).add(key);
                                } else {
                                    cacheProductPriceMap.put(priceKey, Sets.newHashSet(key));
                                }
                            }
                        });
                    }
                });

                String priceInfoKey = RedisKeyUtils.buildPriceInfoKey(productRespDTO.getHotelId(), productRespDTO.getProductId());

                String productInfoJson = redisUtils.get(priceInfoKey);

                if (CollectionUtils.isNotEmpty(productRespDTO.getPriceInfos())) {

                    if (StringUtils.isBlank(productInfoJson)) {
                        ProductRespCacheDTO respCacheDTO = new ProductRespCacheDTO();
                        BeanUtils.copyProperties(productRespDTO, respCacheDTO);
                        productRespCacheDTOMap.put(priceInfoKey, respCacheDTO);
                    } else {
                        // 这里处理早餐变更的数据
                        ProductRespCacheDTO respCacheDTO = JsonUtils.decodeJson(productInfoJson, new TypeReference<>() {
                        });
                        // 如果早餐有变化，则把发生早餐变化的产品id放入changeBreakfastSet，更新缓存productRespCacheDTOMap
                        // 用 Objects.equals 而非 count.equals：缓存中可能存在 count 为空的历史数据
                        if (respCacheDTO.getMeal() != null && productRespDTO.getMeal() != null
                                && !Objects.equals(respCacheDTO.getMeal().count, productRespDTO.getMeal().getCount())) {
                            //产品变更早餐
                            ProductRespCacheDTO dto = new ProductRespCacheDTO();
                            BeanUtils.copyProperties(productRespDTO, dto);
                            productRespCacheDTOMap.put(priceInfoKey, dto);
                        }
                    }

                    List<PriceInfo> infos = productRespDTO.getPriceInfos();

                    // 这个for用来处理变价的逻辑
                    infos.forEach(i -> {
                        String priceKey = RedisKeyUtils.buildPriceKey(productRespDTO.getHotelId(), i.getDate()); // rediskey:price:hotelid:date
                        // F-7 异常价拦截：新价相对缓存中的上一轮价暴跌时拒绝写入、保留旧价。
                        // 放在写 dataMap 之前——一旦进了 dataMap 就会被批量写进 Redis，届时错价已对外可见
                        Integer oldCents = cachedPriceCents(priceKey, productRespDTO.getProductId());
                        if (abnormalPriceGuard.isAbnormalDrop(oldCents, i.getPrice())) {
                            abnormalPriceGuard.logIntercepted(productRespDTO.getHotelId(),
                                    productRespDTO.getProductId(), i.getDate(), oldCents, i.getPrice());
                            interceptedMap.computeIfAbsent(priceKey, k -> Sets.newHashSet())
                                    .add(productRespDTO.getProductId());
                            return;
                        }
                        //priceJson：{"brokerage":2317,"roomPrice":12728,"price":14658,"storePayPrice":null,"taxes":1930,"stayPrice":0}
                        dataMap.computeIfAbsent(priceKey, k -> new HashMap<>()).put(productRespDTO.getProductId(), convertPriceJsonStr(productRespDTO, i));
                    });
                }
            }

            if (MapUtils.isNotEmpty(cacheProductPriceMap)) {
                cacheProductPriceMap.forEach((key, value) -> {
                    Set<String> intercepted = interceptedMap.getOrDefault(key, Collections.emptySet());
                    if (MapUtils.isEmpty(dataMap.get(key))) {
                        for (String productId : value) {
                            if (intercepted.contains(productId)) {
                                continue;
                            }
                            Map<String, String> zeroPriceMap = Maps.newHashMap();
                            zeroPriceMap.put(productId, convertPriceJsonStr(null, null));
                            downDataMap.put(key, zeroPriceMap);
                        }
                    } else {
                        for (String productId : value) {
                            if (intercepted.contains(productId)) {
                                continue;
                            }
                            if (StringUtils.isBlank(dataMap.get(key).get(productId))) {
                                Map<String, String> zeroPriceMap = Maps.newHashMap();
                                zeroPriceMap.put(productId, convertPriceJsonStr(null, null));
                                downDataMap.put(key, zeroPriceMap);
                            }
                        }

                    }
                });
            }

            // 存储到Redis
            if (dataMap.size() > 0) {
                // 把所有价格数据存储到redis，设置过期时间为1天，redis数据结构为hash
                redisUtils.batchHashMapSetWithExpire(dataMap, 1, TimeUnit.DAYS);
            }
            //储存除价格其他信
            // 遍历productRespCacheDTOMap，productRespCacheDTOMap里的数据存储到redis，redis数据类型为String，productRespCacheDTOMap存储的是整条产品的基本信息，有效时间为3天
            if (productRespCacheDTOMap.size() > 0) {
                productRespCacheDTOMap.forEach((key, value) -> {
                    redisUtils.setex(key, JsonUtils.writeObject2Json(value), ONE_DAY * 3);
                });
            }

            if (!downDataMap.isEmpty()) {
                redisUtils.batchHashMapSetWithExpire(downDataMap, 1, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("productToCache error:", e);
        }
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
                    Integer taxes = Objects.isNull(priceMap.get("taxes")) ? null : priceMap.get("taxes");
                    Integer roomPrice = Objects.isNull(priceMap.get("roomPrice")) ? null : priceMap.get("roomPrice");
                    Integer stayPrice = Objects.isNull(priceMap.get("stayPrice")) ? null : priceMap.get("stayPrice");
                    Integer storePayPrice = Objects.isNull(priceMap.get("storePayPrice")) ? null : priceMap.get("storePayPrice");
                    Integer brokerage = Objects.isNull(priceMap.get("brokerage")) ? null : priceMap.get("brokerage");
                    productMap.computeIfAbsent(supplier.getSProductId(), k -> new ArrayList<>()).add(new PriceInfoCache(datePart, totalPrice, taxes, roomPrice, stayPrice, storePayPrice, brokerage));
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
                    Integer taxes = Objects.isNull(priceMap.get("taxes")) ? null : priceMap.get("taxes");
                    Integer roomPrice = Objects.isNull(priceMap.get("roomPrice")) ? null : priceMap.get("roomPrice");
                    Integer stayPrice = Objects.isNull(priceMap.get("stayPrice")) ? null : priceMap.get("stayPrice");
                    Integer storePayPrice = Objects.isNull(priceMap.get("storePayPrice")) ? null : priceMap.get("storePayPrice");
                    Integer brokerage = Objects.isNull(priceMap.get("brokerage")) ? null : priceMap.get("brokerage");
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
    private String convertPriceJsonStr(ProductRespDTO productRespDTO, PriceInfo priceInfo) {

        // 使用 Map 构建 JSON 数据
        Map<String, Integer> jsonMap = new HashMap<>();
        if (null == priceInfo) {
            jsonMap.put("price", 0);
            jsonMap.put("taxes", null);
            jsonMap.put("roomPrice", null);
            jsonMap.put("stayPrice", null);
            jsonMap.put("storePayPrice", null);
            jsonMap.put("brokerage", 0);
        } else {
            jsonMap.put("price", null != priceInfo.getPrice() ? priceInfo.getPrice() : 0);
            jsonMap.put("taxes", priceInfo.getTaxes());
            jsonMap.put("roomPrice", priceInfo.getRoomPrice());
            jsonMap.put("stayPrice", productRespDTO.getStayPrice());
            jsonMap.put("storePayPrice", productRespDTO.getStorePayPrice());
            jsonMap.put("brokerage", productRespDTO.getBrokerage());
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
