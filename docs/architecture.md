# 架构与供应商接入

> **定位**：本服务是什么、分几层、接一家新供应商要做什么、以及网关究竟"网关"了什么。
> **配套**：职责边界（吃什么／不吃什么）见 [gateway-boundary.md](gateway-boundary.md)；
> 提交、分支、配置规范见 [../PROJECT.md](../PROJECT.md)。
> **准确性**：本文所述均于 2026-08-19 对照代码核实（能力矩阵以启动日志实跑核对）。改动契约或新增能力时须同步本文。

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

2026-08-15 六边形重构后,目录**按边界组织**(inbound=谁调我们,outbound=我们调谁):

```
com/trip/booking/spa/
├── bootstrap/                          装配(MybatisPlusConfig、NacosRuntimeConfig)
├── gateway/
│   ├── domain/                         ② 纯模型:键派生(product/)、申报(supplier/)、
│   │                                      三态枚举(booking/)、共用件(shared/)
│   ├── application/                    ② 能力接口+三态模板,按能力分包:
│   │   │                                  pricing/ checkprice/ booking/ order/ cancellation/
│   └── adapter/
│       ├── inbound/
│       │   ├── rest/                   ① SpaController · controller/ common/ dto/ request/
│       │   │   └── ops/                ① BackDoorController(网关侧运维后门,§3.8.9 落点)
│       │   └── scheduler/                 定时任务入口(刷价)
│       └── outbound/
│           ├── supplier/expedia/       ③ 按能力分子包:pricing/ checkprice/ booking/
│           │                              order/ cancellation/ content/(原 staticdata)
│           │                              公共件在 shared/(合同档案、签名、原始 bean)
│           └── state/                  ⑤ offer/(OfferStore) pricecache/(PriceCacheService 及实现) catalog/(建档
│                                          与刷价任务队列 mapper)
├── platform/                           ④+技术设施:http/(BaseHttpAccess=限流唯一闸门、
│                                       asynchttp) ratelimit/ redis/ observability/
│                                       mybatis/ util/ exception/
└── bff/                                浏览器验收 BFF,独立边界
```

按需定位:

| 想找 | 去哪 |
|---|---|
| 某端点入口 | `gateway/adapter/inbound/rest/controller/SpaController` |
| 对外契约长什么样 | `inbound/rest/dto/` + `inbound/rest/request/` |
| 判定纪律写在哪 | `gateway/application/<能力>/Abstract*.java` |
| 键派生/腐性申报 | `gateway/domain/product/` · `gateway/domain/supplier/` |
| Expedia 的错误码分类 | `outbound/supplier/expedia/booking/ExpediaBookingClassifier` |
| Expedia 发什么 HTTP | `outbound/supplier/expedia/<能力>/client/` |
| 限流 / 重试 | `platform/http/BaseHttpAccess` |
| 报价句柄 | `outbound/state/offer/` |

### 2.2 目录即边界（2026-08-15 六边形重构后）

目录结构自本次重构起**如实反映边界**,五层与目录的对应:

```
① 端点层  gateway/adapter/inbound/rest（ops/ 收纳 §3.8.9 后门端点）
② 契约层  gateway/domain（纯模型/枚举/键派生） + gateway/application（用例接口与三态模板）
③ 适配层  gateway/adapter/outbound/supplier/<家>（按能力分子包:pricing/checkprice/booking/order/cancellation/content,公共件在 shared/）
④ 通道层  platform/http（BaseHttpAccess、HttpUtils、asynchttp）+ platform/ratelimit
⑤ 状态层  gateway/adapter/outbound/state（offer/pricecache/catalog/dao）
其他      platform/*（纯技术设施）、bootstrap/（装配）、bff/（独立边界）
```

**legacy/ 已删除**（2026-08-18,10 模块 213 文件）:旧供应商隔离区完成历史使命——
生产 24h 日志零流量、外部 import 为零后整包移除（didatravel/huitravel/meituan/ratehawk/
travelconnect/aichotels/fastpay/inittimezone/placeholder/ops）。架构测试改为
LEGACY_mustStayDeleted 守"不复活":新供应商一律走 gateway 六边形结构,不得再建 legacy 目录。

**继承来的死设施已清除**（2026-08-19,53 文件）:legacy 删除后仍散落在各层的零引用残留
——微信公众号推送（`platform/util/wx/**` + `DingTalkUtils`）、四个无调用方的工具类、
`state/dao/` 整包（时区建档 `city_zone`、分销挂牌 `db_up_hotel`、供应商酒店清单
`supplier_hotel_id_list` 三条链的 mapper 与实体）、以及 `mybatis-config.xml` 中指向已删
`MeituanChangeTypeEnum` 的 typeHandler 与 `@MapperScan` 里的悬空包名。三张表的 DDL 仍
留在 `config/mysql/legacy-schema.sql` 作存量记录,但代码侧已无读写方。

