package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing.client.BatchGoodsAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanBatchGoodsRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanBatchGoodsResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanGoods;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanHotelGoods;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanPriceModel;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.checkprice.client.OrderCheckAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanCodes;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanOrderCheckRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanOrderCheckResponse;
import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import java.math.BigDecimal;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.product.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.Meal;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.gateway.domain.product.PriceInfo;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.ProductInfo;
import com.trip.booking.spa.gateway.domain.product.Room;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.DropReason;
import com.trip.booking.spa.platform.observability.FunnelStage;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.shared.StayDates;

/**
 * 美团查价：供应商响应 → 可售产品。验价那几个钩子在 {@code MeituanCheckPriceServiceImpl}，
 * 供应商侧的读法集中在本类。
 *
 * <p><b>价格口径（B4）</b>：{@code price} 是「每间每晚」，单位已是<b>分</b>（不必再乘 100）。
 * 对外的 {@code totalPrice} 按 B4 统一为「<b>全部间数 × 全部夜</b> × 含税 × 单一币种」，
 * 故 = Σ逐日价 × 间数。依据是 2026-09-14 生产实测：同一产品两晚，问 1 间与问 2 间返回的
 * 逐日价一模一样（11655/11655 对 11636/11636），即间数不进单价。
 *
 * <p><b>币种只在请求里声明，响应不带</b>：故 {@link MeituanProperties#getCurrency()} 是
 * 币种的唯一出处，而不是"报文没给时的兜底"。这与另五家相反，配错即全线错币种。
 *
 * <p><b>只卖即时确认</b>：{@code confirmType=2}（需酒店二次确认）一律丢弃并计
 * {@link DropReason#ON_REQUEST}——本网关的下单语义是"下了就算数"。实测 2,926 条产品里
 * 143 条属这一档。
 */
@Slf4j
@Service
public class MeituanPriceServiceImpl implements MeituanPriceService {

    @Resource
    private MeituanProperties properties;

    @Resource
    private MeituanProductKeyDeriver productKeyDeriver;

    @Resource
    private OfferStore offerStore;

    @Override
    public PricingResult queryPrices(PriceQuery request, CallPurpose purpose) {
        if (!properties.isConfigured()) {
            log.error("美团查价：凭证未配置,sHotelId={}", request.supplierHotelId());
            return PricingResult.indeterminate();
        }
        if (StringUtils.isBlank(request.supplierHotelId())) {
            log.error("美团查价：酒店 id 为空，无法调用");
            return PricingResult.indeterminate();
        }
        PriceQuery withOccupancy = request.occupancies() == null || request.occupancies().isEmpty()
                ? request.toBuilder().occupancies(Occupancy.perRoom(request.roomNum(), request.adultNum(),
                        request.childNum(), request.childAges())).build()
                : request;
        MeituanBatchGoodsResponse data = fetch(withOccupancy, purpose);
        if (data == null) {
            log.warn("美团查价：调用未取得结果,sHotelId={},checkIn={}",
                    withOccupancy.supplierHotelId(), withOccupancy.checkIn());
            return PricingResult.indeterminate();
        }
        return toPricingResult(data, withOccupancy);
    }

    /**
     * 响应 → 三态。
     *
     * <p>{@code code != 0} 一律"不确定"：本家的非零码全是系统层面的失败（2000 内部错误、
     * 参数缺失等），<b>没有一个码表示"这家这住期没货"</b>——没货的表达是 {@code code=0} 且
     * {@code result} 为空。故不许把失败读成无货去清缓存（F-5.1）。
     */
    PricingResult toPricingResult(MeituanBatchGoodsResponse data, PriceQuery request) {
        if (!data.isSucc()) {
            log.warn("美团查价：业务失败，按不确定处理,sHotelId={},code={},message={}",
                    request.supplierHotelId(), data.getCode(), data.getMessage());
            return PricingResult.indeterminate();
        }
        MeituanHotelGoods hotel = data.hotelOf(request.supplierHotelId());
        if (hotel == null || CollectionUtils.isEmpty(hotel.getGoodsList())) {
            log.info("美团查价：无库存,sHotelId={},checkIn={}", request.supplierHotelId(), request.checkIn());
            return PricingResult.noInventory();
        }
        List<Product> products = convert(hotel, request, Instant.now());
        return products.isEmpty() ? PricingResult.noInventory() : PricingResult.available(products);
    }

