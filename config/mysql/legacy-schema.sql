-- 遗留业务表重建脚本
-- 注意：原始 DDL 遗失于旧公司数据库（已无法访问），本文件按 Mapper XML 的列名
-- 与实体类型逆向重建。字段长度/索引为合理推断值；如日后能访问旧库，
-- 请用 SHOW CREATE TABLE 的结果校准本文件。
-- 新表（expedia_property_content）见 expedia-static-schema.sql。

-- Expedia 价格预热任务队列（CPS，ExpediaQueryPriceTaskMapper.xml）
CREATE TABLE IF NOT EXISTS expedia_query_price_task (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    sh_id                 VARCHAR(64)  NOT NULL COMMENT '供应商酒店id',
    delay_check_in        INT          NOT NULL DEFAULT 0 COMMENT '入住日期偏移(天)',
    delay_check_out       INT          NOT NULL DEFAULT 1 COMMENT '离店日期偏移(天)',
    query_count           INT          NOT NULL DEFAULT 0 COMMENT '已查价次数',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_time             DATETIME     NULL COMMENT '最近一次查价时间',
    priority_level_number INT          NOT NULL DEFAULT 0 COMMENT '优先级',
    temporary_upgrade     INT          NOT NULL DEFAULT 0 COMMENT '临时提升优先级 0否 1是',
    upgrade_deadline      DATETIME     NULL COMMENT '临时优先级截止时间',
    PRIMARY KEY (id),
    KEY idx_priority_update (priority_level_number, update_time),
    KEY idx_sh_id (sh_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Expedia查价预热任务队列';

-- RateHawk 价格预热任务队列（CPS，RatehawkQueryPriceTaskMapper.xml，结构与上表一致）
CREATE TABLE IF NOT EXISTS ratehawk_query_price_task (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    sh_id                 VARCHAR(64)  NOT NULL COMMENT '供应商酒店id',
    delay_check_in        INT          NOT NULL DEFAULT 0 COMMENT '入住日期偏移(天)',
    delay_check_out       INT          NOT NULL DEFAULT 1 COMMENT '离店日期偏移(天)',
    query_count           INT          NOT NULL DEFAULT 0 COMMENT '已查价次数',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_time             DATETIME     NULL COMMENT '最近一次查价时间',
    priority_level_number INT          NOT NULL DEFAULT 0 COMMENT '优先级',
    temporary_upgrade     INT          NOT NULL DEFAULT 0 COMMENT '临时提升优先级 0否 1是',
    upgrade_deadline      DATETIME     NULL COMMENT '临时优先级截止时间',
    PRIMARY KEY (id),
    KEY idx_priority_update (priority_level_number, update_time),
    KEY idx_sh_id (sh_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RateHawk查价预热任务队列';

-- 城市时区（InitTimeZoneMapper.xml）
CREATE TABLE IF NOT EXISTS city_zone (
    id           INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    city_name    VARCHAR(128) NOT NULL COMMENT '城市名',
    timezone     VARCHAR(64)  NULL COMMENT '时区，如 Asia/Bangkok',
    country_name VARCHAR(128) NULL COMMENT '国家名',
    PRIMARY KEY (id),
    KEY idx_city_country (city_name, country_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='城市时区映射';

-- 分销上架酒店清单（up_hotel_mapper.xml）
CREATE TABLE IF NOT EXISTS db_up_hotel (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    distribute_id INT         NOT NULL COMMENT '分销渠道id，如 20002',
    supplier_id   INT         NOT NULL COMMENT '供应商id，如 10005',
    hotel_id      VARCHAR(64) NOT NULL COMMENT '酒店id',
    PRIMARY KEY (id),
    KEY idx_distribute_supplier_hotel (distribute_id, supplier_id, hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销上架酒店清单';

-- 供应商酒店ID清单（SupplierHotelIdListMapper.xml + MyBatis-Plus 实体）
CREATE TABLE IF NOT EXISTS supplier_hotel_id_list (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    supplier_id INT          NOT NULL COMMENT '供应商id',
    s_hotel_id  VARCHAR(64)  NOT NULL COMMENT '供应商侧酒店id',
    online      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否在线 0否 1是',
    last_time   DATETIME     NULL COMMENT '最近处理时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    operator    VARCHAR(64)  NULL COMMENT '操作人',
    PRIMARY KEY (id),
    KEY idx_supplier_hotel (supplier_id, s_hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商酒店ID清单';