> 教训:mapper XML 必须与其实体同批删除。MyBatis 在构建 `SqlSessionFactory` 时解析
> `classpath*:/mapper/**/*.xml` 的**每一个**文件并解析其 `resultMap` 类名,残留一个引用
> 已删实体的 XML 会导致**启动硬失败**（`ClassNotFoundException` → `sqlSessionFactory`
> 创建失败),而编译与单测一概发现不了——本次删除即因构建产物中残留旧 XML 复现过一次。

**新旧词汇对照**（第一拍只搬未改名,第二拍随 cursor 迁移逐能力演进）:

| 旧 | 新位置 | 说明 |
|---|---|---|
| `core/api/service` 契约接口+三态模板 | `gateway/application/<能力>/` | 名称暂留 *SyncService/Abstract*,第二拍演进为 Provider/UseCase |
| `core/api/service/impl` 16 个薄壳 | Expedia 的进自家能力包;其余曾进 legacy(2026-08-18 随包删除) | 第二拍由 CapabilityRegistry 取代字符串拼 bean 名后消亡 |
| `core/api/common/identity·enums·offer` | `gateway/domain/*` 与 `state/offer` | — |
| `core/api/dto·request` | `gateway/adapter/inbound/rest/dto·request` | 对外 JSON 契约,第一拍原样直通 |
| `core/api/expedia/**` | `outbound/supplier/expedia/<能力>/` | staticdata→content |
| `core/util·redis·ratelimit·monitor·exception` | `platform/*` | — |

历史遗留说明:旧 §2.2 记录的"四处不吻合"(薄壳分裂、契约散落四处、adaptor/adapter 拼写)
中,前两处已由本次重构消除;第三处(adaptor 拼写)曾保留在 legacy 内,已随 2026-08-18 legacy 删除而消亡——四处全部清零。

## 3. 对外端点与能力矩阵

| 端点 | 能力接口 | 已实现的供应商 |
|---|---|---|
| `POST /client/spa/price` | `ProductSyncService` | expedia、elong、fliggy |
| `POST /client/spa/check` | `CheckPriceSyncService` | expedia、elong、fliggy |
| `POST /client/spa/booking` | `BookingSyncService` | expedia、elong、fliggy |
| `POST /client/spa/order` | `OrderQuerySyncService` | expedia、elong、fliggy |
| `POST /client/spa/cancel` | `CancelSyncService` | expedia、elong、fliggy |

即：三家供应商五个能力全在册。启动日志的能力矩阵可核对（2026-08-26 本地实跑）：

```
能力注册: supplier=expedia(10005) capabilities=[PRICING, CHECK_PRICE, BOOKING, ORDER_QUERY, CANCELLATION]
能力注册: supplier=elong(10010)   capabilities=[PRICING, CHECK_PRICE, BOOKING, ORDER_QUERY, CANCELLATION]
能力注册: supplier=fliggy(10015)  capabilities=[PRICING, CHECK_PRICE, BOOKING, ORDER_QUERY, CANCELLATION]
```

> 飞猪状态（2026-08-26）：五能力在册但**未放量**——查价段已从真实入口穿透验证
> （本机直连 eco.taobao.com，签名/session/信封真验，"hids is empty"=下架语义闭环），
> 验价/下单/查单/取消四段仅有单测与手写报文样本作证，真单验证与必测清单见
> [fliggy/distribution-api.md](fliggy/distribution-api.md) §9。生产 Nacos 的
> FLIGGY 限流键与 GitHub secrets 的 FLIGGY_* 均未配置，放量前须补齐。

`SupplierSourceEnum` 中其余 7 家（travelConnect、aicHotels、didatravel、huitravel、
FastpayHotels、ratehawk、meituan）只保留供应商编码，无任何实现——它们的适配代码已随
2026-08-18 的 `legacy/` 整包删除而消亡，矩阵中一律为空。

### 3.1 路由与能力发现（SupplierCapabilityRegistry）

路由收口在 `gateway/application/routing/SupplierCapabilityRegistry`：启动时按
`SupplierSourceEnum × Capability` 探测容器建好**不可变矩阵**并逐行打印
（`能力注册: supplier=expedia(10005) capabilities=[PRICING, ...]`——哪家缺哪个
能力,启动日志一眼可见、可检索）。bean 名约定（`<供应商desc><能力后缀>`）仍是
底层接线方式,但拼接只存在于 Registry 一处,SpaController 只按枚举查表：

