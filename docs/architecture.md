# 架构与供应商接入

> **定位**：本服务是什么、分几层、接一家新供应商要做什么、以及网关究竟"网关"了什么。
> **配套**：职责边界（吃什么／不吃什么）见 [gateway-boundary.md](gateway-boundary.md)；
> 提交、分支、配置规范见 [../PROJECT.md](../PROJECT.md)。
> **准确性**：本文所述均于 2026-08-11 对照代码核实。改动契约或新增能力时须同步本文。

## 1. 一句话

本服务是 tg-trip-cursor 的**供应商网关**：对上游提供一套与供应商无关的酒店交易接口，
把各家供应商的协议差异、易腐令牌、含糊结果全部关在自己内部。

上游不需要知道正在跟哪家供应商打交道，也不需要知道任何一家的内部概念。

## 2. 分层

```
                      上游（tg-trip-cursor）
                              │  与供应商无关的统一契约
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ ① 端点层    SpaController                                    │
│            5 个业务端点，按 supplierId 路由到具体实现          │
│            兜底：实现返回空时一律回报「不确定」，绝不回报失败    │
├─────────────────────────────────────────────────────────────┤
│ ② 契约层    5 个能力接口 + 5 个抽象模板                       │
│            模板持有判定纪律：不确定的事不许说成确定的           │
│            三态枚举 BookingOutcome / OrderPresence /          │
│                     CheckPriceOutcome                        │
├─────────────────────────────────────────────────────────────┤
│ ③ 适配层    各供应商的 *SyncServiceImpl                       │
│            唯一允许理解供应商语义的地方：错误码怎么分类、       │
│            凭据有哪几项、状态原文怎么映射                      │
├─────────────────────────────────────────────────────────────┤
│ ④ 通道层    BaseHttpAccess 及各 *Access                       │
│            统一限流（唯一闸门）、重试、解析、监控埋点           │
├─────────────────────────────────────────────────────────────┤
│ ⑤ 状态层    OfferStore（报价句柄）· 刷价缓存 · 静态数据        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ↓  各家私有协议
                        供应商（Expedia / …）
```

**关键约束：供应商语义只允许出现在 ③。** ① ② 不得出现任何一家的字段名或错误码，
④ 不解释业务含义（只管发得出去、收得回来、别打爆配额），⑤ 不解释凭据内容。

### 2.1 这五层在目录里长什么样

**目录不是按层组织的，而是二维的**：先按供应商分包，包内再按层分子目录。所以想从目录
直接读出分层，会在几处被误导——见 §2.2。

```
core/
├── api/
│   ├── common/              跨供应商公共物
│   │   ├── access/          ④ BaseHttpAccess ← 限流唯一闸门
│   │   ├── asynchttp/       ④ ResponseResult / IParser / BaseResponse
│   │   ├── enums/           ② 三态枚举 + SupplierSourceEnum
│   │   └── offer/           ⑤ OfferStore / Offer
│   ├── dto/                 ② 出站契约  *RespDTO
│   ├── request/             ② 入站契约  *Req
│   ├── service/             ② 能力接口 + 5 个 Abstract*SyncSupportService 模板
│   │   └── impl/            ③ 薄适配：8 家的「查价 + 验价」都挤在这里
│   │
│   ├── expedia/             ←── 每家一个包，包内按层
│   │   ├── access/          ④ 该家 HTTP 通道
│   │   ├── bean/            　 该家请求 / 响应模型
│   │   ├── service/impl/    ③ 该家协议逻辑（下单 / 查单也在这）
│   │   ├── staticdata/      ⑤ 静态数据摄取
│   │   └── config enums mapper model utils
│   └── ratehawk/ fastpay/ meituan/ travelconnect/
│       didatravel/ huitravel/ aichotels/
│                            └── access bean service adaptor utils
├── redis/                   ⑤ RedisUtils / 限流器
├── task/                    定时任务（刷价）
├── dao/ config/ monitor/ ratelimit/ placeholder/ util/ exception/
└── rest/controller/         ① 端点层  SpaController
```

按需定位：

| 想找 | 去哪 |
|---|---|
| 某端点入口 | `rest/controller/SpaController` |
| 对外契约长什么样 | `core/api/dto/` + `core/api/request/` |
| 判定纪律写在哪 | `core/api/service/Abstract*.java` |
| 某家的错误码怎么分类 | `core/api/<家>/service/`（如 `ExpediaBookingClassifier`） |
| 某家发什么 HTTP | `core/api/<家>/access/` |
| 限流 / 重试 | `core/api/common/access/BaseHttpAccess` |
| 报价句柄 | `core/api/common/offer/` |

