# 供应商网关职责边界

> **定位**：本服务作为 tg-trip-cursor 的供应商网关，哪些复杂度应当吃进来、哪些必须留在上游。
> **来源**：2026-08-10 对 tg-trip-cursor 生产代码与 `backend/specs/`（239 篇）的专项调查，
> 主题为「产品标识身份」与「验价令牌对齐」。文中引用的路径均在 tg-trip-cursor 仓。
> **用途**：新增供应商、改动对上游契约前，先读本文档。它记录的是**为什么**这样划边界，
> 而边界一旦被越过，越过的代价有实测数字。

## 1. 为什么需要这份文档

上游在多供应商接入上踩的坑，绝大多数可归为同一个根因：**供应商的内部概念泄漏到了上游**。
一旦泄漏，上游就必须理解它、拼装它、在多处拆解它，而每一处拆解都是一次漂移机会。

三个实测代价：

- **身份与分组被压进同一个字符串**（`md5#subSaleId`）：仓内 **4 处独立拆解**，其中
  `ManualBookingAutoQuoteAbilityImpl` 用 `lastIndexOf('#')` 而另三处用 `indexOf`/`contains`。
- **验价缓存 key 两端口径靠约定对齐**：10 家供应商里 **6 家没走 `keyDims`**，2 间×1 人的多间单
  写入侧落 `a1`、读取侧找 `a2`，**恒定 cache miss**；而那 6 家的 javadoc 还写着「口径一致」。
- **人数口径单间／整单混淆**：30 天 21 单确认失败中 **422 占用超限 6 单（28.6%）**，
  修了三轮（sum → max → `PerRoomAdults`），每一轮都是靠真实丢单才发现。

结论：网关的价值不在于"多一层转发"，而在于**把这类复杂度关在一个地方**。
若网关只是把供应商字段换个名字透出去，它没有创造任何价值，只是多了一跳。

## 2. 网关承担（吃进来）

| # | 职责 | 对应上游的什么痛 |
|---|---|---|
| B1 | 报价身份对外只发**不透明句柄**，上游原样存、原样回传，永不解析 | `md5#subSaleId` 的 4 处拆解 |
| B2 | 供应商令牌换代／过期由网关内部重解析并续期，对上游是"同一句柄一直有效" | 陈码重解析等 **5 条**救回补丁，钩子位置各不相同 |
| B3 | 占用（人数）契约只允许**一种**结构化 per-room 表示，各供应商要 sum／max／首间由网关换算 | 422 家族三次事故 |
| B4 | 价格口径统一为「全部间数 × 全部夜 × 含税 × 单一币种」，附每夜明细 | clwy 单间/全间数致成本落账错、tourmind 固定 ×100 不换汇 |
| B5 | 订单唯一坐标是**我方单号**（`affiliate_reference_id`），查单／取消均不要求供应商单号 | `cancel-id` 映射 bug 反复复发 |
| B6 | 失败分类必须可辨：确定性死码 ≠ 不确定。**禁止把"不知道"兜底成"可订"** | elong `H001083` 被打成 `VALIDATE_NO_RESPONSE` → 落进 `HARD_ERROR_CODES` → DB 兜底成"可订" → 建单暴死丢真单 |
| B7 | 空响应三态可辨：`ok(空)` / `notFound` / `upstreamUnavailable` | `fetchPriceRaw` 空串→null，死店／满房／超时三态塌成一态；dida KR **25.6% 映射是死 id**，339 个死 id 残留 **66,469 行在售僵尸价** |
| B8 | 透出供应商原生的稳定房型／酒店 ID 与**结构化房型属性**（床型床数、景观、吸烟、面积、最大占用） | 房型 100% 靠名字相似度匹配、零 ID 路径；且聚合会**盖写 `room_name` 销毁证据** |

## 3. 网关禁止承担（红线）

### 3.1 不做比价、选供、卖穿闸、换供补单

**这是最容易被"顺手"越过的一条，也是代价最大的一条。**

上游 `md5` 分组指纹存在的唯一理由，就是 checkPrice 时要回查「用户点的这条报价，还有哪些
兄弟供应商的等价报价」以便 PK。渠道协议只有一个 `ratePlanId` 字段可用，于是分组指纹被塞进
身份字段里带出去再带回来——**身份与分组压进同一个字符串**，才长出了 4 处拆解与那处
`lastIndexOf` 不一致。

⚠️ **本服务当前没有这个问题，原因不是"我们只有一个供应商"**（本服务已接入 didatravel、
ratehawk、travelconnect、huitravel、fastpay 等多家），**而是我们不做比价**。

所以这条免疫力是**职责边界带来的，不是自然属性**：

