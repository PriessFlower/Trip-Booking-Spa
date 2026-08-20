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

- **R-1.1 (MUST)** `productKey = hash(supplier_code, 账号/渠道profile, supplier_hotel_id, supplier_room_id, 餐食规范值, 退改类, 占用)`。sha256 hex（64 字符）。档案表的身份列即 `supplier_product_base.product_key`（2026-08-20 前叫 `supplier_product_id`——旧列被改了语义而列名没改，读起来像"供应商的产品 id"，实际存的是我方 productKey）。
- **R-1.2 (MUST NOT)** 以下不得进键：供应商报价码、价格、床型、房型名称等自由文本、任何含绝对时间戳的字段、任何对照表/聚合产物。
- **R-1.3 (MUST)** 账号必须进键。证据：cursor 汇智双账号共享产品 ID 空间，抽样 600 家 69% 价格不同，三要素裸查被其 spec 定性为 bug（2026-07-11）。
- **R-1.4 (MUST)** productKey 语义为等价类（"卖法"）。一个 key 对应 0..N 条在售报价均为正常态。证据：艺龙同房同餐 ~14 个 GoodsUniqId 轮换在售。
- **R-1.5 (MUST)** 身份只许派生，不许发号（禁止自增序列铸号）。证据：cursor 嵌合体事故——auto_increment 回拨 → 13,090 家酒店身份错乱、错身份真实成交 11 单。
- **R-1.6（元规则）** 键成分的选择判据：该成分意外变化时，后果必须是"少卖"（resolve 匹配不到 → 如实无货 → 目录日同步自愈），不得是"卖错"。

## 2. 存储规则

- **R-2.1 (MUST NOT)** 易腐令牌不得写入 MySQL（目录表、订单表、任何持久层）。
- **R-2.2 (MUST)** 易腐令牌只存 OfferStore，TTL < 该供应商已知轮换周期（汇智 4h → TTL ≤ 2h）。承接既有纪律：句柄存活时间必须短于供应商凭据有效期（`OfferStore` javadoc）。
- **R-2.3 (MAY)** 申报为稳定且有证据的供应商真码（美团 `goodsId`、Expedia `rate_id`）可入目录 hint 列，语义=解析快速通道，**非身份**。身份列只放 productKey。
- **R-2.4 (MUST)** **聚合的映射表不建在网关**。「统一产品 ↔ 各家供应商卖法」这张桥属聚合域（用途只有比价检索），由做聚合的一方自建；SPA 只负责产出 productKey。桥的内部纪律仍然成立——统一侧列（聚合结果）可改，供应商侧列（事实）不改，聚合纠错只许重写统一侧——但那是聚合域自己要守的，不在本仓。

  本仓原有 `global_product_supplier`，是 2026-08-07 还原旧中台（hotel-base 带聚合层）时一并建的。撤除前实况：**全仓零 SELECT**，统一侧三列是供应商侧的 1:1 拷贝（生产抽样 1000/1000 相同），每条档案白写两遍。2026-08-20 停写并撤表，守护测试 `ProductIdentityArchRulesTest.R61_aggregationBridgeMustNotComeBack` 防复活。
- **R-2.5 (MUST)** 订单/快照只绑供应商侧（productKey + 条款快照），不得以对照表统一侧 ID 作身份。
- **R-2.6 (MUST)** **按腐性分层存储**：稳定信息进 MySQL，易腐信息进 Redis 并靠 TTL 自然消亡。

  | 落点 | 放什么 | 判据 |
  |---|---|---|
  | MySQL 目录/档案 | productKey、房型、餐食、退改类、占用、房型名 | 供应商换一批报价码后**仍然成立**的事实 |
  | Redis | 当轮价格、易腐报价码（quote hint）、OfferStore 句柄 | 下一轮刷价即作废，或有明确轮换周期 |

  判据是一句话：**「供应商明天换一批报价码，这条信息还对吗？」对 → MySQL；错 → Redis。**

  为什么是 MUST 而不是优化建议：把稳定信息塞进短命缓存会同时坏三件事——① Redis 承载与刷价覆盖面线性膨胀（2026-08-19 实测：艺龙仅 2,615 家酒店就占 1.19G/2G，其中 **97.4% 是产品详情**，按全量 23,584 家外推超 10G）；② TTL 无从取值，长了浪费、短了验价反查不到；③ 缓存被当成事实源，口径随刷价区间漂移（2026-08-19 的换票基准 bug：详情快照是刷价那次的 1 晚价，客人看的是查询区间的多晚价，差一个量级）。

  Redis 里的稳定信息只应作为**读性能副本**存在（可随时重建、丢了不影响正确性），不得成为唯一事实源。