### 2.2 目录与分层不吻合的四处

记在此处是为了让读代码的人不必自己踩一遍。**这四处都是历史沿革或疏漏，不是设计意图。**

1. **③ 适配层裂成两处。** 同一层、同一家住两个地方：
   `core/api/service/impl/ExpediaCheckPriceServiceImpl`（薄包装，转调下面那个）与
   `core/api/expedia/service/impl/ExpediaPriceServiceImpl`（真正的协议逻辑）。
   8 家的查价 / 验价薄包装全挤在共享的 `core/api/service/impl/`，厚逻辑各在自家包里。

2. **新写的下单 / 查单没走这个两段式。** `ExpediaBookingSyncServiceImpl` 与
   `ExpediaOrderQuerySyncServiceImpl` 直接在 `expedia/service/impl/` 继承模板，没有薄包装。
   **约定（新增能力照此办）**：不再加薄包装——它除了多一跳没带来什么。旧的 16 个薄包装
   （8 家 × 2 能力）应当逐步收敛掉，但那是纯重构，宜在一批发布完成后单独进行。

3. **② 契约层散在四处**：`dto/`、`request/`、`common/enums/`、`service/`（接口）。
   想通读一遍对外契约得跑四个目录。

4. **`adaptor` / `adapter` 拼写不一致**：`didatravel` 用 `adapter`，其余 6 家用 `adaptor`。

## 3. 对外端点与能力矩阵

| 端点 | 能力接口 | 已实现的供应商 |
|---|---|---|
| `POST /client/spa/price` | `ProductSyncService` | 8 家（expedia、didatravel、huitravel、travelConnect、aicHotels、ratehawk、meituan、FastpayHotels） |
| `POST /client/spa/check` | `CheckPriceSyncService` | 同上 8 家 |
| `POST /client/spa/booking` | `BookingSyncService` | **仅 expedia** |
| `POST /client/spa/order` | `OrderQuerySyncService` | **仅 expedia** |
| `POST /client/spa/cancel` | `CancelSyncService` | **无** |

即：查价验价八家齐，下单查单只有 Expedia，取消尚未实现。

### 3.1 路由靠 bean 名拼接

`SpaController.findSupplierService` 的做法是：

```
beanName = SupplierSourceEnum.getEnum(supplierId).getDesc() + 能力后缀
         = "expedia"                                        + "BookingSyncService"
```

然后到 Spring 容器里按名取 bean。**取不到就回报「该供应商不支持该操作」**，不抛异常。

这带来一个性质：**能力是隐式的**——上游只能通过实际调用来发现某家支不支持某能力，
没有可查询的能力清单。新增供应商时只要 bean 名对得上就自动接入，无需改动 ①。

> 已知缺口：没有能力发现端点。上游若要预先知道某供应商能否下单，目前只能试。

## 4. 网关究竟"网关"了什么

只做转发的中间层不创造价值，只是多一跳。本服务的价值集中在三件事上。

### 4.1 把含糊结果收敛成确定的态

三个枚举，同一条纪律的三个面：**不确定的事不许说成确定的**。

| 枚举 | 用于 | 第三态 | 为什么必须有第三态 |
|---|---|---|---|
| `BookingOutcome` | 下单 | `UNKNOWN` | 超时时供应商可能已成单。报失败→上游退款而房仍占着；报成功→承认一笔不存在的订单。两者都是资损 |
| `OrderPresence` | 查单 | `INDETERMINATE` | 「确实没这单」才允许重下。把「没查出来」当成「没有」→重复下单；反之→订单永久悬空 |
| `CheckPriceOutcome` | 验价 | `INDETERMINATE`（另有 `RATE_DEAD`） | 满房该劝退旅客，报价换代该重新查价，调用失败该重试——处置互相矛盾，不能塌成一态 |

各抽象模板（②）的兜底一律落到"不确定"那一态，且**判定"确定"的权力只交给 ③**——
只有适配层读得懂供应商在说什么。

### 4.2 把易腐令牌关在内部（OfferStore）

