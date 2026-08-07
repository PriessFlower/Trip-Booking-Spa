package com.trip.booking.spa.core.api.expedia.staticdata.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.expedia.mapper.ExpediaCatalogMapper;
import com.trip.booking.spa.core.api.expedia.staticdata.model.ExpediaPropertyDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 阶段2 加工层：读快照表的双语行，按旧链路 ExpediaStaticInfoAdaptor 的语义双推——
 * 档案域（supplier_hotel_base / supplier_room_base，原 hotel-info.saveHotelInfo/saveRoomInfo）
 * 目录域（hotel_details / room_base / hotel_picture / hotel_extend，原 hotel-base.saveHotelDetails）。
 * 目标为 spa 专属还原表（docs/legacy-schema-restoration.md）。
 * city_id/country_id 由 Geography 档案（阶段3）落地后回填。
 */
@Slf4j
@Service
public class ExpediaCatalogTransformService {

    private static final int SUPPLIER_ID = SupplierSourceEnum.EXPEDIA.getCode();
    private static final String LANG_EN = "en-US";
    private static final String LANG_ZH = "zh-CN";

    private final ExpediaCatalogMapper catalogMapper;
    private final ObjectMapper objectMapper;

    public ExpediaCatalogTransformService(ExpediaCatalogMapper catalogMapper, ObjectMapper objectMapper) {
        this.catalogMapper = catalogMapper;
        this.objectMapper = objectMapper;
    }

    public int transformAll() {
        return transformByPropertyIds(catalogMapper.selectAllPropertyIds());
    }

    public int transformByPropertyIds(List<String> propertyIds) {
        int transformed = 0;
        for (String propertyId : propertyIds) {
            if (StringUtils.isBlank(propertyId)) {
                continue;
            }
            try {
                transformOne(propertyId.trim());
                transformed++;
            } catch (Exception e) {
                log.error("ExpediaCatalogTransform 失败 propertyId={}", propertyId, e);
            }
        }
        return transformed;
    }

    @Transactional
    public void transformOne(String propertyId) throws Exception {
        Map<String, ExpediaPropertyDocument> byLang = loadSnapshots(propertyId);
        ExpediaPropertyDocument en = byLang.get(LANG_EN);
        ExpediaPropertyDocument zh = byLang.get(LANG_ZH);
        if (en == null) {
            throw new IllegalStateException("缺少 en-US 快照，无法作为目录基准: " + propertyId);
        }

        // 档案域（原 hotel-info 双推的那一半）
        upsertSupplierHotel(en, zh);
        upsertSupplierRooms(en, zh);
        // 目录域（原 hotel-base 双推的那一半）
        upsertHotelDetails(en, zh);
        upsertRoomBases(en, zh);
        replacePictures(en);
        upsertExtend(en, LANG_EN);
        if (zh != null) {
            upsertExtend(zh, LANG_ZH);
        }
    }

    private Map<String, ExpediaPropertyDocument> loadSnapshots(String propertyId) throws Exception {
        Map<String, ExpediaPropertyDocument> byLang = new HashMap<>();
        for (HashMap<String, Object> row : catalogMapper.selectSnapshots(propertyId)) {
            String language = String.valueOf(row.get("language"));
            String normalizedJson = String.valueOf(row.get("normalizedJson"));
            byLang.put(language, objectMapper.readValue(normalizedJson, ExpediaPropertyDocument.class));
        }
        return byLang;
    }

    // ---------- 档案域 ----------

