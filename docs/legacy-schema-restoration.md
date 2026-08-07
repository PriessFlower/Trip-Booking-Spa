# 原系统数据模型还原图纸

> **定位**：spa 专属目录层的建表依据。还原对象 = 旧中台（hotel-base-intl / hotel-info-intl）存储 spa 推送数据所用的表。
> **状态**：已落地。DDL 见 `config/mysql/spa-catalog-schema.sql`，建在 **spa 专属库 `tg_trip_spa`**（与 trip-cursor 的库物理隔离，本地已迁移 spa 全部 16 张表）。

## 一、证据源

| 证据 | 位置 | 内容 | 可信度 |
|---|---|---|---|
| `com.bingo.hotel.base.intl:cli:0.0.18` | 本地 ~/.m2（反编译） | 92 类 722 字段：统一目录层全部 DTO + 接口签名 | 字段级原始证据 |
| `com.bingo.hotel.info.intl:cli:0.1.3` | 本地 ~/.m2（反编译） | 42 类 293 字段：供应商档案层全部 DTO | 同上 |
| 旧 adaptor（`ExpediaStaticInfoAdaptor`，302 行） | 本仓库 | 每个字段怎么填的语义 | 加工逻辑原始证据 |
| 国内版 `base:cli:0.5.0`（`HotelBaseDTO` 等） | 本地 ~/.m2 | 表实体镜像风格（含 id/del/operator 家务列） | 风格参照 |

**置信度标注**：A=实体镜像/字段级证据直接还原；B=从接口语义与 adaptor 推断（表名/唯一键）；C=经验补全（列宽/索引，原证据不含）。

## 二、复原出的原系统架构

```
旧 spa ──transformInfoHotelReq──▶ hotel-info（供应商档案层）
        saveHotelInfo/saveRoomInfo/saveProductInfo
        存：supplier_hotel_base / supplier_room_base / supplier_product_base
        （原始供应商数据，带 merger 合并状态）

旧 spa ──transformBaseHotelReq──▶ hotel-base（统一目录层）
        saveHotelDetails/saveRoomBase/saveCountryList/saveCityList
        存：hotel_details / room_base / hotel_picture / hotel_extend
            / country_info / city_info / global_product_supplier
        （加工后的双语目录，直接支撑业务展示）
```

两层各司其职：**info 层存"供应商怎么说"，base 层存"我们对外怎么讲"**。还原时保留这个分层——
对应到新链路：`expedia_property_content`（raw，已有）之上，加工产物落 base 域表；info 域表可选（见第五节取舍）。

## 三、建表清单 —— 统一目录域（hotel-base，7 张）

### `hotel_details` ← `HotelDetailsRequest`（29 字段）

酒店双语主档。**键**：UNIQUE(hotel_id)｜置信度：字段A/键B（adaptor 以 Expedia property_id 作 hotelId 推送，saveHotelDetails 是幂等 upsert）

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `id` | bigint | `id` (Long) |  |
| `hotel_id` | varchar(64) | `hotelId` (String) |  |
| `hotel_name` | varchar(255) | `hotelName` (String) |  |
| `hotel_name_cn` | varchar(255) | `hotelNameCN` (String) |  |
| `telephone` | varchar(64) | `telephone` (String) |  |
| `address` | varchar(255) | `address` (String) |  |
| `address_cn` | varchar(255) | `addressCN` (String) |  |
| `post_code` | varchar(64) | `postCode` (String) |  |
| `city_id` | varchar(64) | `cityId` (String) |  |
| `city_name` | varchar(255) | `cityName` (String) |  |
| `city_name_cn` | varchar(255) | `cityNameCN` (String) |  |
| `state_name` | varchar(255) | `stateName` (String) |  |
| `country_id` | varchar(64) | `countryId` (String) |  |
| `country_code` | varchar(64) | `countryCode` (String) |  |
| `fax` | varchar(64) | `fax` (String) |  |
| `star` | varchar(64) | `star` (String) |  |
| `score` | varchar(64) | `score` (String) |  |
| `longitude` | varchar(64) | `longitude` (String) |  |
| `latitude` | varchar(64) | `latitude` (String) |  |
| `hotel_group` | varchar(255) | `group` (String) | ⚠️ group 是 MySQL 保留字，列名改 hotel_group（唯一一处偏离原名） |
| `brand` | varchar(255) | `brand` (String) |  |
| `status` | tinyint(1) | `status` (Boolean) |  |
| `create_time` | datetime | `createTime` (Date) |  |
| `update_time` | datetime | `updateTime` (Date) |  |
| `del` | tinyint(1) | `del` (Boolean) |  |
| `operator` | varchar(64) | `operator` (String) |  |
| `global_hotel_picture_dtos` | —（拆子表/JSON，见说明） | `globalHotelPictureDTOS` (List<GlobalHotelPictureDTO>) | 拆到 hotel_picture 表 |
| `global_hotel_base_extend_dtos` | —（拆子表/JSON，见说明） | `globalHotelBaseExtendDTOS` (List<GlobalHotelBaseExtendDTO>) | 拆到 hotel_extend 表 |
| `room_base_list` | —（拆子表/JSON，见说明） | `roomBaseList` (List<RoomBaseRequest>) | 拆到 room_base 表 |