```
验价                                          下单
  │                                            │
  │ 供应商返回内部凭据                          │ 上游回传 offerId
  │ （Expedia 是 1486 字符的下单链接）           │ （24 字符）
  ↓                                            ↓
OfferStore.issue(supplierId, credentials) ──→ OfferStore.resolve(offerId)
  │  Redis: offer:<offerId>                    │  取回 credentials
  │  TTL = cache.offer.ttl-seconds (600)       │  取不回 → 确定性失败
  ↓                                            ↓
返回 offerId + offerTtlSeconds 给上游          用凭据向供应商下单
```

上游**原样存、原样回传、永不解析**。

两个要点：

- **凭据是按名取用的键值对，不是单个字符串。** 艺龙要七项全齐、飞猪的 `rateKey` 与
  `request_trace_id` 必须配对、clwy 要 `rateKey` 加 `rateplanId`。单个字符串装不下，
  接入方就只能自行发明编码，而编码规则只写在注释里就会漂移。
- **"验价与下单口径对不齐"这个问题在结构上消失了。** 凭据由同一份代码写入、同一份
  代码读出，不存在两个系统各按自己的规则拼 key 再期望拼出同一个值的余地。上游那条
  "验价端与下单端必须用同一份拼接规则"的纪律，在这里不需要存在。

### 4.3 把配额与重试收在一处

所有供应商 HTTP 调用都经 `BaseHttpAccess.access()`，它是**限流的唯一闸门**：
key 为 `供应商_接口`，QPS 配在 Nacos `ratelimit.qps`，热生效。重试次数由各 `*Access`
在构造时声明——**写操作必须为 0**（`CreateOrderAccess` 即 0），只读接口才允许重试
（`QueryOrderAccess` 为 1）。

## 5. 接一家新供应商要做什么

以接入某家为例，按层自下而上：

**第一步 · 通道层**　为每个要调的供应商接口写一个 `XxxAccess extends BaseHttpAccess`，
实现 `buildRequestUrl` / `request` / `parseResponse`。构造时声明供应商、接口枚举、
监控名与**重试次数**（下单类一律 0）。限流无需自己写。

**第二步 · 适配层**　按需要实现能力：

```
XxxProductSyncServiceImpl     extends AbstractProductSyncSupportService
XxxCheckPriceServiceImpl      extends AbstractCheckPriceSyncSupportService
XxxBookingSyncServiceImpl     extends AbstractBookingSyncSupportService
XxxOrderQuerySyncServiceImpl  extends AbstractOrderQuerySyncSupportService
XxxCancelServiceImpl          extends AbstractCancelSyncSupportService
```

bean 名必须是 `<SupplierSourceEnum.desc><能力后缀>`，否则 ① 路由不到。

**第三步 · 定义该家的凭据键名**　参照 `ExpediaOfferCredentials`：把键名集中在一个类里，
供验价（写入方）与下单（读取方）共同引用，而不是两边各写一个字面量。
验价时 `offerStore.issue(supplierId, Map.of(键, 值, …))`，下单时 `offer.credential(键)`。

**第四步 · 把该家的错误码映射到三态**　这是接入工作里最需要动脑的部分，也是最容易
出资损的地方。判据参照 `ExpediaBookingClassifier`，纪律见 §4.1：

- 只有确证**不会因重试而改变**的结果才可判"确定失败"（满房、售罄、参数非法、额度不足）
- 一切无法证明"请求未在供应商侧生效"的情形，一律判"不确定"
- 供应商明确的**产品级死码**必须判 `RATE_DEAD`，**绝不可折叠进"不确定"**

**第五步 · 在 Nacos 补该家的限流配额**　键为 `ratelimit.qps` 里的
`GLOBAL_LIMIT:<供应商>:<接口>`，样例见 `config/nacos/trip-booking-spa.yaml.example`。

**第六步 · 本地实跑**　按 PROJECT.md §2.2.1，合并前必须实际跑通所改链路。
编译与单测通过不构成验证。

## 6. rateplan：cursor 那个坑，我们做到哪了

### 6.1 坑长什么样

上游把**产品身份**与**易腐令牌**塞进了同一个字段（`md5#subSaleId`）：`md5` 是报价
分组指纹（比价用），`subSaleId` 是售卖单元 id。后果是 cursor 仓内 **13 处**独立拆解
（2 处 `lastIndexOf('#')`、11 处 `indexOf`/`split`），两套分隔符解析规则并存
（2026-08-14 复查修正；早先记为 4 处，低估了）。

