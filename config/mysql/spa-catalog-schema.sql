-- =====================================================================
-- spa 专属目录层（原系统还原表）
-- 依据：docs/legacy-schema-restoration.md（旧中台 intl jar 反编译还原）
-- 库：tg_trip_spa（与 trip-cursor 的库物理隔离）
-- 域划分：统一目录域（hotel-base 还原，7 张）+ 供应商档案域（hotel-info 还原，3 张）
-- =====================================================================

-- ---------- 统一目录域 ----------

-- 酒店双语主档 ← base.HotelDetailsRequest
CREATE TABLE IF NOT EXISTS hotel_details (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hotel_id VARCHAR(64) NOT NULL COMMENT '统一酒店ID（Expedia打底=property_id）',
    hotel_name VARCHAR(255) NULL,
    hotel_name_cn VARCHAR(255) NULL,
    telephone VARCHAR(64) NULL,
    address VARCHAR(255) NULL,
    address_cn VARCHAR(255) NULL,
    post_code VARCHAR(64) NULL,
    city_id VARCHAR(64) NULL,
    city_name VARCHAR(255) NULL,
    city_name_cn VARCHAR(255) NULL,
    state_name VARCHAR(255) NULL,
    country_id VARCHAR(64) NULL,
    country_code VARCHAR(64) NULL,
    fax VARCHAR(64) NULL,
    star VARCHAR(64) NULL,
    score VARCHAR(64) NULL,
    longitude VARCHAR(64) NULL,
    latitude VARCHAR(64) NULL,
    hotel_group VARCHAR(255) NULL COMMENT '原字段 group（MySQL 保留字改名）',
    brand VARCHAR(255) NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    del TINYINT(1) NOT NULL DEFAULT 0,
    operator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_hotel_details_hotel (hotel_id),
    KEY idx_hotel_details_city (city_id),
    KEY idx_hotel_details_country (country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='酒店双语主档（原 hotel-base.saveHotelDetails）';

-- 房型双语档案 ← base.RoomBaseRequest
CREATE TABLE IF NOT EXISTS room_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id VARCHAR(64) NOT NULL COMMENT '统一房型ID',
    hotel_id VARCHAR(64) NOT NULL,
    room_name VARCHAR(255) NULL,
    room_name_cn VARCHAR(255) NULL,
    area VARCHAR(64) NULL,
    floor VARCHAR(64) NULL,
    broadnet INT NULL,
    bed_name VARCHAR(255) NULL COMMENT '床名英文（"1 King Bed or 2 Queen Beds"）',
    bed_name_cn VARCHAR(255) NULL COMMENT '床名中文（"1张特大床或2张大床"）',
    bed_type VARCHAR(255) NULL,
    bed_desc TEXT NULL COMMENT 'JSON：List<List<BedInfoDTO>>（旧 adaptor convertBedInfo 产物）',
    bed_type_status INT NULL,
    bed_number VARCHAR(64) NULL,
    capacity INT NULL,
    has_bathroom INT NULL,
    has_windows INT NULL,
    is_smoking INT NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    del TINYINT(1) NOT NULL DEFAULT 0,
    operator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_room_base_room (room_id),
    KEY idx_room_base_hotel (hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='房型双语档案（原 hotel-base.saveRoomBase）';

-- 酒店/房型图片（共用，room_id 空=酒店图） ← base.GlobalHotelPictureDTO
CREATE TABLE IF NOT EXISTS hotel_picture (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hotel_id VARCHAR(64) NOT NULL,
    room_id VARCHAR(64) NULL,
    type VARCHAR(64) NULL COMMENT 'hotel/room',
    name VARCHAR(255) NULL,
    name_cn VARCHAR(255) NULL,
    sort INT NULL COMMENT 'hero 图=0 置顶',
    url VARCHAR(768) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_hotel_picture_hotel (hotel_id),
    KEY idx_hotel_picture_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='酒店/房型图片（原 GlobalHotelPictureDTO）';

-- 政策/费用/描述扩展（language 维度） ← base.GlobalHotelBaseExtendDTO
CREATE TABLE IF NOT EXISTS hotel_extend (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hotel_id VARCHAR(64) NOT NULL,
    room_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '空串=酒店级',
    language VARCHAR(20) NOT NULL DEFAULT 'en-US',
    check_in VARCHAR(255) NULL,
    check_out VARCHAR(255) NULL,
    instructions TEXT NULL,
    min_age VARCHAR(64) NULL,
    fees TEXT NULL,
    policies TEXT NULL,
    descriptions TEXT NULL,
    del TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_hotel_extend (hotel_id, room_id, language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入住政策/费用/描述扩展（原系统自带 language 列的多语言表）';

-- 国家档案 ← base.CountryInfoRequest（洲际+双语+坐标+区号）
CREATE TABLE IF NOT EXISTS country_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    country_id VARCHAR(64) NOT NULL COMMENT 'Expedia region id',
    country_code VARCHAR(64) NULL,
    phone_code VARCHAR(64) NULL,
    country_name VARCHAR(255) NULL,
    country_name_cn VARCHAR(255) NULL,
    continent VARCHAR(64) NULL,
    continent_cn VARCHAR(64) NULL,
    longitude DECIMAL(13,10) NULL,
    latitude DECIMAL(13,10) NULL,
    note VARCHAR(64) NULL COMMENT '来源标记（旧代码存 supplier desc）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_country_info (country_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国家档案（原 hotel-base.saveCountryList）';

-- 城市/州省档案（state_id 指向上级，递归成树） ← base.CityInfoRequest/Response 并集
CREATE TABLE IF NOT EXISTS city_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    city_id VARCHAR(64) NOT NULL COMMENT 'Expedia region id',
    city_name VARCHAR(255) NULL,
    city_name_cn VARCHAR(255) NULL,
    state_id VARCHAR(64) NULL COMMENT '上级 region id（递归层级）',
    state_name VARCHAR(255) NULL,
    state_name_cn VARCHAR(255) NULL,
    country_id VARCHAR(64) NULL,
    longitude DECIMAL(13,10) NULL,
    latitude DECIMAL(13,10) NULL,
    note VARCHAR(64) NULL COMMENT 'region type（city/province_state 等，旧代码语义）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_city_info (city_id),
    KEY idx_city_info_country (country_id),
    KEY idx_city_info_state (state_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='城市/州省档案（原 hotel-base.saveCityList）';

-- 产品-供应商映射（global_product_supplier）：**本仓不建此表**（2026-08-20 撤除）
--
-- 它是聚合域的桥——把聚合后的统一产品与各家供应商的卖法连起来，用途只有一个：比价检索。
-- 而 R-6.1 定案「聚合（酒店级+房型级）不放在供应商网关」，R-6.3「聚合域引用 productKey；
-- 网关执行路径不引用聚合产物」。仓内 ProductIdentityArchRulesTest 早已把它与
-- amap_expedia_match / hotel_base_mapping 并列为「对照表」，禁止四链路引用。
--
-- 它当初在这里，只是因为 2026-08-07 还原旧中台时一并建了（旧中台 hotel-base 带聚合层）。
-- 撤除前的实况：全仓零 SELECT，统一侧三列是供应商侧的 1:1 拷贝（生产抽样 1000/1000 相同）。
-- 谁做聚合谁自建这张映射表，SPA 只负责产出 productKey。

-- ---------- 供应商档案域 ----------

-- 供应商酒店原始档案 ← info.SupplierHotelBaseRequest
-- 决策①：合并原 base 域副本表（GlobalHotelSupplierRequest），映射列 hotel_id/merger/country_name_cn 落在这里
CREATE TABLE IF NOT EXISTS supplier_hotel_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_id INT NOT NULL,
    supplier_hotel_id VARCHAR(64) NOT NULL,
    hotel_id VARCHAR(64) NULL COMMENT '统一酒店ID（映射列，原 base 域副本表 hotelId；Expedia 打底=supplier_hotel_id）',
    merger TINYINT(1) NOT NULL DEFAULT 0 COMMENT '合并状态（原 base 域副本表 merger）',
    supplier_hotel_name VARCHAR(255) NULL,
    supplier_hotel_name_cn VARCHAR(255) NULL,
    telephone VARCHAR(64) NULL,
    postcode VARCHAR(64) NULL,
    currency VARCHAR(64) NULL,
    address VARCHAR(255) NULL,
    address_cn VARCHAR(255) NULL,
    country_code VARCHAR(64) NULL,
    country_name VARCHAR(255) NULL,
    country_name_cn VARCHAR(255) NULL COMMENT '原 base 域副本表独有列，随合并保留',
    country_id VARCHAR(64) NULL,
    city_id VARCHAR(64) NULL,
    city_name VARCHAR(255) NULL,
    city_name_cn VARCHAR(255) NULL,
    state_name VARCHAR(255) NULL,
    state_name_cn VARCHAR(255) NULL,
    fax VARCHAR(64) NULL,
    longitude VARCHAR(64) NULL,
    latitude VARCHAR(64) NULL,
    hotel_type VARCHAR(64) NULL,
    rooms INT NULL,
    brand_id VARCHAR(64) NULL,
    brand_name VARCHAR(255) NULL,
    group_id VARCHAR(64) NULL,
    group_name VARCHAR(255) NULL,
    recommend_level INT NULL,
    score VARCHAR(64) NULL,
    book_able TINYINT(1) NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    breakfast INT NULL,
    descriptions TEXT NULL,
    introduce_info TEXT NULL,
    del TINYINT(1) NOT NULL DEFAULT 0,
    operator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_shb_supplier_hotel (supplier_id, supplier_hotel_id),
    KEY idx_shb_hotel (hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商酒店原始档案（原 hotel-info.saveHotelInfo）';

-- 供应商房型原始档案 ← info.SupplierRoomBaseRequest
CREATE TABLE IF NOT EXISTS supplier_room_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_id INT NOT NULL,
    supplier_room_id VARCHAR(64) NOT NULL,
    supplier_hotel_id VARCHAR(64) NOT NULL,
    room_id VARCHAR(64) NULL COMMENT '统一房型ID（映射列，原 base 域副本表 roomId）',
    supplier_room_name VARCHAR(255) NULL,
    supplier_room_name_cn VARCHAR(255) NULL,
    description TEXT NULL,
    area VARCHAR(64) NULL,
    floor VARCHAR(64) NULL,
    broad_net INT NULL,
    bed_info_list TEXT NULL COMMENT 'JSON：List<List<BedInfoDTO>>',
    capacity INT NULL,
    has_bathroom INT NULL,
    has_windows INT NULL,
    is_smoking INT NULL,
    is_add_bed INT NULL,
    service TEXT NULL,
    remarks TEXT NULL,
    merger INT NOT NULL DEFAULT 0 COMMENT '合并状态',
    status TINYINT(1) NOT NULL DEFAULT 1,
    operator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_srb_supplier_room (supplier_id, supplier_room_id),
    KEY idx_srb_supplier_hotel (supplier_hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商房型原始档案（原 hotel-info.saveRoomInfo）';

-- 供应商产品档案：一行 = 一个卖法（productKey 粒度）
--
-- 2026-08-20 重设计（R-2.7/2.8/2.9）。原表是 2026-08-07 从旧中台 info.SupplierProductBaseRequest
-- 还原来的，productKey 是 8 天后以「改一列语义 + 加一列 hint」retrofit 上去的，列没重新设计，
-- 后果有三：
--   ① 身份列叫 supplier_product_id，读起来像"供应商的产品 id"，实际存的是我方 productKey；
--   ② breakfast/cancel_type 是旧 DTO 的 Integer，把 MealSignature(B1L1D1) 与 CancelClass 压成
--      0/1，占用连列都没有——生产实测 1,359 组档案「四列全同、productKey 不同」，表无法自证；
--   ③ has_window/supplier_bed_desc 是房型层事实，混在卖法行上要重复写 N 遍（同酒店同房型实测
--      最多 8 个卖法），且两家供应商都硬编码占位、30.8 万行全是 0。
CREATE TABLE IF NOT EXISTS supplier_product_base (
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- 身份
    supplier_id INT NOT NULL,
    product_key VARCHAR(64) NOT NULL COMMENT '产品身份 sha256 hex(R-1.1);禁止存易腐报价码(R-2.1)',

    -- productKey 的成分（连同 supplier_id 共七个）。必须能由这些列重算出 product_key(R-2.7)：
    -- 判据可执行——拿这些列重算一遍 sha256，必须等于 product_key
    supplier_account VARCHAR(64) NOT NULL COMMENT '成分 a:账号/渠道 profile。艺龙=账户名,Expedia=partnerPointOfSale',
    supplier_hotel_id VARCHAR(64) NOT NULL COMMENT '成分 h',
    supplier_room_id VARCHAR(64) NOT NULL COMMENT '成分 r。艺龙=RatePlan.RoomTypeId,非外层 Room.RoomId',
    meal_signature VARCHAR(8) NOT NULL COMMENT '成分 m:MealSignature.canonical(),如 B1L0D0。禁止降维成布尔(R-2.7)',
    cancel_class VARCHAR(20) NOT NULL COMMENT '成分 c:CancelClass 名,如 FREE_CANCELLABLE。UNKNOWN 不进目录(R-5.4)',
    occupancy VARCHAR(32) NOT NULL COMMENT '成分 o:占用规范串,如 2 或 2-9,4',

    -- 产品事实（不进 key）
    supplier_product_name VARCHAR(255) NULL COMMENT '自由文本,不进 key(R-1.2)',
    supplier_quote_hint VARCHAR(64) NULL COMMENT '申报为稳定的供应商真码(如 Expedia rate_id),解析快速通道(R-2.3),非身份;易腐供应商恒 NULL',

    del TINYINT(1) NOT NULL DEFAULT 0,
    operator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_spb_product_key (supplier_id, product_key),
    KEY idx_spb_supplier_hotel (supplier_id, supplier_hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商产品档案：一行=一个卖法(productKey 粒度)';