```java
capabilityRegistry.find(supplierId, Capability.BOOKING, BookingSyncService.class)
```

查不到返回 null → 上游收到「该供应商不支持该操作」,不抛异常（与历史行为一致）。

**能力发现端点**：`GET /client/spa/capabilities` 返回供应商 × 能力矩阵——上游可以
预先查询,不必再靠试探调用（此前的在册缺口,2026-08-15 关闭）。新增供应商时
bean 名对得上就自动进矩阵,无需改动 ①。

## 4. 网关究竟"网关"了什么

只做转发的中间层不创造价值，只是多一跳。本服务的价值集中在三件事上。

### 4.1 把含糊结果收敛成确定的态

四个枚举，同一条纪律的四个面：**不确定的事不许说成确定的**。

| 枚举 | 用于 | 第三态 | 为什么必须有第三态 |
|---|---|---|---|
| `PricingOutcome` | 查价 | `INDETERMINATE` | 「供应商说这儿没房」该劝退旅客并停止重试，「我们没问出来」该稍后重试。塌成一态时上游只能猜——2026-08-20 前本服务查价出口对两者一律回 `result is null` |
| `BookingOutcome` | 下单 | `UNKNOWN` | 超时时供应商可能已成单。报失败→上游退款而房仍占着；报成功→承认一笔不存在的订单。两者都是资损 |
| `OrderPresence` | 查单 | `INDETERMINATE` | 「确实没这单」才允许重下。把「没查出来」当成「没有」→重复下单；反之→订单永久悬空 |
| `CheckPriceOutcome` | 验价 | `INDETERMINATE`（另有 `RATE_DEAD`） | 满房该劝退旅客，报价换代该重新查价，调用失败该重试——处置互相矛盾，不能塌成一态 |

各抽象模板（②）的兜底一律落到"不确定"那一态，且**判定"确定"的权力只交给 ③**——
只有适配层读得懂供应商在说什么。

验价模板另守一条**应答自洽**：回 `BOOKABLE` 必须同时给出 `offerId` 与正的
`offerTtlSeconds`，缺任一即降为 `INDETERMINATE`。理由是上游拿"无句柄的可订"
无从下单、拿"无时效的句柄"无从判断该直接下单还是重新验价；而它属于形状约束、
与供应商无关，故收在 ② 而不是各家实现里。现网两家的可订路径本就必带这两项
（拿不到句柄时它们自己就回不确定），这道关卡是给下一家接入时用的。

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
key 为 `GLOBAL_LIMIT:<供应商>:<接口>[:<用途>]`（两级：接口桶＝对供应商的承诺，用途桶＝我方内部分配），QPS 配在 Nacos `ratelimit.qps`，热生效。

**限流器只有一种实现：跨实例的 Redisson 令牌桶。** 2026-08-25 删掉了 `local`(Guava)/`distributed` 这个开关——供应商配额是账号或接口级的，与我们部署几个实例无关；Guava 在 JVM 内计数，单实例时"本机"恰好等于"全局"是侥幸而非设计，加第二台就是对供应商双倍流量，且不会有任何报错。同一条刷价路径上的分布式锁早就是跨实例的（F-2.3），两处前提本就不一致。代价是每次扣格一次 Redis 往返（Lua，同 VPC 亚毫秒）；Redis 不可用时前台快速失败、后台阻塞等待——这是刻意取舍：放行意味着对供应商无限流，可能招致封号，而拒绝只是这段时间不刷价。价格缓存本就在同一个 Redis 上，故不算新增单点。

速率表达为「每 X 毫秒 1 个许可」而非「每秒 N 个」：Redisson 的 rate 是整数（0.5 QPS 会被截成 0，永久阻塞），且窗口内先到先得——配 `(12,1秒)` 时 12 个请求可能挤在几十毫秒内打出去，而艺龙按秒限、我方实测 11.25 QPS 就已每小时数百次频控。固定 1 许可 + 毫秒级窗口是用 Redisson 逼近匀速放行的办法，也顺带让小数配额不再被截断。

重试次数由各 `*Access`
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

查价转换循环里**每个丢弃报价的分支必须计 `QUOTE_DROPPED`**（带 stage/reason），要么注释说明
为什么不算丢弃——被扔的报价只有转换代码自己看得见，模板无从代劳；不计数它们就无声消失，
「出报率掉了丢在哪」只能靠 grep 和猜。守护测试从包路径自动查账
（`MetricVocabularyArchRulesTest.O45`），漏计构建即红。`pricing_supplier_query`（一次实时
查价一笔）不用管，查价模板统一打（O-4.3），实现方**不得**自行再打。