### `room_base` ← `RoomBaseRequest`（24 字段）

房型双语档案（床型深加工落点）。**键**：UNIQUE(room_id)、KEY(hotel_id)｜字段A/键B

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `id` | bigint | `id` (Long) |  |
| `room_id` | varchar(64) | `roomId` (String) |  |
| `hotel_id` | varchar(64) | `hotelId` (String) |  |
| `room_name` | varchar(255) | `roomName` (String) |  |
| `room_name_cn` | varchar(255) | `roomNameCN` (String) |  |
| `area` | varchar(64) | `area` (String) |  |
| `floor` | varchar(64) | `floor` (String) |  |
| `broadnet` | int | `broadnet` (Integer) |  |
| `bed_name` | varchar(255) | `bedName` (String) |  |
| `bed_name_cn` | varchar(255) | `bedNameCN` (String) |  |
| `bed_type` | varchar(64) | `bedType` (String) |  |
| `bed_desc` | text（JSON） | `bedDesc` (String) | List<List<BedInfoDTO>> 序列化，旧 adaptor convertBedInfo 的产物 |
| `bed_type_status` | int | `bedTypeStatus` (Integer) |  |
| `bed_number` | varchar(64) | `bedNumber` (String) |  |
| `capacity` | int | `capacity` (Integer) |  |
| `has_bathroom` | int | `hasBathroom` (Integer) |  |
| `has_windows` | int | `hasWindows` (Integer) |  |
| `is_smoking` | int | `isSmoking` (Integer) |  |
| `update_time` | datetime | `updateTime` (Date) |  |
| `status` | tinyint(1) | `status` (Boolean) |  |
| `operator` | varchar(64) | `operator` (String) |  |
| `del` | tinyint(1) | `del` (Boolean) |  |
| `global_room_picture_dtos` | —（拆子表/JSON，见说明） | `globalRoomPictureDTOS` (List<GlobalHotelPictureDTO>) | 拆到 hotel_picture（room_id 非空行） |
| `global_room_base_extend_dtos` | —（拆子表/JSON，见说明） | `globalRoomBaseExtendDTOS` (List<GlobalHotelBaseExtendDTO>) | 拆到 hotel_extend |

### `hotel_picture` ← `GlobalHotelPictureDTO`（7 字段）

酒店/房型图片（共用，room_id 区分）。**键**：KEY(hotel_id)、KEY(room_id)｜字段A/键C（无业务唯一键证据；旧语义疑似重推先删后插）

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `hotel_id` | varchar(64) | `hotelId` (String) |  |
| `room_id` | varchar(64) | `roomId` (String) |  |
| `type` | varchar(64) | `type` (String) | "hotel"/房型 |
| `name` | varchar(255) | `name` (String) |  |
| `name_cn` | varchar(255) | `nameCN` (String) |  |
| `sort` | int | `sort` (Integer) | hero 图=0 置顶（adaptor 语义） |
| `url` | varchar(768) | `url` (String) |  |

### `hotel_extend` ← `GlobalHotelBaseExtendDTO`（11 字段）

