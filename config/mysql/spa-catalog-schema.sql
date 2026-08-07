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

-- 产品-供应商映射 ← base.GlobalProductSupplierRequest
CREATE TABLE IF NOT EXISTS global_product_supplier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id VARCHAR(64) NULL COMMENT '统一产品ID',
    room_id VARCHAR(64) NULL,
    hotel_id VARCHAR(64) NULL,
    supplier_id INT NOT NULL,
    supplier_hotel_id VARCHAR(64) NULL,
    supplier_room_id VARCHAR(64) NULL,
    supplier_product_id VARCHAR(64) NOT NULL,
    supplier_product_name VARCHAR(255) NULL,
    supplier_product_name_cn VARCHAR(255) NULL,
    supplier_bed_desc TEXT NULL,
    has_window INT NULL,
    breakfast INT NULL,
    cancel_type INT NULL,
    del TINYINT(1) NOT NULL DEFAULT 0,
    operator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_gps_supplier_product (supplier_id, supplier_product_id),
    KEY idx_gps_hotel (hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品-供应商映射（原 hotel-base.aggregatorProductMapping）';

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

-- 供应商产品原始档案 ← info.SupplierProductBaseRequest
CREATE TABLE IF NOT EXISTS supplier_product_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id VARCHAR(64) NULL COMMENT '统一产品ID',
    room_id VARCHAR(64) NULL,
    supplier_id INT NOT NULL,
    supplier_hotel_id VARCHAR(64) NULL,
    supplier_room_id VARCHAR(64) NULL,
    supplier_product_id VARCHAR(64) NOT NULL,
    supplier_product_name VARCHAR(255) NULL,
    supplier_product_name_cn VARCHAR(255) NULL,
    supplier_bed_desc TEXT NULL,
    has_window INT NULL,
    breakfast INT NULL,
    cancel_type INT NULL,
    del TINYINT(1) NOT NULL DEFAULT 0,
    operator VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqx_spb_supplier_product (supplier_id, supplier_product_id),
    KEY idx_spb_supplier_hotel (supplier_hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商产品原始档案（原 hotel-info.saveProductInfo）';
