# 产品身份与聚合边界（productKey / 腐性申报 / resolve 管线）

**定位**：产品身份的实现规范。实现与评审按编号引用规则（如 R-2.1）。MUST=违反即 bug；SHOULD=偏离须书面说明理由。
**来源**：2026-08-14 与周涛对齐定稿。证据基础：① 本仓沙箱实测（Expedia）；② tg-trip-cursor 生产代码与 specs 专项调查（房型聚合、供应商轮换、四类事故）。
**正本声明**：R 规则正文**只存本文一份**（PROJECT.md §5.1.3）。wiki 的 AI 版是本文的规则索引（编号+摘要+链接），人类版是面向工程师的叙事版；事故取证归档在 issue #44。落地状态以本文 §7 为准，wiki 不另记状态。
**前置**：`docs/architecture.md`（五层架构、§6 身份与令牌）、`docs/gateway-boundary.md`（网关不做比价）。

## 0. 术语

| 术语 | 定义 |
|---|---|
| **productKey** | 网关派生的稳定产品身份，标识"卖法"（等价类），非单条报价 |
| **令牌 / 票** | 供应商签发的易腐报价凭证（rpid、RatePlanID、book_hash、rateKey、`?token=` 等） |
| **腐性** | 标识随时间失效的性质。易腐=会轮换/过期；稳定=长期有效 |
| **resolve** | 令牌失效后，拿 productKey 向供应商现货重新匹配等价报价并签发新令牌的过程 |
| **对照表** | 聚合域的映射：高德 POI↔供应商酒店、统一房型↔供应商房型 |
| **OfferStore** | 网关内带 TTL 的令牌存储（已存在，`architecture.md` §4.2） |

## 1. 身份规则

- **R-1.1 (MUST)** `productKey = hash(supplier_code, 账号/渠道profile, supplier_hotel_id, supplier_room_id, 餐食规范值, 退改类, 占用)`。sha256 hex（64 字符，恰好适配现有 `product_id VARCHAR(64)`）。
- **R-1.2 (MUST NOT)** 以下不得进键：供应商报价码、价格、床型、房型名称等自由文本、任何含绝对时间戳的字段、任何对照表/聚合产物。
- **R-1.3 (MUST)** 账号必须进键。证据：cursor 汇智双账号共享产品 ID 空间，抽样 600 家 69% 价格不同，三要素裸查被其 spec 定性为 bug（2026-07-11）。
- **R-1.4 (MUST)** productKey 语义为等价类（"卖法"）。一个 key 对应 0..N 条在售报价均为正常态。证据：艺龙同房同餐 ~14 个 GoodsUniqId 轮换在售。
- **R-1.5 (MUST)** 身份只许派生，不许发号（禁止自增序列铸号）。证据：cursor 嵌合体事故——auto_increment 回拨 → 13,090 家酒店身份错乱、错身份真实成交 11 单。
- **R-1.6（元规则）** 键成分的选择判据：该成分意外变化时，后果必须是"少卖"（resolve 匹配不到 → 如实无货 → 目录日同步自愈），不得是"卖错"。

## 2. 存储规则

- **R-2.1 (MUST NOT)** 易腐令牌不得写入 MySQL（目录表、订单表、任何持久层）。
- **R-2.2 (MUST)** 易腐令牌只存 OfferStore，TTL < 该供应商已知轮换周期（汇智 4h → TTL ≤ 2h）。承接既有纪律：句柄存活时间必须短于供应商凭据有效期（`OfferStore` javadoc）。
- **R-2.3 (MAY)** 申报为稳定且有证据的供应商真码（美团 `goodsId`、Expedia `rate_id`）可入目录 hint 列，语义=解析快速通道，**非身份**。身份列只放 productKey。
- **R-2.4 (MUST)** `global_product_supplier` 行=桥：统一侧列（聚合结果）可改；供应商侧列（事实）不改。聚合纠错只允许重写统一侧。
- **R-2.5 (MUST)** 订单/快照只绑供应商侧（productKey + 条款快照），不得以对照表统一侧 ID 作身份。

## 3. resolve 管线规则

流程：① OfferStore 活令牌 → 直用；② 过期 → 实时现货匹配换新；③ 匹配失败 → 如实报当日无此卖法。