    /** 现货 → 产品列表。每个丢弃分支都要计 {@code quote_dropped}（architecture.md §5 第二步） */
    List<Product> convert(MeituanHotelGoods hotel, PriceQuery request, Instant now) {
        List<Product> products = new ArrayList<>();
        if (hotel == null || CollectionUtils.isEmpty(hotel.getGoodsList())) {
            return products;
        }
        int nights = StayDates.nights(request.checkIn(), request.checkOut());
        int rooms = Math.max(1, request.roomNum());
        String occupancy = request.occupancies() == null || request.occupancies().isEmpty()
                ? null : request.occupancies().get(0);
        String hotelId = String.valueOf(hotel.getHotelId());
        int onRequest = 0;
        int noDayPrice = 0;
        int zeroPrice = 0;
        int noRoomId = 0;

        for (MeituanGoods goods : hotel.getGoodsList()) {
            if (goods == null || goods.getGoodsId() == null) {
                continue;
            }
            if (!goods.isInstantConfirm()) {
                onRequest++;
                continue;
            }
            if (goods.getRealRoomId() == null) {
                // 没有物理房型就没有房型维度的身份成分，productKey 会与别的产品撞在一起
                noRoomId++;
                continue;
            }
            List<MeituanPriceModel> days = goods.getPriceModelList();
            if (CollectionUtils.isEmpty(days) || days.size() != nights) {
                // 逐晚价不齐即无法给出住期总价（也无法做逐晚明细），不猜缺的那晚
                noDayPrice++;
                continue;
            }
            Integer totalCents = totalCents(days, rooms);
            if (totalCents == null || totalCents <= 0) {
                zeroPrice++;
                continue;
            }
            products.add(convertGoods(hotelId, goods, days, totalCents, rooms, occupancy, request, now));
        }
        countDropped(DropReason.ON_REQUEST, onRequest);
        countDropped(DropReason.QUOTE_DETAIL_MISSING, noRoomId);
        countDropped(DropReason.NO_DAY_PRICE, noDayPrice);
        countDropped(DropReason.ZERO_TOTAL_PRICE, zeroPrice);
        log.info("美团 查价：转换完成,hotelId={},checkIn={},出报={},跳过_非即时确认={},跳过_无房型={},"
                        + "跳过_逐晚价不齐={},跳过_零价={}",
                hotelId, request.checkIn(), products.size(), onRequest, noRoomId, noDayPrice, zeroPrice);
        return products;
    }