入住政策/费用/描述扩展（自带 language 列=原系统的多语言扩展表）。**键**：UNIQUE(hotel_id, room_id, language)｜字段A/键B

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `hotel_id` | varchar(64) | `hotelId` (String) |  |
| `room_id` | varchar(64) | `roomId` (String) |  |
| `language` | varchar(64) | `language` (String) |  |
| `check_in` | varchar(64) | `checkIn` (String) |  |
| `check_out` | varchar(64) | `checkOut` (String) |  |
| `instructions` | text | `instructions` (String) |  |
| `min_age` | varchar(64) | `minAge` (String) |  |
| `fees` | text | `fees` (String) |  |
| `policies` | text | `policies` (String) |  |
| `descriptions` | text | `descriptions` (String) |  |
| `del` | tinyint(1) | `del` (Boolean) |  |

### `country_info` ← `CountryInfoRequest`（10 字段）

国家档案（洲际+双语+坐标）。**键**：UNIQUE(country_id)｜字段A/键B（saveCountryList 批量 upsert）

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `country_id` | varchar(64) | `countryId` (String) |  |
| `country_code` | varchar(64) | `countryCode` (String) |  |
| `phone_code` | varchar(64) | `phoneCode` (String) |  |
| `country_name` | varchar(255) | `countryName` (String) |  |
| `country_name_cn` | varchar(255) | `countryNameCN` (String) |  |
| `continent` | varchar(64) | `continent` (String) |  |
| `continent_cn` | varchar(64) | `continentCN` (String) |  |
| `longitude` | decimal(13,10) | `longitude` (BigDecimal) |  |
| `latitude` | decimal(13,10) | `latitude` (BigDecimal) |  |
| `note` | varchar(64) | `note` (String) | 来源标记，旧代码存 supplier desc（"expedia"） |

### `city_info` ← `CityInfoResponse`（10 字段）

城市/州省档案（递归层级：state_id 指向上级）。**键**：UNIQUE(city_id)、KEY(country_id)、KEY(state_id)｜字段A（Response 含 id，实体镜像）/键B

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `id` | int | `id` (Integer) | 原表自增主键（Response 镜像证据） |
| `country_id` | varchar(64) | `countryId` (String) |  |
| `city_id` | varchar(64) | `cityId` (String) |  |
| `city_name` | varchar(255) | `cityName` (String) |  |
| `city_name_cn` | varchar(255) | `cityNameCN` (String) |  |
| `state_name` | varchar(255) | `stateName` (String) |  |
| `state_name_cn` | varchar(255) | `stateNameCN` (String) |  |
| `longitude` | decimal(13,10) | `longitude` (BigDecimal) |  |
| `latitude` | decimal(13,10) | `latitude` (BigDecimal) |  |
| `note` | varchar(64) | `note` (String) | 旧代码存 region type（city/province_state 等） |

### `global_product_supplier` ← `GlobalProductSupplierRequest`（16 字段）

产品-供应商映射（查价响应建档）。**键**：UNIQUE(supplier_id, supplier_product_id)、KEY(hotel_id)｜字段A/键B

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `id` | bigint | `id` (Long) |  |
| `product_id` | varchar(64) | `productId` (String) |  |
| `room_id` | varchar(64) | `roomId` (String) |  |
| `hotel_id` | varchar(64) | `hotelId` (String) |  |
| `supplier_id` | int | `supplierId` (Integer) |  |
| `supplier_hotel_id` | varchar(64) | `supplierHotelId` (String) |  |
| `supplier_room_id` | varchar(64) | `supplierRoomId` (String) |  |
| `supplier_product_id` | varchar(64) | `supplierProductId` (String) |  |
| `supplier_product_name` | varchar(255) | `supplierProductName` (String) |  |
| `supplier_product_name_cn` | varchar(255) | `supplierProductNameCN` (String) |  |
| `supplier_bed_desc` | text（JSON） | `supplierBedDesc` (String) |  |
| `has_window` | int | `hasWindow` (Integer) |  |
| `breakfast` | int | `breakfast` (Integer) |  |
| `cancel_type` | int | `cancelType` (Integer) |  |
| `operator` | varchar(64) | `operator` (String) |  |
| `del` | tinyint(1) | `del` (Boolean) |  |

## 四、建表清单 —— 供应商档案域（hotel-info，3 张）

