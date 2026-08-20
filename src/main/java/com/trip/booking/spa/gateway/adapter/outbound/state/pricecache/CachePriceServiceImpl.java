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
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
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

    /** 入缓存裁剪（price-refresh.md F-3）；同样供应商通用 */
    @Autowired
    private PriceCacheTrimmer priceCacheTrimmer;

    /** TTL 分档（price-refresh.md F-4）；按入住日期远近给不同存活期 */
    @Autowired
    private PriceCacheTtlPolicy priceCacheTtlPolicy;

    /** R-2.6 读侧：稳定属性在档案表，按 productKey 回查（缓存只留易腐码+桥+价） */
    @org.springframework.beans.factory.annotation.Autowired
    private ProductAttributeReader productAttributeReader;

    private static final long ONE_DAY = 86400L;

    /**
     * 价格缓存的<b>字段名</b>：一律用 productKey（R-1.1，跨次稳定），不用 productId。
     *
     * <p><b>为什么必须是 productKey</b>（2026-08-19 生产实证）：刷价按<b>单晚</b>切片，
     * T+0~T+6 每个日期是一次<b>独立的</b> hotel.detail 调用；而艺龙的 productId 申报为
     * {@link com.trip.booking.spa.gateway.domain.supplier.SupplierIdentityProfile.QuoteCodeStability#PERISHABLE}
     * （会话级轮换），于是同一个卖法在 08-19 与 08-20 两次调用里拿到的 productId 不同。
     * 而 {@link #getPrice} 要求产品在住期内<b>每一天</b>都有价，两天的 productId 交集恒为空，
     * 结果是<b>住 2 晚及以上一个产品都出不来</b>——实测某酒店 08-19 缓存 42 个产品、
     * 08-20 有 47 个，连续两天都在的 0 个。当时 cursor 打 SPA 出价 6,403 次，
     * 空结果 83.1%、跨云超时 8.4%、真正拿到报价仅 8.6%。
     *
     * <p>顺带解决内存的结构性增长：易腐码每轮刷价都铸出一整套全新的键，旧键要等 TTL
     * 才消失，故同时并存"TTL/刷价周期"代垃圾（提速后档位 0 约 12 代）。键稳定后每轮
     * 写的是同一批键，代数压到 1。
     *
     * <p>productKey 缺席时退回 productId：不是所有供应商都派生了键，退回可保住报价
     * （多晚查询对这部分仍不可用，属已知缺口），好过整条不报（R-1.6）。
     */
    private static String cacheField(ProductRespDTO product) {
        return StringUtils.isNotBlank(product.getProductKey())
                ? product.getProductKey() : product.getProductId();
    }

    /**
     * 该产品写入哪一片占用。
     *
     * <p><b>优先取 identity 里的占用</b>——那是真正进了 productKey 的那个值，与 field 同源；
     * 拿请求参数另算一遍就又成了"两处拼、靠约定对齐"。identity 缺席（未派生键的供应商）
     * 才回落到请求参数。
     */
    private static String occupancyOf(ProductRespDTO product, PriceReq request) {
        if (product.getIdentity() != null && StringUtils.isNotBlank(product.getIdentity().occupancy())) {
            return product.getIdentity().occupancy();
        }
        return Occupancy.canonical(request.getAdultNum(), request.getChildNum(), request.getChildAges());
    }

    @Override
    public List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier) {
        return getPrice(priceReq, supplier, null);
    }

    @Override
    public List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier, String cacheField) {
        List<ProductRespDTO> respDTOList = new ArrayList<>();
        List<String> checkList = DateUtil.getDatesBetween(priceReq.getCheckIn(), priceReq.getCheckout());
        Map<String, List<PriceInfoCache>> productMap = new HashMap<>();

        // 占用必须进键：productKey 的成分里有占用，而本方法是把整个 Hash 端出来、
        // 里面有什么报什么——不按占用分片，刷价按 1 人刷出来的价就会被原样报给 2 人的查询
        // （实测同店同日 1 人 335.46 / 2 人 293.17）。分片后 2 人查询取到空片，如实无货
        String occupancy = Occupancy.canonical(priceReq.getAdultNum(), priceReq.getChildNum(),
                priceReq.getChildAges());
        List<String> keyList = new ArrayList<>();
        checkList.forEach(c -> {
            //price:sHotelId:occupancy:yyyy-MM-dd
            String priceKey = RedisKeyUtils.buildPriceKey(supplier.getSHotelId(), occupancy, c);
            keyList.add(priceKey);
        });
        //productMap--缓存字段(productKey),List<PriceInfo>
        fetchAndProcessPriceInfo(productMap, cacheField, keyList);

        // 稳定属性从档案表批量取（R-2.6 读侧）。一次出价一次批量，避免逐条查库；
        // 命中进程内缓存后零 IO。取不到的产品仍照常出价，只是没有房型/餐食——
        // 缺属性不该让整条报价消失（R-1.6：宁可少报信息，不可少报货）
        Map<String, ProductAttributeReader.ProductAttribute> attrMap =
                productAttributeReader.batchGet(supplier.getSupplierId(), new ArrayList<>(productMap.keySet()));

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
            // 补充产品其他信息。key 是 productKey（见 cacheField），
            // 票据键随之为 quote:{sHotelId}:{productKey}
            String priceInfoKey = RedisKeyUtils.buildQuoteKey(supplier.getSHotelId(), key);
            String priceInfoJson = redisUtils.get(priceInfoKey);
            if (StringUtils.isBlank(priceInfoJson)) {
                // 详情缺席 = 拿不到可下单的票据（productId 只存在于详情里，依 R-2.1 不落库）。
                // 只有价没有票的报价不可成交，不如不报（R-1.6）
                return;
            }
            ProductRespCacheDTO productRespCacheDTO = JsonUtils.decodeJson(priceInfoJson, new TypeReference<>() {
            });
            BeanUtils.copyProperties(productRespCacheDTO, respDTO);
            respDTO.setTotalPrice(totalPrice);
            respDTO.setTotalTaxes(totalTaxes);
            respDTO.setRoomTotalPrice(roomTotalPrice);
            respDTO.setSupplierId(supplier.getSupplierId());
            // 票据取自详情（最近一轮刷价写入的那张），不是缓存字段名——字段名现在是
            // 跨次稳定的 productKey，拿它去下单会被供应商拒（它不是报价码）
            if (StringUtils.isBlank(respDTO.getProductId())) {
                return;
            }
            respDTO.setHotelId(supplier.getSHotelId());
            // 房型/餐食/产品名来自档案表，不再随每轮刷价重写进 Redis
            ProductAttributeReader.ProductAttribute attr = attrMap.get(key);
            if (attr != null) {
                respDTO.setRoom(attr.toRoom());
                respDTO.setMeal(attr.toMeal());
                respDTO.setProductInfo(attr.toProductInfo());
            }
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
     * 按 {@code price:{hotelId}:{date}} 里的日期分档写入（F-4）。
     *
     * <p>同一批数据可能横跨多个日期（一次刷价覆盖若干住期），故先按 TTL 归并再批量写，
     * 既保证各日期用对存活期，又不至于退化成逐 key 单发。
     *
     * <p>解析不出日期的键归入未来档（偏长而非偏短）——TTL 判定失误不该让刚刷回来的价
     * 立刻消失。
     */
    private void writeWithTieredTtl(Map<String, Map<String, String>> dataMap) {
        Map<Long, Map<String, Map<String, String>>> byTtl = new HashMap<>();
        dataMap.forEach((priceKey, value) -> {
            String date = StringUtils.substringAfterLast(priceKey, ":");
            long ttl = priceCacheTtlPolicy.ttlSeconds(date);
            byTtl.computeIfAbsent(ttl, k -> new HashMap<>()).put(priceKey, value);
        });
        byTtl.forEach((ttl, batch) -> redisUtils.batchHashMapSetWithExpire(batch, ttl, TimeUnit.SECONDS));
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
            // F-3 裁剪：按 productKey 等价类留最低价的前 N 条。放在最前面——
            // 后续的下架判断依赖"谁进了 dataMap"，裁剪必须先于它发生，
            // 被裁掉的产品才能正确地走下架置 0（与被 F-7 拦截者相反，见 PriceCacheTrimmer 注释）
            list = priceCacheTrimmer.trim(list);

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
                String occupancy = occupancyOf(productRespDTO, request);
                dateSet.forEach(date -> {
                    String priceKey = RedisKeyUtils.buildPriceKey(productRespDTO.getHotelId(), occupancy, date); // price:hotelId:occupancy:date
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

                String field = cacheField(productRespDTO);
                String priceInfoKey = RedisKeyUtils.buildQuoteKey(productRespDTO.getHotelId(), field);

                if (CollectionUtils.isNotEmpty(productRespDTO.getPriceInfos())) {

                    // 每轮无条件覆盖详情：键是 productKey，本就一个卖法一条，覆盖即刷新票据。
                    // 原先此处有一段「早餐变更检测」——只在早餐变了时才重写。改键后它既无必要
                    // 也无从判断：餐食是 productKey 的成分，餐食一变键就变了，那是另一条记录，
                    // 不存在"同一条的早餐变了"
                    ProductRespCacheDTO respCacheDTO = new ProductRespCacheDTO();
                    BeanUtils.copyProperties(productRespDTO, respCacheDTO);
                    productRespCacheDTOMap.put(priceInfoKey, respCacheDTO);

                    List<PriceInfo> infos = productRespDTO.getPriceInfos();

                    // 这个for用来处理变价的逻辑
                    infos.forEach(i -> {
                        String priceKey = RedisKeyUtils.buildPriceKey(productRespDTO.getHotelId(), occupancy, i.getDate()); // price:hotelId:occupancy:date
                        // F-7 异常价拦截：新价相对缓存中的上一轮价暴跌时拒绝写入、保留旧价。
                        // 放在写 dataMap 之前——一旦进了 dataMap 就会被批量写进 Redis，届时错价已对外可见
                        Integer oldCents = cachedPriceCents(priceKey, field);
                        if (abnormalPriceGuard.isAbnormalDrop(oldCents, i.getPrice())) {
                            abnormalPriceGuard.logIntercepted(productRespDTO.getHotelId(),
                                    productRespDTO.getProductId(), i.getDate(), oldCents, i.getPrice());
                            interceptedMap.computeIfAbsent(priceKey, k -> Sets.newHashSet()).add(field);
                            return;
                        }
                        //priceJson：{"brokerage":2317,"roomPrice":12728,"price":14658,"storePayPrice":null,"taxes":1930,"stayPrice":0}
                        dataMap.computeIfAbsent(priceKey, k -> new HashMap<>()).put(field, convertPriceJsonStr(productRespDTO, i));
                    });
                }
            }

            if (MapUtils.isNotEmpty(cacheProductPriceMap)) {
                // downDataMap 的一个 key（price:{hotelId}:{date}）下可能有多个产品同时下架，
                // 故必须【累加】而非整体覆盖——循环维度是 productId，用 put 会让每次迭代
                // 把该 key 的整张 map 换成只含一条的新 map，只剩最后一条真被置 0，其余保留
                // 旧价直到 TTL 过期（issue #96）。写法与上方 dataMap、interceptedMap 一致
                cacheProductPriceMap.forEach((key, value) -> {
                    Set<String> intercepted = interceptedMap.getOrDefault(key, Collections.emptySet());
                    if (MapUtils.isEmpty(dataMap.get(key))) {
                        for (String productId : value) {
                            if (intercepted.contains(productId)) {
                                continue;
                            }
                            downDataMap.computeIfAbsent(key, k -> Maps.newHashMap())
                                    .put(productId, convertPriceJsonStr(null, null));
                        }
                    } else {
                        for (String productId : value) {
                            if (intercepted.contains(productId)) {
                                continue;
                            }
                            if (StringUtils.isBlank(dataMap.get(key).get(productId))) {
                                downDataMap.computeIfAbsent(key, k -> Maps.newHashMap())
                                        .put(productId, convertPriceJsonStr(null, null));
                            }
                        }

                    }
                });
            }

            // 存储到Redis。F-4：TTL 按入住日期远近分档，不再一律 1 天——
            // 键形如 price:{hotelId}:{date}，故按 date 分组后各用各的存活期
            if (dataMap.size() > 0) {
                writeWithTieredTtl(dataMap);
            }
            //储存除价格其他信
            // 遍历productRespCacheDTOMap，productRespCacheDTOMap里的数据存储到redis，redis数据类型为String，productRespCacheDTOMap存储的是整条产品的基本信息，有效时间为3天
            if (productRespCacheDTOMap.size() > 0) {
                productRespCacheDTOMap.forEach((key, value) -> {
                    redisUtils.setex(key, JsonUtils.writeObject2Json(value), ONE_DAY * 3);
                });
            }

            if (!downDataMap.isEmpty()) {
                // 下架标记与价格同档：它代表"该产品此刻无价"，同样应随日期远近失效
                writeWithTieredTtl(downDataMap);
            }
        } catch (Exception e) {
            log.error("productToCache error:", e);
        }
    }
    /**
     * 组装价格信息。
     *
     * @param cacheField 非空则只取该字段（{@link #cacheField(ProductRespDTO)}，即 productKey）；
     *                   为空则取该店该日期下的全部字段
     **/
    private void fetchAndProcessPriceInfo(Map<String, List<PriceInfoCache>> productMap, String cacheField, List<String> keySet) {
        if (StringUtils.isNotBlank(cacheField)) {
            //根据priceKey（price:sHotelId:yyyy-MM-dd）查询map（productKey,price)
            keySet.forEach(priceKey -> {
                String price = redisUtils.hmGet(priceKey, cacheField);
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
                    productMap.computeIfAbsent(cacheField, k -> new ArrayList<>()).add(new PriceInfoCache(datePart, totalPrice, taxes, roomPrice, stayPrice, storePayPrice, brokerage));
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
