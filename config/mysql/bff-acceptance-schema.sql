-- =====================================================================
-- expdia 验收前端（bff 包）专属表
-- 库：tg_trip_spa（与业务表同库，以 bff_ 前缀标明归属）
-- 执行者：DBA。应用不建表，启动时只校验（OrderStore#verifySchema）；
--         表缺失或缺列一律拒绝启动，故本文件须在部署前执行。
--
-- 存续期：本表是「上游的替身」，非网关的订单表。
--   本服务是供应商网关，订单归上游持有——下单用的 affiliate_reference_id 由上游
--   经 BookingReq.orderId 传入（docs/gateway-boundary.md B5），core 因此一张订单表都没有。
--   验收前端背后没有上游，只能由 bff 层代为记账。
--   验收结束、真前端改走上游后，本表连同 bff 包一并退役。
--
-- 注意：traveler_* 三列存旅客真实姓名与联系方式，仅供确认页/客服使用，
--       不发送给供应商（对 Expedia 一律使用固定联系人）。清退时按个人信息处理。
-- =====================================================================

CREATE TABLE IF NOT EXISTS bff_order (
    order_id        VARCHAR(32)  NOT NULL COMMENT '我方单号，即发往 Expedia 的 affiliate_reference_id；下单结果不确定时凭它反查确证',
    itinerary_id    VARCHAR(64)  NULL     COMMENT 'Expedia 行程号，下单成功后回填',
    property_id     VARCHAR(32)  NOT NULL COMMENT 'Expedia property_id',
    property_name   VARCHAR(512) NULL,
    checkin         VARCHAR(10)  NOT NULL COMMENT '入住日期 yyyy-MM-dd',
    checkout        VARCHAR(10)  NOT NULL COMMENT '离店日期 yyyy-MM-dd',
    occupancy       VARCHAR(255) NULL     COMMENT '占用串，格式「成人数-儿童年龄,儿童年龄」',
    bed_description VARCHAR(255) NULL,
    -- 下单时该房价是否提供多床型选择：ER3 的「床型不保证」提示只在多床型时展示
    bed_choice TINYINT(1) NOT NULL DEFAULT 0,
    traveler_name   VARCHAR(255) NULL     COMMENT '旅客真实姓名，仅存本地不出境',
    traveler_email  VARCHAR(255) NULL     COMMENT '旅客邮箱，仅存本地不出境',
    traveler_phone  VARCHAR(64)  NULL     COMMENT '旅客电话，仅存本地不出境',
    status          VARCHAR(32)  NOT NULL COMMENT '本地订单状态',
    request_json    JSON         NULL     COMMENT '发往 Expedia 的请求原文（TR7 证据 + 排障）',
    response_json   JSON         NULL     COMMENT 'Expedia 响应原文（TR7 证据 + 排障）',
    pricing_json    JSON         NULL     COMMENT '验价价格快照（occupancy_pricing 原文）',
    policy_json     JSON         NULL     COMMENT '政策快照：refundable、cancel_penalties、nonrefundable_date_ranges、paymentOptions',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (order_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='expdia 验收前端订单（bff 专属，验收后退役）';