### `supplier_hotel_base` ← `info.SupplierHotelBaseRequest`（37 字段）

供应商酒店原始档案。**键**：UNIQUE(supplier_id, supplier_hotel_id)｜字段A/键B

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `id` | bigint | `id` (Long) |  |
| `supplier_id` | int | `supplierId` (Integer) |  |
| `supplier_hotel_id` | varchar(64) | `supplierHotelId` (String) |  |
| `supplier_hotel_name` | varchar(255) | `supplierHotelName` (String) |  |
| `supplier_hotel_name_cn` | varchar(255) | `supplierHotelNameCN` (String) |  |
| `telephone` | varchar(64) | `telephone` (String) |  |
| `postcode` | varchar(64) | `postcode` (String) |  |
| `currency` | varchar(64) | `currency` (String) |  |
| `address` | varchar(255) | `address` (String) |  |
| `address_cn` | varchar(255) | `addressCN` (String) |  |
| `country_code` | varchar(64) | `countryCode` (String) |  |
| `country_name` | varchar(255) | `countryName` (String) |  |
| `country_id` | varchar(64) | `countryId` (String) |  |
| `city_id` | varchar(64) | `cityId` (String) |  |
| `city_name` | varchar(255) | `cityName` (String) |  |
| `city_name_cn` | varchar(255) | `cityNameCN` (String) |  |
| `state_name` | varchar(255) | `stateName` (String) |  |
| `state_name_cn` | varchar(255) | `stateNameCN` (String) |  |
| `fax` | varchar(64) | `fax` (String) |  |
| `longitude` | varchar(64) | `longitude` (String) |  |
| `latitude` | varchar(64) | `latitude` (String) |  |
| `hotel_type` | varchar(64) | `hotelType` (String) |  |
| `rooms` | int | `rooms` (Integer) |  |
| `brand_id` | varchar(255) | `brandId` (String) |  |
| `brand_name` | varchar(255) | `brandName` (String) |  |
| `group_id` | varchar(255) | `groupId` (String) |  |
| `group_name` | varchar(255) | `groupName` (String) |  |
| `recommend_level` | int | `recommendLevel` (Integer) |  |
| `score` | varchar(64) | `score` (String) |  |
| `book_able` | tinyint(1) | `bookAble` (Boolean) |  |
| `status` | tinyint(1) | `status` (Boolean) |  |
| `breakfast` | int | `breakfast` (Integer) |  |
| `descriptions` | text | `descriptions` (String) |  |
| `introduce_info` | text | `introduceInfo` (String) |  |
| `del` | tinyint(1) | `del` (Boolean) |  |
| `operator` | varchar(64) | `operator` (String) |  |
| `room_list` | —（拆子表/JSON，见说明） | `roomList` (List<SupplierRoomBaseRequest>) | 拆到 supplier_room_base |

### `supplier_room_base` ← `info.SupplierRoomBaseRequest`（21 字段）

供应商房型原始档案。**键**：UNIQUE(supplier_id, supplier_room_id)｜字段A/键B

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `id` | bigint | `id` (Long) |  |
| `supplier_id` | int | `supplierId` (Integer) |  |
| `supplier_room_id` | varchar(64) | `supplierRoomId` (String) |  |
| `supplier_hotel_id` | varchar(64) | `supplierHotelId` (String) |  |
| `supplier_room_name` | varchar(255) | `supplierRoomName` (String) |  |
| `supplier_room_name_cn` | varchar(255) | `supplierRoomNameCN` (String) |  |
| `description` | text | `description` (String) |  |
| `area` | varchar(64) | `area` (String) |  |
| `floor` | varchar(64) | `floor` (String) |  |
| `broad_net` | int | `broadNet` (Integer) |  |
| `bed_info_list` | —（拆子表/JSON，见说明） | `bedInfoList` (List<List<BedInfoDTO>>) | JSON：List<List<BedInfoDTO>> |
| `capacity` | int | `capacity` (Integer) |  |
| `has_bathroom` | int | `hasBathroom` (Integer) |  |
| `has_windows` | int | `hasWindows` (Integer) |  |
| `is_smoking` | int | `isSmoking` (Integer) |  |
| `is_add_bed` | int | `isAddBed` (Integer) |  |
| `service` | text | `service` (String) |  |
| `remarks` | text | `remarks` (String) |  |
| `merger` | int | `merger` (Integer) | 聚合状态（0=未合并） |
| `status` | tinyint(1) | `status` (Boolean) |  |
| `operator` | varchar(64) | `operator` (String) |  |

