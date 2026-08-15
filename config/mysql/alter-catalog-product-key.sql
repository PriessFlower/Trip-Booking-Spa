-- 产品身份落地（docs/product-identity.md 阶段3）：目录两表新增稳定真码 hint 列。
-- 适用于已按旧版 spa-catalog-schema.sql 建过表的环境（生产两表当前均为 0 行，纯加列无回填）。
-- 新环境直接跑 spa-catalog-schema.sql 即可，无需本脚本。
--
-- 列语义变更说明（无 DDL，只是约定）：
--   supplier_product_id 自此存 productKey（sha256 hex 64 字符，恰好占满 VARCHAR(64)），
--   不再存供应商报价码；报价码中申报为稳定的（R-2.3）进 supplier_quote_hint。

ALTER TABLE global_product_supplier
    ADD COLUMN supplier_quote_hint VARCHAR(64) NULL
        COMMENT '申报为稳定的供应商真码(如 Expedia rate_id),解析快速通道(R-2.3),非身份;易腐供应商恒 NULL'
        AFTER supplier_product_id,
    MODIFY COLUMN supplier_product_id VARCHAR(64) NOT NULL
        COMMENT '产品身份=productKey(sha256 hex,docs/product-identity.md R-1.1);禁止存易腐报价码(R-2.1)';

ALTER TABLE supplier_product_base
    ADD COLUMN supplier_quote_hint VARCHAR(64) NULL
        COMMENT '申报为稳定的供应商真码(如 Expedia rate_id),解析快速通道(R-2.3),非身份;易腐供应商恒 NULL'
        AFTER supplier_product_id,
    MODIFY COLUMN supplier_product_id VARCHAR(64) NOT NULL
        COMMENT '产品身份=productKey(sha256 hex,docs/product-identity.md R-1.1);禁止存易腐报价码(R-2.1)';