    private void upsertSupplierHotel(ExpediaPropertyDocument en, ExpediaPropertyDocument zh) throws Exception {
        HashMap<String, Object> p = new HashMap<>();
        p.put("supplierId", SUPPLIER_ID);
        p.put("supplierHotelId", en.supplierPropertyId());
        // Expedia 打底：统一酒店ID = supplier_hotel_id，映射天然 1:1（merger=1 由 SQL 固定）
        p.put("hotelId", en.supplierPropertyId());
        p.put("supplierHotelName", en.name());
        p.put("supplierHotelNameCn", zh == null ? null : zh.name());
        p.put("telephone", en.phone());
        p.put("postcode", en.address() == null ? null : en.address().postalCode());
        p.put("address", en.address() == null ? null : en.address().line1());
        p.put("addressCn", zh == null || zh.address() == null ? null : zh.address().line1());
        p.put("countryCode", en.address() == null ? null : en.address().countryCode());
        p.put("countryName", null);
        p.put("countryNameCn", null);
        p.put("cityName", en.address() == null ? null : en.address().city());
        p.put("cityNameCn", zh == null || zh.address() == null ? null : zh.address().city());
        p.put("stateName", en.address() == null ? null : en.address().stateProvince());
        p.put("stateNameCn", zh == null || zh.address() == null ? null : zh.address().stateProvince());
        p.put("longitude", en.coordinates() == null || en.coordinates().longitude() == null ? null : en.coordinates().longitude().toPlainString());
        p.put("latitude", en.coordinates() == null || en.coordinates().latitude() == null ? null : en.coordinates().latitude().toPlainString());
        p.put("hotelType", en.category() == null ? null : en.category().name());
        p.put("brandId", en.brand() == null ? null : en.brand().id());
        p.put("brandName", en.brand() == null ? null : en.brand().name());
        p.put("groupId", en.chain() == null ? null : en.chain().id());
        p.put("groupName", en.chain() == null ? null : en.chain().name());
        p.put("score", en.rating() == null || en.rating().guest() == null ? null : en.rating().guest().toPlainString());
        p.put("bookAble", en.active() ? 1 : 0);
        p.put("status", en.active() ? 1 : 0);
        p.put("descriptions", en.descriptions() == null ? null : objectMapper.writeValueAsString(en.descriptions()));
        catalogMapper.upsertSupplierHotelBase(p);
    }

    private void upsertSupplierRooms(ExpediaPropertyDocument en, ExpediaPropertyDocument zh) throws Exception {
        if (en.rooms() == null) {
            return;
        }
        Map<String, ExpediaPropertyDocument.Room> zhRooms = roomsById(zh);
        for (ExpediaPropertyDocument.Room roomEn : en.rooms()) {
            ExpediaPropertyDocument.Room roomZh = zhRooms.get(roomEn.id());
            HashMap<String, Object> p = new HashMap<>();
            p.put("supplierId", SUPPLIER_ID);
            p.put("supplierRoomId", roomEn.id());
            p.put("supplierHotelId", en.supplierPropertyId());
            p.put("roomId", roomEn.id());
            p.put("supplierRoomName", roomEn.name());
            p.put("supplierRoomNameCn", roomZh == null ? null : roomZh.name());
            p.put("description", roomEn.description());
            p.put("area", area(roomEn));
            p.put("bedInfoList", bedInfoListJson(roomEn));
            p.put("capacity", roomEn.occupancy() == null ? null : roomEn.occupancy().total());
            p.put("isSmoking", null);
            catalogMapper.upsertSupplierRoomBase(p);
        }
    }

    // ---------- 目录域 ----------