    private Product convertGoods(String hotelId, MeituanGoods goods, List<MeituanPriceModel> days,
                                 int totalCents, int rooms, String occupancy, PriceQuery request, Instant now) {
        Meal meal = productKeyDeriver.convertMeal(goods.getMealType());
        // 报价档的罚金是单间口径，故 penaltyPerRoom=true，由 deriver 乘上间数
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.checkIn(), goods.getRefundable(), goods.getCpApply(), rooms, true, now);
        String realRoomId = String.valueOf(goods.getRealRoomId());
        // 身份与成分一次算出（R-2.8）：建档照抄 identity，不得再判一遍
        ProductIdentity identity = productKeyDeriver.deriveIdentity(hotelId, realRoomId,
                meal, cancelPolicy, occupancy, totalCents);
        Product product = Product.builder()
                .hotelId(hotelId)
                // 报价标识=goodsId；身份=productKey，二者永不同字段（R-2.3）
                .productId(String.valueOf(goods.getGoodsId()))
                .productKey(identity.productKey())
                .identity(identity)
                .supplierId(SupplierSourceEnum.MEITUAN.getCode())
                .room(Room.builder().roomId(realRoomId).roomName(roomName(goods)).build())
                .productInfo(ProductInfo.builder()
                        // 本家报价不带可售间数，故留空——不拿"能问到价"当"有货 N 间"
                        .inventory(null)
                        .productStatus(1)
                        .productName(roomName(goods))
                        .build())
                // 币种不在报文里，只在我们发出的请求里声明，见类注释
                .currencyType(properties.getCurrency())
                // B4：全部间数 × 全部夜。逐晚明细仍按单间给（明细的自然口径），故两者不等价
                .totalPrice(totalCents)
                .priceInfos(buildPriceInfos(days))
                .meal(meal)
                .cancelPolicy(cancelPolicy)
                .build();
        // 报价是否含税报文未申明，故不另报税额——不把一个没问过的数字说成 0 以外的任何值
        product.setTotalTaxes(0);
        return product;
    }

    /** Σ逐日价 × 间数（B4）。价格已是分。任一晚缺失或非正返回 null */
    static Integer totalCents(List<MeituanPriceModel> days, int rooms) {
        long perRoom = 0;
        for (MeituanPriceModel day : days) {
            if (day == null || day.getPrice() == null || day.getPrice() <= 0) {
                return null;
            }
            perRoom += day.getPrice();
        }
        try {
            return Math.toIntExact(perRoom * Math.max(1, rooms));
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** 逐晚明细按<b>单间</b>给：这是明细的自然口径，与总价的全间数口径刻意不同，见类注释 B4 段 */
    static List<PriceInfo> buildPriceInfos(List<MeituanPriceModel> days) {
        List<PriceInfo> infos = new ArrayList<>(days.size());
        for (MeituanPriceModel day : days) {
            infos.add(PriceInfo.builder()
                    .date(day.getDate())
                    .price(Math.toIntExact(day.getPrice()))
                    .build());
        }
        return infos;
    }

    /** 发一次批量查价。调用失败返回 null */
    MeituanBatchGoodsResponse fetch(PriceQuery request, CallPurpose purpose) {
        MeituanBatchGoodsRequest body = MeituanBatchGoodsRequest.builder()
                .hotelIds(List.of(Long.parseLong(request.supplierHotelId())))
                .checkinDate(request.checkIn())
                .checkoutDate(request.checkOut())
                // 官方参数表：这几项是「每间房」的人数，不是总数
                .numberOfAdults(Math.max(1, request.adultNum()))
                .numberOfChildren(request.childNum())
                .childrenAges(CollectionUtils.isEmpty(request.childAges()) ? null
                        : StringUtils.join(request.childAges(), ","))
                .currencyCode(properties.getCurrency())
                // 国籍筛的是可售集合，报价与验价必须同值，否则"列表里有、点进去没有"
                .clientNationality(properties.getClientNationality())
                .build();
        ResponseResult<MeituanBatchGoodsResponse> result = new BatchGoodsAccess(properties).access(body, purpose);
        return result == null ? null : result.getData();
    }

    private static String roomName(MeituanGoods goods) {
        return StringUtils.isNotBlank(goods.getGoodsName()) ? goods.getGoodsName() : goods.getGoodsNameEn();
    }

    /**
     * 本类的丢弃计数：只绑定"是哪一家、丢在哪一层"，怎么记在 {@link Monitor#recordDropped}。
     * 这个切分就是 §4.1.3 的判据——换一家供应商要改的只有这两个常量。
     */
    private static void countDropped(DropReason reason, int count) {
        Monitor.recordDropped(SupplierSourceEnum.MEITUAN, FunnelStage.CONVERT, reason, count);
    }

    // ────────── 验价钩子（流程在 AbstractCheckPriceFlow，供应商侧的读法在这里）──────────

    public CheckPriceResult precondition(CheckPriceCommand request) {
        if (!properties.isConfigured()) {
            log.error("美团验价：凭证未配置,sHotelId={}", request.supplierHotelId());
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "供应商凭证未配置，未能确认该产品是否可订");
        }
        if (StringUtils.isBlank(request.supplierHotelId())) {
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "酒店标识为空，未能确认该产品是否可订");
        }
        return null;
    }

    /** 现取整店现货。失败与无货分开：本家的非零码全是系统失败，没有一个表示"没货" */
    public LiveStock<MeituanHotelGoods> fetchLiveStock(CheckPriceCommand request) {
        PriceQuery asQuery = asQuery(request);
        MeituanBatchGoodsResponse data = fetch(asQuery, CallPurpose.CHECK_PRICE);
        if (data == null) {
            log.warn("美团验价：现货查询未取得结果,sHotelId={},sProductId={}",
                    request.supplierHotelId(), request.supplierProductId());
            return LiveStock.terminal(CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE,
                    "现货查询未取得结果，未能确认该产品是否可订，请稍后重试"));
        }
        MeituanHotelGoods hotel = data.hotelOf(request.supplierHotelId());
        Instant now = Instant.now();
        // 验价即刷：闭包捕获这份现货，终态分支也带着它返回——整店无售正是要落无货标记的时候
        java.util.function.Function<PriceQuery, List<Product>> fresh =
                priceReq -> convert(hotel, priceReq, now);
        if (!data.isSucc()) {
            log.info("美团验价：现货查询业务失败,sHotelId={},code={},message={}",
                    request.supplierHotelId(), data.getCode(), data.getMessage());
            return LiveStock.terminal(CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE,
                    "现货查询失败，未能确认该产品是否可订"));
        }
        if (hotel == null || CollectionUtils.isEmpty(hotel.getGoodsList())) {
            log.info("美团验价：该店该住期无在售产品,sHotelId={}", request.supplierHotelId());
            return LiveStock.<MeituanHotelGoods>terminal(CheckPriceResult.of(CheckPriceOutcome.SOLD_OUT,
                    "该酒店该住期已无在售产品")).freshConvertedBy(fresh);
        }
        return LiveStock.of(hotel).freshConvertedBy(fresh);
    }

    /** 按 goodsId 精确找票 */
    public MeituanGoods findGoods(MeituanHotelGoods hotel, String goodsId) {
        if (hotel == null || StringUtils.isBlank(goodsId) || CollectionUtils.isEmpty(hotel.getGoodsList())) {
            return null;
        }
        for (MeituanGoods goods : hotel.getGoodsList()) {
            if (goods != null && goods.getGoodsId() != null && goodsId.equals(String.valueOf(goods.getGoodsId()))) {
                return goods;
            }
        }
        return null;
    }

    /** 换票候选：同 productKey 的现货。判据必须与出报侧逐条同源，否则换出来的不是同一种卖法 */
    public List<ResolveCandidate<MeituanGoods>> resolveCandidates(MeituanHotelGoods hotel, CheckPriceCommand request) {
        List<ResolveCandidate<MeituanGoods>> candidates = new ArrayList<>();
        if (hotel == null || CollectionUtils.isEmpty(hotel.getGoodsList())) {
            return candidates;
        }
        int nights = StayDates.nights(request.checkIn(), request.checkOut());
        int rooms = Math.max(1, request.roomNum());
        Instant now = Instant.now();
        String occupancy = Occupancy.perRoom(rooms, request.adultCount() == null ? 1 : request.adultCount(),
                request.childNum(), request.childAges() == null ? List.of() : request.childAges()).get(0);
        for (MeituanGoods goods : hotel.getGoodsList()) {
            if (goods == null || goods.getGoodsId() == null || goods.getRealRoomId() == null
                    || !goods.isInstantConfirm()) {
                continue;
            }
            List<MeituanPriceModel> days = goods.getPriceModelList();
            if (CollectionUtils.isEmpty(days) || days.size() != nights) {
                continue;
            }
            Integer totalCents = totalCents(days, rooms);
            if (totalCents == null || totalCents <= 0) {
                continue;
            }
            Meal meal = productKeyDeriver.convertMeal(goods.getMealType());
            List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                    request.checkIn(), goods.getRefundable(), goods.getCpApply(), rooms, true, now);
            String key = productKeyDeriver.deriveProductKey(String.valueOf(hotel.getHotelId()),
                    String.valueOf(goods.getRealRoomId()), meal, cancelPolicy, occupancy, totalCents);
            if (key.equals(request.productKey())) {
                candidates.add(new ResolveCandidate<>(goods, totalCents));
            }
        }
        return candidates;
    }

    /** 曝光档：只报现货，不打 order.check——那一档没必要占供应商的校验配额 */
    public CheckPriceResult availabilityOnlyResp(CheckPriceCommand request, MeituanGoods candidate) {
        int rooms = Math.max(1, request.roomNum());
        List<MeituanPriceModel> days = candidate.getPriceModelList();
        Integer totalCents = CollectionUtils.isEmpty(days) ? null : totalCents(days, rooms);
        if (totalCents == null || totalCents <= 0) {
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "供应商未给出可用价格，未能确认该产品");
        }
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.checkIn(), candidate.getRefundable(), candidate.getCpApply(), rooms, true, Instant.now());
        log.info("美团 验价(仅现货)：有货但未校验,sHotelId={},goodsId={},价格={}分,退改条数={}",
                request.supplierHotelId(), candidate.getGoodsId(), totalCents, cancelPolicy.size());
        return CheckPriceResult.builder()
                .outcome(CheckPriceOutcome.AVAILABLE)
                .salePrice(totalCents)
                .subPrice(totalCents)
                .currencyType(properties.getCurrency())
                .cancelPolicy(cancelPolicy)
                .priceInfos(buildPriceInfos(days))
                .build();
    }

    /**
     * 下单前档：打一次 order.check。
     *
     * <p>码的三分是本家最要紧的判据，错一档就要么少卖要么卖出订不到的货：
     * <ul>
     *   <li>{@code 0} → 可订，按<b>验后价</b>签句柄（不用查价那一版的价）</li>
     *   <li>{@code 3/4/5/6}（房态不满足／不可售／产品不存在／库存不足）→ <b>确定不可订</b>。
     *       其中"产品不存在"判 RATE_DEAD（重新查价可能换到等价票），其余判 SOLD_OUT</li>
     *   <li>其余（含 1 校验失败、2 酒店被拉黑、2000 系统错误）→ 不确定。拉黑与校验失败虽然
     *       听着确定，但它们说的是"这次调用被挡下"而非"这个产品没货"，混进确定档会把一次
     *       策略性拦截写成售罄</li>
     * </ul>
     */
    public CheckPriceResult validate(CheckPriceCommand request, MeituanGoods candidate) {
        int rooms = Math.max(1, request.roomNum());
        MeituanOrderCheckRequest body = MeituanOrderCheckRequest.builder()
                .hotelId(Long.parseLong(request.supplierHotelId()))
                .goodsId(candidate.getGoodsId())
                .checkinDate(request.checkIn())
                .checkoutDate(request.checkOut())
                .numberOfAdults(request.adultCount() == null ? 1 : request.adultCount())
                .numberOfChildren(request.childNum())
                .childrenAges(Occupancy.childAgesCsv(request.childAges()))
                .roomNum(rooms)
                .currencyCode(properties.getCurrency())
                .clientNationality(properties.getClientNationality())
                .build();
        ResponseResult<MeituanOrderCheckResponse> result = new OrderCheckAccess(properties)
                .access(body, CallPurpose.CHECK_PRICE);
        MeituanOrderCheckResponse data = result == null ? null : result.getData();
        if (data == null) {
            log.warn("美团 下单前校验：未取得结果,sHotelId={},goodsId={}",
                    request.supplierHotelId(), candidate.getGoodsId());
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "校验未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        if (!data.isSucc()) {
            CheckPriceOutcome terminal = terminalOf(data.getCode());
            log.info("美团 下单前校验：业务失败,sHotelId={},goodsId={},roomNum={},code={},message={},判定={}",
                    request.supplierHotelId(), candidate.getGoodsId(), rooms,
                    data.getCode(), data.getMessage(), terminal);
            return CheckPriceResult.of(terminal, message(terminal));
        }
        return buildBookable(request, candidate, data.getResult(), rooms);
    }

    /** 业务码 → 终态。只有"确定订不到"的那几个码进确定档，其余一律不确定（B6） */
    static CheckPriceOutcome terminalOf(Integer code) {
        if (code == null) {
            return CheckPriceOutcome.INDETERMINATE;
        }
        if (code == MeituanCodes.CHECK_PRODUCT_NOT_EXIST) {
            return CheckPriceOutcome.RATE_DEAD;
        }
        if (code == MeituanCodes.CHECK_ROOM_STATUS_NOT_AVAILABLE
                || code == MeituanCodes.CHECK_PRODUCT_NOT_SELLABLE
                || code == MeituanCodes.CHECK_STOCK_INSUFFICIENT) {
            return CheckPriceOutcome.SOLD_OUT;
        }
        return CheckPriceOutcome.INDETERMINATE;
    }

    private static String message(CheckPriceOutcome terminal) {
        return switch (terminal) {
            case RATE_DEAD -> "该产品已不可订，请重新查价后再选择";
            case SOLD_OUT -> "该产品该住期已订不到所需间数";
            default -> "校验未取得确定结果，未能确认该产品是否可订";
        };
    }

    private CheckPriceResult buildBookable(CheckPriceCommand request, MeituanGoods candidate,
                                           MeituanOrderCheckResponse.CheckResult confirmed, int rooms) {
        List<MeituanPriceModel> days = confirmed == null ? null : confirmed.getPriceModelList();
        Integer totalCents = CollectionUtils.isEmpty(days) ? null : totalCents(days, rooms);
        if (totalCents == null || totalCents <= 0) {
            log.error("美团 下单前校验：校验通过却无可用价格,sHotelId={},goodsId={}",
                    request.supplierHotelId(), candidate.getGoodsId());
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "校验未回可用价格，未能确认该产品是否可订");
        }
        java.util.Map<String, String> credentials = new java.util.HashMap<>();
        credentials.put(MeituanOfferCredentials.HOTEL_ID, request.supplierHotelId());
        credentials.put(MeituanOfferCredentials.GOODS_ID, String.valueOf(candidate.getGoodsId()));
        credentials.put(MeituanOfferCredentials.REAL_ROOM_ID, String.valueOf(candidate.getRealRoomId()));
        credentials.put(MeituanOfferCredentials.CHECK_IN, request.checkIn());
        credentials.put(MeituanOfferCredentials.CHECK_OUT, request.checkOut());
        credentials.put(MeituanOfferCredentials.ROOM_NUM, String.valueOf(rooms));
        credentials.put(MeituanOfferCredentials.ADULT_COUNT,
                String.valueOf(request.adultCount() == null ? 1 : request.adultCount()));
        credentials.put(MeituanOfferCredentials.CHILD_COUNT, String.valueOf(request.childNum()));
        credentials.put(MeituanOfferCredentials.CHILD_AGES, Occupancy.childAgesCsv(request.childAges()));
        credentials.put(MeituanOfferCredentials.DECLARED_TOTAL,
                BigDecimal.valueOf(totalCents).movePointLeft(2).toPlainString());
        credentials.put(MeituanOfferCredentials.CURRENCY, properties.getCurrency());
        credentials.put(MeituanOfferCredentials.CLIENT_NATIONALITY, properties.getClientNationality());
        String offerId = offerStore.issue(SupplierSourceEnum.MEITUAN.getCode(), credentials);
        if (StringUtils.isBlank(offerId)) {
            return CheckPriceResult.of(CheckPriceOutcome.INDETERMINATE, "报价句柄签发失败，请稍后重试");
        }
        // 校验档的罚金已是「全部间数」口径（实测 1 间 15098/25164、2 间 30146/50244），
        // 故 penaltyPerRoom=false——再乘一次间数就是把罚金说成两倍
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(
                request.checkIn(), confirmed.getRefundable(), confirmed.getCpApply(), rooms, false, Instant.now());
        log.info("美团 下单前校验：通过并签发句柄,sHotelId={},goodsId={},salePrice={}分{},offerId={},退改条数={}",
                request.supplierHotelId(), candidate.getGoodsId(), totalCents,
                properties.getCurrency(), offerId, cancelPolicy.size());
        return CheckPriceResult.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.ttlSecondsOf(SupplierSourceEnum.MEITUAN.getCode()))
                .salePrice(totalCents)
                .subPrice(totalCents)
                .currencyType(properties.getCurrency())
                .cancelPolicy(cancelPolicy)
                .priceInfos(buildPriceInfos(days))
                .build();
    }

    private PriceQuery asQuery(CheckPriceCommand request) {
        return PriceQuery.builder()
                .supplierId(SupplierSourceEnum.MEITUAN.getCode())
                .supplierHotelId(request.supplierHotelId())
                .checkIn(request.checkIn()).checkOut(request.checkOut())
                .roomNum(request.roomNum())
                .adultNum(request.adultCount() == null ? 1 : request.adultCount())
                .childNum(request.childNum())
                .childAges(request.childAges() == null ? List.of() : request.childAges())
                .build();
    }

    /** 仅供测试构造场景使用 */
    public void setOfferStore(OfferStore offerStore) {
        this.offerStore = offerStore;
    }

    /** 仅供测试构造场景使用 */
    public void setProperties(MeituanProperties properties) {
        this.properties = properties;
    }

    /** 仅供测试构造场景使用 */
    public void setProductKeyDeriver(MeituanProductKeyDeriver productKeyDeriver) {
        this.productKeyDeriver = productKeyDeriver;
    }
}