### `supplier_product_base` ← `info.SupplierProductBaseRequest`（14 字段）

供应商产品原始档案。**键**：UNIQUE(supplier_id, supplier_product_id)｜字段A/键B

| 列 | 类型 | 来源字段 | 说明 |
|---|---|---|---|
| `product_id` | varchar(64) | `productId` (String) |  |
| `room_id` | varchar(64) | `roomId` (String) |  |
| `supplier_id` | int | `supplierId` (Integer) |  |
| `supplier_hotel_id` | varchar(64) | `supplierHotelId` (String) |  |
| `supplier_room_id` | varchar(64) | `supplierRoomId` (String) |  |
| `supplier_product_name` | varchar(255) | `supplierProductName` (String) |  |
| `supplier_product_name_cn` | varchar(255) | `supplierProductNameCN` (String) |  |
| `supplier_product_id` | varchar(64) | `supplierProductId` (String) |  |
| `supplier_bed_desc` | text（JSON） | `supplierBedDesc` (String) |  |
| `has_window` | int | `hasWindow` (Integer) |  |
| `breakfast` | int | `breakfast` (Integer) |  |
| `cancel_type` | int | `cancelType` (Integer) |  |
| `operator` | varchar(64) | `operator` (String) |  |
| `del` | tinyint(1) | `del` (Boolean) |  |

## 五、取舍与边界（需拍板的点）

1. **【已定】base 域副本表不建，映射列搬家**：base 域 `GlobalHotelSupplierRequest`/`GlobalRoomSupplierRequest` 与 info 域 supplier_* 有 35 个字段完全相同——历史上因两个中台跨库不能 JOIN 而存的副本，其真实身份是"映射工作台"（独有 `hotelId`=归属统一酒店、`merger`=合并状态）。单库后副本冗余取消，**映射列 `hotel_id`/`merger`/`country_name_cn` 迁至 `supplier_hotel_base`（房型同理 `room_id`）**，能力零丢失。`hotel_mapping_fail`（映射失败记录）等接入第二家供应商做映射时再建。
2. **info 域三张表建不建**：如果 spa 只做"Expedia 打底目录"，raw 已有 `expedia_property_content`，info 域（供应商原始档案的关系化形态）可暂缓；如果希望完整还原两层架构（未来多供应商时按旧模式扩），则一起建。**默认：一起建**（还原优先）。
3. **家务列**：原实体自带 `status/del/operator/create_time/update_time`——全部保留（这正是原系统风格）；仅统一为 MySQL 惯例 `create_dt/update_dt` 命名？**默认：保留原名 create_time/update_time**（还原优先）。
4. 还原不了的（明示）：中台合并/映射治理的**算法**（ArtificialMerger*、matching 打分）、列宽/索引/默认值物理细节、中台内部运营表。

## 六、与现有链路的衔接

```
第0层 expedia_property_content（raw+双语快照）      ←已有，不动
第1层 本图纸的还原表（spa 专属目录）                  ←图纸确认后建
加工  ExpediaCatalogTransformService                ←目标表从 trip-cursor hotel_base 切换到还原表
geo   阶段3的 Geography 服务                         ←直接落 country_info / city_info
```

> 注：当前工作区里 transform 写的是 trip-cursor `hotel_base`（方案A初版）。图纸落地后改写目标为 `hotel_details`/`room_base` 等还原表，与"这套专属于 spa"的定位一致。trip-cursor 侧将来经它自己的转换器从 spa 目录取数。

## 七、建议实施顺序

1. 本图纸 review，圈定表范围与第五节取舍 → 2. 生成 `config/mysql/spa-catalog-schema.sql` 并本地建表 → 3. Geography 服务（阶段3）落 country_info/city_info → 4. transform 目标切换到还原表 → 5. 产品映射（查价响应→global_product_supplier）→ 6. 旧链路+placeholder 退役