    private void upsertHotelDetails(ExpediaPropertyDocument en, ExpediaPropertyDocument zh) {
        HashMap<String, Object> p = new HashMap<>();
        p.put("hotelId", en.supplierPropertyId());
        p.put("hotelName", en.name());
        p.put("hotelNameCn", zh == null ? null : zh.name());
        p.put("telephone", en.phone());
        p.put("address", en.address() == null ? null : en.address().line1());
        p.put("addressCn", zh == null || zh.address() == null ? null : zh.address().line1());
        p.put("postCode", en.address() == null ? null : en.address().postalCode());
        p.put("cityName", en.address() == null ? null : en.address().city());
        p.put("cityNameCn", zh == null || zh.address() == null ? null : zh.address().city());
        p.put("stateName", en.address() == null ? null : en.address().stateProvince());
        p.put("countryCode", en.address() == null ? null : en.address().countryCode());
        // 还原旧语义：star/score 是字符串列，3.5 星原样保存，不再取整
        p.put("star", en.rating() == null || en.rating().property() == null ? "0" : en.rating().property().toPlainString());
        p.put("score", en.rating() == null || en.rating().guest() == null ? "0" : en.rating().guest().toPlainString());
        p.put("longitude", en.coordinates() == null || en.coordinates().longitude() == null ? "0" : en.coordinates().longitude().toPlainString());
        p.put("latitude", en.coordinates() == null || en.coordinates().latitude() == null ? "0" : en.coordinates().latitude().toPlainString());
        p.put("hotelGroup", en.chain() == null ? "" : StringUtils.defaultString(en.chain().name()));
        p.put("brand", en.brand() == null ? "" : StringUtils.defaultString(en.brand().name()));
        p.put("status", en.active() ? 1 : 0);
        catalogMapper.upsertHotelDetails(p);
    }

    private void upsertRoomBases(ExpediaPropertyDocument en, ExpediaPropertyDocument zh) throws Exception {
        if (en.rooms() == null) {
            return;
        }
        Map<String, ExpediaPropertyDocument.Room> zhRooms = roomsById(zh);
        for (ExpediaPropertyDocument.Room roomEn : en.rooms()) {
            ExpediaPropertyDocument.Room roomZh = zhRooms.get(roomEn.id());
            HashMap<String, Object> p = new HashMap<>();
            p.put("roomId", roomEn.id());
            p.put("hotelId", en.supplierPropertyId());
            p.put("roomName", roomEn.name());
            p.put("roomNameCn", roomZh == null ? null : roomZh.name());
            p.put("area", area(roomEn));
            // 旧 convertBedInfo 语义：床名中英各自拼接、连接词 " or " / "或"
            p.put("bedName", bedName(roomEn, "or"));
            p.put("bedNameCn", roomZh == null ? null : bedName(roomZh, "或"));
            p.put("bedType", bedTypeSet(roomEn));
            p.put("bedDesc", bedInfoListJson(roomEn));
            p.put("bedNumber", String.valueOf(totalBeds(roomEn)));
            p.put("capacity", roomEn.occupancy() == null ? null : roomEn.occupancy().total());
            catalogMapper.upsertRoomBase(p);
        }
    }

    /**
     * 图片（移植旧 Adaptor）：hero 图 sort=0 置顶其余 1；酒店图与房型图共表，room_id 区分；重推先删后插
     */
    private void replacePictures(ExpediaPropertyDocument en) {
        String hotelId = en.supplierPropertyId();
        catalogMapper.deleteHotelPictures(hotelId);
        insertPictures(hotelId, null, "hotel", en.propertyImages());
        if (en.rooms() != null) {
            for (ExpediaPropertyDocument.Room room : en.rooms()) {
                insertPictures(hotelId, room.id(), "room", room.images());
            }
        }
    }

    private void insertPictures(String hotelId, String roomId, String type,
                                List<ExpediaPropertyDocument.Image> images) {
        if (images == null) {
            return;
        }
        images.stream()
                .filter(img -> StringUtils.isNotBlank(img.url()))
                .forEach(img -> {
                    HashMap<String, Object> p = new HashMap<>();
                    p.put("hotelId", hotelId);
                    p.put("roomId", roomId);
                    p.put("type", type);
                    p.put("name", StringUtils.abbreviate(StringUtils.defaultString(img.caption()), 255));
                    p.put("sort", img.hero() ? 0 : 1);
                    p.put("url", img.url());
                    catalogMapper.insertHotelPicture(p);
                });
    }