- **R-3.1 (MUST)** ② 只许实时调供应商现货接口，禁止从网关自身缓存/DB 解析候选。cursor 实测：重验旧 id **0%**（0/561）；读自家陈缓存 **8%**（整代同轮换）；睡等异步落地 **92% 白等**。唯一存活方案=现拉整店→匹配→新码验（其 clwy 主路）。
- **R-3.2 (MUST)** 硬门：同 `supplier_room_id` ∧ 同餐食 ∧ 退改不劣于（R-5.2）∧ 占用满足。任一不合即非等价，禁止放宽。证据：cursor 订单 49046202，仅按最便宜救回把含早错配成不含早。
- **R-3.3 (MUST)** 硬门幸存者多条 → 取最低总价；再过价格容差门（**双门取严**）：新价 ≤ 客人所见展示价 + min(展示价 × 容差比例, 绝对帽)，两参数均可配（默认 2% / 20 元），超出拒绝自动换票。比例门保证任意单价下 drift 只吃毛利不产生亏损（实测成交毛利 7~11%）；绝对帽是单笔自动让利的财务上限——比例制的绝对敞口随单价无界放大（5 万单 2% = 1000 元），且实测存在毛利 3% 的薄单与负毛利单（取证 issue #59，2026-08-15 补订）。
- **R-3.4 (MUST)** resolve 全程有时间预算（参考：二验 1500ms、保底 300ms），预算不足直接走③。
- **R-3.5 (MUST NOT)** ③ 禁止兜底成"可订"、禁止跨供应商静默转单。证据：cursor 艺龙 H001083 被兜成可订 → 建单暴死丢真单。
- **R-3.6 (MUST)** 全部供应商共用同一条管线实现，禁止 per-supplier 救回补丁（反面：cursor 5 条钩子位置各异的补丁）。
- **R-3.7 (MUST)** 匹配键用供应商房型 ID，自由文本房型名不参与匹配。

## 4. 供应商接入规则（腐性申报三行制）

- **R-4.1 (MUST)** 接入前分别申报三个标识的稳定性，各附证据（供应商文档或实测）：`hotel_id` / `room_id` / 报价码。
- **R-4.2 (MUST)** 无证据一律按易腐处理。成本不对称：错待稳定码=多一次现查（几百毫秒）；错待易腐码=僵尸价+丢单。
- **R-4.3 (MUST)** `room_id` 不稳或不存在 → 现货级降级：房型身份用结构化属性（床型+容量+浴室，参照 ratehawk `rg_ext`），不进房型级目录、不参与房型级聚合。
- **R-4.4 (MUST)** `hotel_id` 不稳 → 拒接（酒店静态映射是业务打底）。
- **R-4.5** 首批申报（2026-08-14）。**本表是预研快照，不是免检凭证**：每家供应商实际迁入 SPA 时，必须重新核对①该供应商接口文档的原始描述、②cursor 仓内该家的对接代码与救回补丁，复核后按 R-4.1 重新申报（证据可能已过时，供应商也可能改版）：

| 供应商 | 报价标识 | 腐性 | 证据 |
|---|---|---|---|
| Expedia | rate_id | 稳定 | 本仓测试端点实测：跨日期/跨天不变；易腐的 `?token=` 上游已分离。**跨端点（test↔生产）是否同值未证**，认证后复核 |
| meituan | goodsId | 稳定 | 供应商文档"产品ID"；cursor 零救回代码 |
| tourmind | RateCode | 稳定 | 供应商文档"全局唯一" |
| greencloud | productCode | 稳定 | cursor 代码注释：无独立 token，下单沿用 productCode |
| xiwan | ratePlanId | 弱稳定→按易腐起步 | 仅 A/B 探针"双拉取稳定"实测 |
| local | ratePlanId | 弱稳定→按易腐起步 | 仅"无时效代码"的消极证据 |
| 汇智（本仓 huitravel） | ratePlanCode/rpid | **易腐 ≤4h** | cursor 团队确认 2026-06-19；告警样本 35/35 全 ERR:1001 |
| dida（本仓 didatravel） | RatePlanID | **易腐，快于 4h** | 错误码 2005；cursor 06-29 实证失败 rpId 全不在现货 |
| clwy | rateplanId(hash) | **易腐，分代轮换** | 60 天 58 次重放仅 4 成功，93% 撞 `500 No Availability` |
| 艺龙 | GoodsUniqId | **易腐，会话级** | cursor 代码注释"会话级短时效凭证"；07-19 实测 45/47 全灭 |
| 飞猪 | rateKey | **易腐，须同 session** | 与 requestTraceId 不同代 → 供应商返 214 |