- **R-2.7 (MUST)** **档案表的列必须能重算出 productKey**。即 R-1.1 的七个成分
  （supplier_code、账号、supplier_hotel_id、supplier_room_id、餐食、退改类、占用）
  各有一列，且**按派生时的原形存**——餐食存 `MealSignature.canonical()`（如 `B1L0D0`）、
  退改存 `CancelClass` 名（如 `FREE_CANCELLABLE`），不得压成布尔或整型。

  判据可执行：拿表里的列重算一遍 sha256，必须等于 `product_key`。不等即缺列或写错。

  为什么是 MUST：productKey 是 sha256，**单向不可逆**。成分在派生时是完整的
  （`MealSignature`/`CancelClass` 都是内部规范型），一旦落表时被降维，表就既没有原信息、
  也无法从身份列反推——档案自此无法自证，也无法用于对账。
  反面即改造前（2026-08-20 已修）：`breakfast INT` 把 `B1L1D1`（含三餐）与 `B1L0D0`（只含早）
  压成同一个 `1`；占用连列都没有，同一 (酒店,房型,餐食,退改) 下 1,359 组各有 2~3 行
  productKey 不同的档案，从表上看完全一样。

  > 成因备注：该表是 2026-08-07 从旧中台还原的（`legacy-schema-restoration.md`），
  > `breakfast INT` / `cancel_type INT` 是旧接口 DTO 的字段类型；productKey 是 8 天后
  > 以「改一列语义 + 加一列 hint」retrofit 上去的，列没有重新设计。不是权衡后的取舍。

- **R-2.8 (MUST)** **建档只落派生产物，不得自行判定**。餐食/退改/占用等键成分由
  productKey 派生器一次算出并原样透出，建档侧照抄入库；禁止建档从原始响应或出参 DTO
  重新判一遍。

  为什么：同一事实两处判定必然漂移，且漂移不报错。反面即改造前（2026-08-20 已修）——建档侧
  `ElongCatalogService.hasFreeCancelWindow` 只看 `cancelType==1`，而派生器
  `classifyCancel` 还要求 `RefundType.NO_DEDUCTION`；两者目前结论一致，靠的是
  UNKNOWN 先被 `isCatalogEligible` 挡掉（R-5.4），**不是靠共用判据**。UNKNOWN 口径一动即分叉。

  推论：派生器的返回值不能只是 key 字符串，须一并带出各成分——实物即 `ProductIdentity`。

- **R-2.9 (MUST NOT)** 档案表不得混入非产品层的属性。房型层事实（有窗、床型、面积、
  容量、吸烟）归 `supplier_room_base`，酒店层归 `supplier_hotel_base`。

  为什么：一行档案=一个卖法，而同一 (酒店,房型) 下实测最多 8 个卖法
  （餐食 × 退改 × 占用），房型层的一个事实要在 8 行里各写一遍，且无机制保证一致。
  反面即改造前的 `supplier_product_base.has_window`（2026-08-20 已随表重设计移除）：两家供应商都硬编码 0
  （`ExpediaProductMappingService` 注释自认「有窗是房型层事实，产品层保持占位」），
  30.8 万行全是 0 —— 有列、无数据、不报错，比没有这列更危险。

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
| 4 | **移植标准**：cursor 供应商迁入 SPA 时必须生在新管线上——申报（§4）→ 适配层两钩子（现货查询、餐食/退改规范化）→ productKey/resolve/OfferStore 全复用 → **建档落库（R-2.6：稳定信息进目录表，Redis 只留易腐与 TTL 自消项）**。SPA 现存的非 Expedia 供应商代码（didatravel/huitravel/ratehawk/travelconnect/aichotels/meituan）均为待替换旧代码，**整包替换、不原地修补**（其中 didatravel 用存库码直接验价违反 R-3.1、huitravel rpid 落库违反 R-2.1——记录在案，由替换消灭） | 待做 |
| 5 | **档案表重设计**（R-2.7/2.8/2.9）：身份列更名 `product_key`；七个成分各有一列且按派生原形存（`meal_signature`/`cancel_class`/`occupancy`/`supplier_account`）；房型层与聚合域列移除；`global_product_supplier` 停写并撤表。派生器改出 `ProductIdentity`，建档只照抄不判定 | ✅ 已实现（2026-08-20）。**存量清空重建**：艺龙随刷价约 2h 自动铺回；Expedia 需手动跑 `/hotel/expedia/catalog/products`（无定时任务，且认证切 api.ean.com 后本就要重跑整库） |
| 5 | 规则测试化：`ProductIdentityArchRulesTest`——R62_gatewayChainsMustNotReadMappingTables（扫 30 个四链路类，禁引用对照表）+ R21_perishableTokensMustNotBePersisted（扫全部 mapper XML，禁令牌字段落库） | ✅ 已实现 |