叠加供应商侧的报价码轮换（汇智 ≤4h、dida 轮换即报 2005），造成"旧列表点击 + 分组已
过期"成为**常态流量**，进而长出 5 条钩子位置各异的救回补丁。

### 6.2 拆成两半看

| 半边 | 是什么 | 我们的状态 |
|---|---|---|
| **令牌** | 一次性、易腐、下单用 | ✅ 已由 OfferStore 收进网关（§4.2） |
| **身份** | 稳定、可重复解析、上游长期持有 | ❌ **尚未派生统一 `productKey`**（设计已定稿，见 `docs/product-identity.md`） |

> 2026-08-14 实测更正：Expedia 的 `rate.id` **不是易腐令牌** —— 沙箱实测同参数两次、
> 跨日期、跨天均不变；易腐的是验价 href 里的 `?token=` query（每次不同）。Expedia 属于
> "上游已自行分离身份与令牌"的形态。真正的缺口是：各家标识腐性不一（汇智/dida 的报价码
> 确实易腐），而我们没有统一的身份层。分供应商腐性证据见 `docs/product-identity.md` §4。

**原则：身份与令牌永不同字段。** 合在一个字段里，就会长出 `md5#subSaleId`。

### 6.3 已经做了的：验价分态

上游缓存列表、旅客几分钟后点进来，那份报价可能已经不在——不是因为 ID 被重铸
（见 §6.2 的实测更正），而是该卖法当日下架、满房、或供应商侧轮换了报价码。
原实现对四种情形一律返回 `null`，现已分开：

| 情形 | 现在回报 | 上游该怎么做 |
|---|---|---|
| 查价／验价调用失败 | `INDETERMINATE` | 稍后重试 |
| 所点报价不在当前响应中 | `RATE_DEAD` | **重新查价**（同房型往往仍有房） |
| 所选床型已不可选 | `RATE_DEAD` | 重新查价 |
| 供应商明确满房 | `SOLD_OUT` | 如实告知旅客 |

要紧的是最后一条纪律：`RATE_DEAD` 不可折叠进 `INDETERMINATE`。cursor 把艺龙的产品级
死码打成"无响应"，而"无响应"落进硬错误集合被数据库价兜底成"可订"，于是死产品反复
曝光、用户下单后在建单段暴死——丢的是真单。

### 6.4 还没做的：稳定身份与陈码重解析

- **稳定 `productKey`**：由 (supplier_code, 账号/渠道 profile, supplier_hotel_id,
  supplier_room_id, 餐食, 退改类, 占用) 派生的哈希。床型、价格、供应商报价码、自由文本
  一律不进键（成分规范见 `docs/product-identity.md` §1）。申报为稳定的供应商真码
  （如 Expedia `rate.id`、美团 `goodsId`）降级为解析快速通道（hint），不再充当身份。
- **陈码重解析（resolve）**：验价时若令牌已死，用 `productKey` 向供应商**实时现货**
  找等价报价重新验价，对上游无感。**硬门不可放宽——同房型 ID、同餐、退改不劣于**：
  cursor 实证过"只按最便宜救会把含早错配成不含早"（订单 49046202）；且只许打现货，
  cursor 实测重验旧码 0%、翻自家陈缓存 8%、睡等刷新 92% 白等。

这两件是一对：`productKey` 是重解析的依据，缺了它第二件无从落地。
完整规则（R-x.x 编号、腐性申报表、聚合边界）见 `docs/product-identity.md`。

## 7. 当前缺口一览

| 缺口 | 影响 |
|---|---|
| 取消（`CancelSyncService`）零实现 | 认证硬性要求 |
| 改单、通知服务未实现 | 认证要求 |
| 锁单（hold & resume）未使用 | `hold` 硬编码 false |
| 统一身份 `productKey` 未实现（§6.4，设计已定稿于 `docs/product-identity.md`） | 旧列表点击会得到 `RATE_DEAD` 而非自动救回 |
| 报价句柄成功后不作废 | 依赖 Expedia 侧 `affiliate_reference_id` 幂等兜底 |
| 无能力发现端点（§3.1） | 上游只能靠调用来发现支不支持 |
| 除 Expedia 外各家未做三态分类 | 它们尚无下单实现；实现前必须先补 §5 第四步 |