    private void upsertExtend(ExpediaPropertyDocument doc, String language) throws Exception {
        ExpediaPropertyDocument.StayInformation stay = doc.stayInformation();
        HashMap<String, Object> p = new HashMap<>();
        p.put("hotelId", doc.supplierPropertyId());
        p.put("language", language);
        p.put("checkIn", stay == null ? null : joinNonBlank(" - ", stay.checkInBegin(), stay.checkInEnd()));
        p.put("checkOut", stay == null ? null : stay.checkOutTime());
        p.put("instructions", stay == null ? null : joinNonBlank("\n", stay.checkInInstructions(), stay.checkInSpecialInstructions()));
        p.put("fees", stay == null ? null : joinNonBlank("\n", stay.mandatoryFees(), stay.optionalFees()));
        p.put("policies", stay == null ? null : stay.knowBeforeYouGo());
        p.put("descriptions", doc.descriptions() == null ? null : objectMapper.writeValueAsString(doc.descriptions()));
        catalogMapper.upsertHotelExtend(p);
    }

    // ---------- 加工函数（语义移植自旧 ExpediaStaticInfoAdaptor.convertBedInfo 等） ----------

    private Map<String, ExpediaPropertyDocument.Room> roomsById(ExpediaPropertyDocument doc) {
        Map<String, ExpediaPropertyDocument.Room> map = new HashMap<>();
        if (doc != null && doc.rooms() != null) {
            doc.rooms().forEach(r -> map.put(r.id(), r));
        }
        return map;
    }

    private String bedName(ExpediaPropertyDocument.Room room, String joiner) {
        if (room.bedGroups() == null || room.bedGroups().isEmpty()) {
            return "";
        }
        List<String> names = room.bedGroups().stream()
                .map(ExpediaPropertyDocument.BedGroup::description)
                .filter(StringUtils::isNotBlank)
                .toList();
        return String.join(" " + joiner + " ", names).trim();
    }

    /** 旧语义：收集所有床组里出现过的床型（bedTypeSet.toString()） */
    private String bedTypeSet(ExpediaPropertyDocument.Room room) {
        Set<String> types = new HashSet<>();
        if (room.bedGroups() != null) {
            room.bedGroups().forEach(bg -> {
                if (bg.beds() != null) {
                    bg.beds().forEach(bed -> {
                        if (StringUtils.isNotBlank(bed.type())) {
                            types.add(bed.type());
                        }
                    });
                }
            });
        }
        return types.isEmpty() ? "" : types.toString();
    }

    /** 旧结构：List<List<BedInfoDTO>>，外层=床组（可选方案），内层=该方案的床；字段名沿用旧 DTO */
    private String bedInfoListJson(ExpediaPropertyDocument.Room room) throws Exception {
        List<List<Map<String, Object>>> groups = new ArrayList<>();
        if (room.bedGroups() != null) {
            for (ExpediaPropertyDocument.BedGroup bg : room.bedGroups()) {
                List<Map<String, Object>> beds = new ArrayList<>();
                if (bg.beds() != null) {
                    for (ExpediaPropertyDocument.Bed bed : bg.beds()) {
                        Map<String, Object> one = new LinkedHashMap<>();
                        one.put("bedNumber", bed.quantity());
                        one.put("bedDesc", bed.type());
                        one.put("bedType", bed.size());
                        beds.add(one);
                    }
                }
                groups.add(beds);
            }
        }
        return objectMapper.writeValueAsString(groups);
    }

    private int totalBeds(ExpediaPropertyDocument.Room room) {
        if (room.bedGroups() == null || room.bedGroups().isEmpty()) {
            return 0;
        }
        ExpediaPropertyDocument.BedGroup first = room.bedGroups().get(0);
        if (first.beds() == null) {
            return 0;
        }
        return first.beds().stream()
                .map(ExpediaPropertyDocument.Bed::quantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String area(ExpediaPropertyDocument.Room room) {
        if (room.squareMeters() != null) {
            return room.squareMeters() + "㎡";
        }
        if (room.squareFeet() != null) {
            return room.squareFeet() + "sqft";
        }
        return null;
    }

    private String joinNonBlank(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                kept.add(part.trim());
            }
        }
        return kept.isEmpty() ? null : String.join(sep, kept);
    }
}