**第二步半 · 申报**　`SupplierIdentityProfile` 补该家三行申报（R-4.1，含证据），外加
凭据续期档位（`CredentialRenewal`：每请求现签 / 可自续 / 只能人工）。申报 `HUMAN_ONLY`
的家必须同时注册 `CredentialExpiry` bean 供到期时间——启动即校验，缺了拒绝启动；
剩余天数指标与 14 天告警（`spa.yml` SupplierCredentialExpiringSoon）自动生效。

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

**第六步 · 建档落库：稳定信息进库，Redis 只留易腐**　按 R-2.6。查价响应里的
产品事实（productKey、房型、餐食、退改类、占用、房型名）写目录/档案表；供应商的
易腐报价码只进 `supplier_quote_hint` 列（语义=解析快速通道，非身份）。Redis 只承载
当轮价格、易腐码与 OfferStore 句柄，靠 TTL 自然消亡。

判据一句话：**「供应商明天换一批报价码，这条信息还对吗？」对 → MySQL；错 → Redis。**

数据源不必额外调接口——刷价本就在查价，写缓存的同时顺手 upsert 即可（Expedia 的
`ExpediaProductMappingService` 是先例，只是它为建档另查了一遍价）。跳过这步的代价见
R-2.6：Redis 随覆盖面线性膨胀、TTL 无从取值、缓存被误当事实源。

**第七步 · 本地实跑**　按 PROJECT.md §2.2.1，合并前必须实际跑通所改链路。
编译与单测通过不构成验证。**且必须从真实入口穿透验证**：2026-08-19 的教训是
只验两端各自可用、没验请求能从入口流到它们，生产上出价发了 8.3 万条报价而验价
入口零调用（详见 price-refresh.md 与当日日报）。

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
| **身份** | 稳定、可重复解析、上游长期持有 | ✅ 已派生统一 `productKey`（`domain/product/ProductKeyFactory`，两家均已接线） |

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

### 6.4 已落地的：稳定身份与陈码重解析

- **稳定 `productKey`**（已落地）：由 (supplier_code, 账号/渠道 profile, supplier_hotel_id,
  supplier_room_id, 餐食, 退改类, 占用) 派生的 sha256。床型、价格、供应商报价码、自由文本
  一律不进键（成分规范见 `docs/product-identity.md` §1）。申报为稳定的供应商真码
  （如 Expedia `rate.id`）降级为解析快速通道（hint），不再充当身份。
  实现在 `domain/product/ProductKeyFactory`，两家各有一个 `*ProductKeyDeriver` 负责
  归一餐食与退改后调它；查价响应里 `productId`（易腐报价码）与 `productKey`（稳定身份）
  分列两字段。
- **陈码重解析（resolve）**（已落地，默认关闭）：验价时若令牌已死，用 `productKey` 向
  供应商**实时现货**找等价报价重新验价，对上游无感。**硬门不可放宽——同房型 ID、同餐、
  退改不劣于**：cursor 实证过"只按最便宜救会把含早错配成不含早"（订单 49046202）；
  且只许打现货，cursor 实测重验旧码 0%、翻自家陈缓存 8%、睡等刷新 92% 白等。
  判定收口在 `domain/product/ResolveGate`（比例门 `resolve-price-tolerance` ∧
  绝对帽 `resolve-price-cap-cents` 双门同时成立才换）。

闸口：`supplier.expedia.resolve-enabled` / `supplier.elong.resolve-enabled`，
**两者代码默认与 Nacos 现值均为 `false`** ——即能力在册但尚未放量，关闸期间陈码仍走
`RATE_DEAD` 正门（拒绝时打 `闸口 ... 关闭，拒绝按 productKey 自动换票` 日志）。

完整规则（R-x.x 编号、腐性申报表、聚合边界）见 `docs/product-identity.md`。

## 7. 当前缺口一览

| 缺口 | 影响 |
|---|---|
| 改单、通知服务未实现 | 认证要求 |
| 锁单（hold & resume）未使用 | `hold` 在 `ExpediaBookingSyncServiceImpl` 中硬编码 false |
| 陈码重解析（resolve）两家均未放量 | 闸口默认关闭（§6.4），旧列表点击目前仍得到 `RATE_DEAD` 而非自动救回 |
| 新接第三家时须先补三态分类 | 判据见 §5 第四步；错判会直接造成资损 |
