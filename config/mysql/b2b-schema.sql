-- =====================================================================
-- B2B 代理商平台（b2b 包）专属表
-- 库：tg_trip_spa（与业务表同库，以 b2b_ 前缀标明归属）
-- 执行者：DBA。应用不建表，启动时只校验（各 *Store#verifySchema）；
--         表缺失或缺列一律拒绝启动，故本文件须在部署前执行。
--
-- 为什么需要这三张表：Expedia 上线要求中「B2B 才适用」一节要求下游代理在预订前
--   阅读并接受 Expedia 下游代理协议，且代理工具须能访问原始行程号。前者要留痕
--   （b2b_agreement_accept），后者要求订单能按代理商隔离（b2b_order_owner），
--   两者都以代理商账号为前提（b2b_agent）。
--
-- 订单本体不在此处：下单仍由 bff 包的 bff_order 记账，本包只记「哪个订单属于谁」。
--   b2b 与 bff 共用同一条 Expedia 车道与同一套取数链路，重复建一张订单表只会
--   造出两份可能不一致的账。
-- =====================================================================

CREATE TABLE IF NOT EXISTS b2b_agent (
    agent_id      VARCHAR(32)  NOT NULL COMMENT '代理商编号，订单归属与协议留痕均以此为键',
    login_name    VARCHAR(128) NOT NULL COMMENT '登录名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'PBKDF2 哈希，格式「pbkdf2-sha256:迭代次数:盐:哈希」，盐与哈希为 Base64；串内自带参数，故调高迭代不作废存量口令',
    agent_name    VARCHAR(255) NOT NULL COMMENT '代理商名称（公司名），下单页与订单页展示',
    contact_email VARCHAR(255) NULL     COMMENT '代理商业务联系邮箱，非旅客信息',
    contact_phone VARCHAR(64)  NULL     COMMENT '代理商业务联系电话，非旅客信息',
    status        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 启用；0 停用，停用后登录即拒',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (agent_id),
    UNIQUE KEY uqx_b2b_agent_login_name (login_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B2B 代理商账号';

-- 一个代理商对同一协议版本只签一次；协议改版后 unique 键放行，代理商须重签。
CREATE TABLE IF NOT EXISTS b2b_agreement_accept (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id          VARCHAR(32)  NOT NULL COMMENT '签署人',
    agreement_version VARCHAR(32)  NOT NULL COMMENT '协议版本，取自配置 b2b.agreement.version',
    accepted_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '点击同意的时刻',
    client_ip         VARCHAR(64)  NULL     COMMENT '签署时的来源 IP，Site Review 证据',
    user_agent        VARCHAR(512) NULL     COMMENT '签署时的 User-Agent，Site Review 证据',
    PRIMARY KEY (id),
    UNIQUE KEY uqx_b2b_agreement_accept_agent_version (agent_id, agreement_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Expedia 下游代理协议签署留痕';

-- 订单归属与下单快照。bff_order 没有代理商维度，B2B 侧的「只看自己的单、只能取消自己的单」
-- 靠本表判定；查不到归属的订单对 B2B 一律视为不存在。
--
-- rate_amenities 是下单当刻该房价包含项（早餐、Wi-Fi 等）的快照。之所以要存：
-- 该字段只出现在房价查询的响应里，验价与下单响应都不带，订单查询更无从取得——
-- 不在下单时留档，事后就再也说不清这单到底含不含早。
CREATE TABLE IF NOT EXISTS b2b_order_owner (
    order_id       VARCHAR(32) NOT NULL COMMENT '我方单号，与 bff_order.order_id 同值',
    agent_id       VARCHAR(32) NOT NULL COMMENT '下单的代理商',
    rate_amenities JSON        NULL     COMMENT '下单时该房价的包含项原文数组，语言随查价时的展示语言',
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (order_id),
    KEY idx_b2b_order_owner_agent (agent_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B2B 订单归属与下单快照（订单本体在 bff_order）';