- 增加供应商 → 免疫力不变
- 任何人在网关里实现"顺手比一下价" → 分组身份立刻长回来，4 处拆解随后长回来

比价、选供、`under_cost` 卖穿闸、换供补单属**商业决策层**，是上游的事。
网关只提供报价与价格事实。

### 3.2 不做预刷保鲜以外的多供应商预推

刷价缓存（`task.expedia-cps`）的存在理由是我方查价的响应速度，不是多供应商预推。
上游那 24K 行保鲜层是为多供应商预推而生，本服务不复制其形态。

### 3.3 不碰钱

EPS `payments.type=affiliate_collect` = **我方代收款**，支付链路在上游。
网关提交下单请求时不含任何卡数据。

### 3.4 不持有旅客联系方式

姓名、邮箱、电话一律不出境（已与 Expedia 商定），详见
[expedia-booking-contract.md](expedia-booking-contract.md) §7.2。
由此"订单确认邮件／入住凭证"必须由持有旅客真实联系方式的一方发送，即上游。

### 3.5 不做 canonical 聚合 —— 但栈型未定前这条是有条件的

纯 Expedia 闭环下 `property_id` 即主键，无需聚合。
但若最终是混合栈（Expedia 与其他供应商同台），canonical 映射问题会回来。
上游 kickoff 纪要已明确警告：**「先定栈型再定 mapping 基准」**，而栈型至今未定。
真走混合栈时，「按房型属性返回稳定 roomKey」的接口从可选项变为必需项。

> 注：高德→Expedia 的 ID 对齐（`tg_amap_orders.amap_expedia_match` 81 对映射）
> 是**离线数据工程**，不属于本节所指的在线聚合层。

## 4. 已获得的结构性免疫力，及其失效条件

记录在此是为了**防止被"优化"掉**——这些好处来自设计选择，看起来像是白来的。

| 免疫的痛 | 来源 | 什么会让它失效 |
|---|---|---|
| 分组身份／多处拆解 | 不做比价（§3.1） | 在网关内实现比价 |
| 验价缓存 key 两端对齐 | 令牌自带上下文，不靠拼 key 匹配 | 改为按业务维度拼 key 查缓存 |
| 人数口径单间／整单混淆 | 占用烧在 Expedia token 内，下单请求不带人数 | 让下单请求自己带人数字段 |
| 取消单号映射 | 用 `affiliate_reference_id` 作唯一坐标 | 让取消／查单要求供应商单号 |
| 卖 A 订 B | 用供应商原生 `room_id` | 改用房型名相似度匹配 |

## 5. 待吸收清单

| 级别 | 事项 | 状态 |
|---|---|---|
| P0 | 查单接口接出；`OrderQueryReq.supplierOrderId` 改可选（我方单号足够反查） | ✅ 已做（2026-08-26 对照代码核实：`supplierOrderId` 已可选，`expediaOrderQuerySyncService` 在册） |
| P1 | `offerId` 取代 `prebookToken`；收敛 `plansId`／`sProductId` 冗余身份字段 | 前半已做（全仓无 `prebookToken`）；`sProductId` 仍是验价主检索键，收敛待做 |
| P2 | 占用统一为结构化 per-room；删除未使用却标 `@NonNull` 的死字段 | 待做 |
| P3 | 取消（含 `cancelFee` + `currency`；政策缺失时报 `UNKNOWN` 而非"可免费取消"） | ✅ 已做（2026-08-26）：`CancelRespDTO` 增 `cancelFee`（分）+ `cancelFeeCurrency` + `penaltySource{FIELD/POLICY_DERIVED/NONE}`，NONE 显式区别于 0；罚金不再拼中文 message。取消同时是②层解耦试点：能力接口改吃领域模型（`CancelCommand`/`CancelResult`），JSON 翻译收口 `CancelMapping`，方向由 `CancellationLayerBoundaryTest` 钉住 |
| — | 透出 `room_id` 与结构化房型属性（B8） | 待做 |

### P0 为何列为最高

本服务的三态契约（`BookingOutcome`）向上游承诺「UNKNOWN 时凭订单号反查确证」，
但当前：

- `BookingRespDTO` 在 UNKNOWN 时 `sOrderId` 必然为空——本来就没拿到供应商单号
- `OrderQueryReq.supplierOrderId` 标了 `@NonNull`
- `ExpediaOrderQuerySyncService` 尚不存在，`/client/spa/order` 路由不到 Expedia

即**恰恰在唯一需要反查的场景里，上游拿不到反查所需的入参，接口也没通**。
`QueryOrderAccess` 已实现并经 e2e 验证，只是没接出来。
这是一条已承诺而未兑现的资损防线，优先于任何契约优化。
