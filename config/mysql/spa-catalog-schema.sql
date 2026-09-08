-- =====================================================================
-- spa 专属目录层（原系统还原表）
-- 依据：docs/legacy-schema-restoration.md（旧中台 intl jar 反编译还原）
-- 库：tg_trip_spa（与 trip-cursor 的库物理隔离）
-- 域划分：供应商档案域（hotel-info 还原，3 张）。统一目录域已于 2026-09-08 整域撤除，见下。
-- =====================================================================

-- ---------- 统一目录域：**本仓不再有此域**（2026-09-08 撤除） ----------
--
-- 原有 7 张：hotel_details / room_base / hotel_picture / hotel_extend / country_info /
-- city_info / global_product_supplier。前六张是 2026-08-07 还原旧中台（hotel-base 带目录层）
-- 时一并建的静态目录，由 Expedia 静态摄取→加工链路单向写入。撤除前实况：
--   · 全仓零 SELECT（bff/b2b 对它们的引用数为 0，唯一读取是加工层自己回查 city_id/country_id）；
--   · 数据停在 2026-08-14（两个 @Scheduled 在 Nacos 里始终 enabled:false）；
--   · 占 tg_trip_spa 约 3 GB（hotel_picture 787 万行 1.4 GB + 索引 0.6 GB、hotel_extend 723 MB）。
-- 生产 DDL 已于 2026-09-08 执行（DROP 六表；结构备份见提交信息，
-- country_info/city_info 的数据备份留在 trip-offline:/opt/trip-booking-spa/retired-static/）。
-- 酒店静态内容的家是 trip-booking-agg（hotel_base/room_base/room_i18n 在产），
-- 本仓只保留 expedia_property_content 供 bff/b2b 详情页与 productKey 派生读取。

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
-- 只存供应商侧事实。统一侧列 hotel_id/merger 已于 2026-09-08 撤除：归一属聚合域（R-2.4），
-- 且生产实测 97,409/97,409 行的 hotel_id 就等于 supplier_hotel_id——与 global_product_supplier
-- 当年被撤的同一个病（统一侧是供应商侧的 1:1 拷贝）。country_name_cn 是事实列，保留。
CREATE TABLE IF NOT EXISTS supplier_hotel_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_id INT NOT NULL,
    supplier_hotel_id VARCHAR(64) NOT NULL,
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
    UNIQUE KEY uqx_shb_supplier_hotel (supplier_id, supplier_hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商酒店原始档案（原 hotel-info.saveHotelInfo）';

-- 供应商房型原始档案 ← info.SupplierRoomBaseRequest
-- 同上：统一侧列 room_id/merger 已撤（生产 356,571/356,571 行 room_id = supplier_room_id）。
CREATE TABLE IF NOT EXISTS supplier_room_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_id INT NOT NULL,
    supplier_room_id VARCHAR(64) NOT NULL,
    supplier_hotel_id VARCHAR(64) NOT NULL,
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
    supplier_room_id VARCHAR(64) NOT NULL COMMENT '成分 r = 供应商静态房型号。艺龙=外层 Room.RoomId（物理房型，2026-09-08 起；此前误用 RatePlan.RoomTypeId 销售号，销售号只在下单凭据里）',
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

-- 飞猪查价预热任务队列（与 elong_query_price_task 同构;那份 DDL 在 legacy-schema.sql
-- 是历史原因,新表落本文件）。速率不由本表控制,由 Nacos ratelimit.qps 的
-- GLOBAL_LIMIT:FLIGGY:*:REFRESH 约束。播种口径:艺龙在售名单 ∩ 飞猪映射(比价交集)
-- + 飞猪独家增量,住期 T+0..2 各 1 晚(照 cursor keeper 口径,扩住期改 delay 列即可)。
CREATE TABLE IF NOT EXISTS fliggy_query_price_task (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    sh_id                 VARCHAR(64)  NOT NULL COMMENT '飞猪酒店id（shid）',
    delay_check_in        INT          NOT NULL DEFAULT 0 COMMENT '入住日期偏移(天)',
    delay_check_out       INT          NOT NULL DEFAULT 1 COMMENT '离店日期偏移(天)',
    query_count           INT          NOT NULL DEFAULT 0 COMMENT '已查价次数',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_time             DATETIME     NULL COMMENT '最近一次查价时间',
    priority_level_number INT          NOT NULL DEFAULT 0 COMMENT '优先级(起步单档0)',
    temporary_upgrade     INT          NOT NULL DEFAULT 0 COMMENT '临时提升优先级 0否 1是',
    upgrade_deadline      DATETIME     NULL COMMENT '临时优先级截止时间',
    PRIMARY KEY (id),
    KEY idx_priority_update (priority_level_number, last_time),
    KEY idx_sh_id (sh_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞猪查价预热任务队列';