## 5. 退改规则（三层）

- **R-5.1 (MUST)** 键内退改成分=粗分类：`FREE_CANCELLABLE` / `NON_REFUNDABLE` / `UNKNOWN`。完整条款（含绝对时间戳）禁止进键。
- **R-5.2 (MUST)** resolve 换票硬门="条款不劣于"：新票免费取消截止不得早于客人所见、罚金结构不得更重。更差 → 拒换走③。
- **R-5.3 (MUST)** 订单契约存完整分段条款快照（`cancel_penalties` 原样保真），退款以快照为准。
- **R-5.4 (MUST)** 摄取时餐食/退改解析失败=UNKNOWN，UNKNOWN 不进目录。禁止默认值兜底（反面：cursor dida 餐食未知→0、clwy 退改未知→不可退）。承接"不确定不许说成确定"。

## 6. 聚合边界规则

- **R-6.1（定案）** 比价建在聚合上；订单绝不穿过聚合；聚合（酒店级+房型级）不放在供应商网关。
- **R-6.2 (MUST NOT)** 网关四链路（查价/验价/下单/取消）禁止读取任何对照表。
- **R-6.3 (MUST)** 依赖单向：聚合域引用 productKey；网关执行路径不引用聚合产物。
- **R-6.4 (MUST NOT)** 身份字段禁止混装比价产物（反面：`md5#subSaleId` → 13 处拆解、两套分隔符、15min TTL 过期成常态故障）。
- **R-6.5 (MUST NOT)** 下单路由禁止依赖比价期缓存（反面：cursor winner 缓存 30min，miss 则订到不同供应商）。订哪家由客人点击定死。
- **R-6.6 (MUST)** 聚合域固有四规：①派生键不铸号（=R-1.5）；②出过单的映射受删除守卫（反面：富国岛洲际订单 2606261523 供给静默蒸发）；③死映射软退三段式（摘消费面→停售价→K 轮确认→硬删）；④匹配结构化属性打底、名字辅助。

## 7. 落地顺序

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 本文档落地 + 修正 architecture.md §6（rate.id 重铸论、拆解处数） | ✅ PR #45 |
| 1 | `SupplierIdentityProfile`（申报代码化）+ `ProductKeyFactory` + 查价响应附加 productKey（零行为变化） | ✅ PR #45 |
| 2 | resolve 管线接 Expedia：验价令牌死 → 按上游携带的 productKey 现货匹配（`tryResolveByProductKey`）→ `ResolveGate` 选最低+容差门 → 换票或如实 RATE_DEAD。开关 `supplier.expedia.resolve-enabled` 默认关，关闭时行为与旧实现一致 | ✅ 已实现 |
| 3 | 目录层通电：`supplier_product_id` 改存 productKey、新增 `supplier_quote_hint` 列（DDL：`config/mysql/alter-catalog-product-key.sql`）、UNKNOWN 不入目录、建档键与查价键同一份代码派生 | ✅ 已实现。**2026-08-15 全量填充经 test.ean.com 完成，属链路打通期数据**：键/属性/有货分布有效（测试端点查价为真实库存镜像，实证推断），hint 跨端点未证——认证切 api.ean.com 后重跑建档整库刷新（幂等，键不变） |
| 4 | **移植标准**：cursor 供应商迁入 SPA 时必须生在新管线上——申报（§4）→ 适配层两钩子（现货查询、餐食/退改规范化）→ productKey/resolve/OfferStore 全复用。SPA 现存的非 Expedia 供应商代码（didatravel/huitravel/ratehawk/travelconnect/aichotels/meituan）均为待替换旧代码，**整包替换、不原地修补**（其中 didatravel 用存库码直接验价违反 R-3.1、huitravel rpid 落库违反 R-2.1——记录在案，由替换消灭） | 待做 |
| 5 | 规则测试化：`ProductIdentityArchRulesTest`——R62_gatewayChainsMustNotReadMappingTables（扫 30 个四链路类，禁引用对照表）+ R21_perishableTokensMustNotBePersisted（扫全部 mapper XML，禁令牌字段落库） | ✅ 已实现 |
