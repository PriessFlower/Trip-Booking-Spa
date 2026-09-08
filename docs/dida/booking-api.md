# 道旅（DidaTravel）接入实录

> **定位**：本仓道旅适配层的协议事实、判据与实测证据。设计原则见
> [../architecture.md](../architecture.md) §5（接一家新供应商要做什么）与
> [../product-identity.md](../product-identity.md)。
> **依据**：官方文档 `https://apidoc.didatravel.com/zh/`，全文引用均标注查阅日期；
> 文档没写而实测得到的，一律写明「实测」与日期、样本量（PROJECT.md §4.2.5）。
> **本批范围**（2026-09-08）：查价、验价、刷价、建档。下单／查单／取消留待二批。

## 1. 端点与鉴权

| 用途 | 端点 | 本仓落点 |
|---|---|---|
| 查价 | `POST https://api.didatravel.com/api/rate/pricesearch?$format=json` | `pricing/client/PriceSearchAccess` |
| 验价 | `POST https://api.didatravel.com/api/rate/PriceConfirm?$format=json` | `checkprice/client/PriceConfirmAccess` |
| 下单（二批） | `POST /api/booking/HotelBookingConfirm?$format=json` | — |
| 查单（二批） | `POST /api/booking/HotelBookingSearch?$format=json` | — |
| 预取消（二批） | `POST /api/booking/HotelBookingCancel?$format=json` | — |
| 确认取消（二批） | `POST /api/booking/HotelBookingCancelConfirm?$format=json` | — |

**鉴权**：每个请求体自带 `Header.ClientID` 与 `Header.LicenseKey`，无签名、无会话、无到期
（故 `CredentialRenewal.STATELESS`）。凭据经 `DIDA_CLIENT_ID` / `DIDA_LICENSE_KEY` 注入。

**两条硬约束**（都不在文档里，2026-09-08 实测）：

- **必须带 `Accept-Encoding: gzip`**。不带直接被拒：
  `{"Error":{"Code":"-2","Message":"gzip is required, please add Accept-Encoding: gzip in your request header."}}`
- **出网 IP 必须在道旅白名单内**，否则 `{"Error":{"Code":"2017","Message":"Invalid ip/signature"}}`。
  本机（美国出口）被拒；腾讯云 `trip-offline` 与阿里云 `tg_server1` 已在白名单。
  本机跑真链路的办法：`ssh -D 1080` 到白名单机，JVM 加
  `-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1080`；本机若有 TUN 代理污染 DNS
  （解析出 198.18.x），再加 `-Djdk.net.hosts.file=<写死真实 IP 的 hosts 文件>`。

**没有沙箱**：官方 price-search 注 15（2026-09-08 查阅）写明测试账号 `DidaApiTestID` 已废除，
要测试须请客户经理另开专属测试账号。故 `supplier.dida.url-host` 的兜底即生产端点。

## 2. 错误码 → 三态

码表出处：`information-hub/api-error-code`（2026-09-08 查阅）。落点：查价在
`DidaPriceServiceImpl#toPricingResult`，验价在 `#interpretConfirmResponse`，单测
`DidaPricingOutcomeTest` / `DidaConfirmOutcomeTest` 逐码钉住。

| 码 | 官方文案 | 查价 | 验价 |
|---|---|---|---|
| 2005 | 没有库存 | `NO_INVENTORY` | `SOLD_OUT` |
| 2006 | 此价格计划失效 | `NO_INVENTORY` | `RATE_DEAD` |
| 2020 | RatePlanID不正确 | — | `RATE_DEAD` |
| 2029 | 酒店停止售卖 | `NO_INVENTORY` | `RATE_DEAD` |
| 2030 | 价格不可用 | `NO_INVENTORY` | `RATE_DEAD` |
| 2017 / 2019 | 机构信息验证失败／请求被禁止 | `INDETERMINATE` | `INDETERMINATE`（error 级日志，需人工） |
| 2022 | 超过流量限制 | `INDETERMINATE` | `INDETERMINATE`（并计 `throttled`） |
| 其余与表外码 | — | `INDETERMINATE` | `INDETERMINATE` |

两个形态上的坑：

- **HTTP 恒 200**，成败只看 `Success` / `Error` 两个互斥节点。
- **空 `HotelList` 是成功响应**，不是错误：拿一个不存在的酒店 id 去查，回的是
  `Success` + 空列表（2026-09-08 实测）。它与「这家这天满房」同形，本仓一并落
  `NO_INVENTORY`——对上游是同一件事，且这样缓存里的僵尸价才会被清（B7）。

## 3. 身份与腐性（申报见 `SupplierIdentityProfile.DIDA`）

- **`RoomTypeID` 稳定**：2026-09-08 实测 36 家有报价的酒店、225 个不同的 `RoomTypeID`，
  **全部**能在静态内容接口（`static-api.didatravel.com/api/v1/hotel/details` 的
  `rooms[].id`）里找到，未命中 0 个。故它进 productKey，也是房型级目录的锚。
- **`RatePlanID` 易腐，且腐得极快**：同店同参数间隔 **3 秒** 重查，所点报价码已不在响应中
  （首查 24 条与复查 116 条只有 12 条同码）。故它只进 OfferStore、禁止落库，验价一律现取
  现验，且 resolve（按 productKey 换等价票）对道旅不是可选项而是常态路径。
- **`ReferenceNo` 有效期 2 小时**（错误码 3006），下单唯一入口；句柄 TTL 帽取 30 分钟。

## 4. 查价的两个口径坑

**① 多店 + 实时报价会被降级**（2026-09-08 生产实测，腾讯云 trip-offline 直打）。

样本：从道旅可卖清单里等距抽 **60 家酒店**（跨国家、非同城），跑 **3 个住期**
（T+3 / T+21 / T+60，各 1 晚、2 成人、CNY/CN），共 180 个「酒店×住期」，其中单店实时
确有报价的 50 个。基准 = 逐店实时；对照 = 同样 10 家一批，实时档与缓存档各一次。

| 住期 | 有货酒店 | 逐店实时（基准） | 10 家一批 · 实时 | 10 家一批 · 缓存 | 实时档整家消失 |
|---|---|---|---|---|---|
| T+3 | 17 | 354 条 | 114 条（32%） | 354 条（100%） | 10/17（59%） |
| T+21 | 17 | 394 条 | 129 条（33%） | 393 条（100%） | 11/17（65%） |
| T+60 | 16 | 357 条 | 136 条（38%） | 353 条（99%） | 9/16（56%） |
| **合计** | **50** | **1105 条** | **379 条（34%）** | **1100 条（99.5%）** | **30/50（60%）** |

最低价方向**从无例外**：混批实时里 18 家的最低价比逐店实时贵、0 家更便宜，中位偏高
3.6%~4.7%；混批缓存则 47/50 家与逐店实时**完全相等**（另 3 家小幅偏高）。

**机制不是"批量条数上限"**：同一批 10 家关掉 `IsRealTime` 就全回来了。批量从 2 开始就退化
（酒店 528：单店 116 条 → 批量 2/3/5/10 一律 39 条），不随批量大小递减。
按批内位置看实时档的命中率有前高后低的迹象（第 1 位 6/7，第 10 位 0/6），与"实时聚合按列表
顺序做、做到哪算哪"相符，但每格样本只有个位数，**不足以定论**。

单店那一档 `IsRealTime` true / false 的差别很小：8 家里 7 家逐条相同，1 家实时多 3 条
（80 vs 77）。故本仓取**逐店 + 实时**，是最全的一档。若将来为省调用数要合批，
必须同时关掉 `IsRealTime`——只改批量就会丢货。

> 顺带的观察（不构成结论）：从非实时那一档拿到的 4 条最低价报价，逐条打 PriceConfirm
> 验价，4/4 价格与查价完全一致。这只说明"此刻缓存价没有陈"，不足以证明它长期可靠。

**cursor 侧现状**：`DidaTravelPriceFetchService#fetchBatchHotelPriceFromDida` 把
`IsRealTime` 写死 true，而生产 Redis 的 `dida:flush:batch-size` 当前取值为 **10**
（2026-09-08 读取），即正好踩在退化组合上。代码默认值本是 1（注释引用过一次
「fix batchsize to 1」的提交），是后来灰度拉到 10 的（动机写着"10× 降调用数缓解账号级限流"）。
在线验价的重解析路径用的是 `Collections.singletonList`，不受影响。

**文档自己怎么说的**（官方 price-search，2026-09-08 查阅）：同一个端点靠参数分三种查法——
`LowestPriceOnly=true` 是**最低价查询**、`IsRealTime.Value=false` 是**缓存查询**
（原文：「拉取多酒店缓存报价，当多酒店搜索报价时推荐使用缓存(最多支持50家酒店)」）、
`IsRealTime.Value=true` 是**实时查询**（原文只有「拉取多酒店实时报价」，没写家数上限）。
选哪种，文档给的是一句速度与准确度的取舍：

> 当使用多酒店查询报价时，如果贵公司更在意返回的**速度**，建议多酒店使用**缓存**查价，
> 如果贵公司更在意返回的价格和库存的**准确度**，建议多酒店也使用实时查价。

**实测与这句话冲突**：多酒店实时不是"更准"，是**更少**——同一批 10 家，实时只回 105 条、
漏掉 4 家，缓存回满 280 条。按 PROJECT.md §4.2.4「文档与实测冲突时以实测为准，两者都留痕」，
本仓按实测走（逐店 + 实时），并把这条列进第 9 节待向客户经理确认。
超时不是解释：官方超时页写价格搜索默认 5 秒，而那次批量实时响应只用了 0.36 秒，远没到超时。

**关掉实时的硬代价：缓存档完全无视占用**。官方注 2 写着「默认情况下，Lowest price search 和
Cache rate search 将返回基于 2 人的价格」，2026-09-08 实测坐实（酒店 528 与 5279 各测四档）：

| 请求占用 | 实时档回的最低价 | 缓存档回的最低价 | 缓存档响应里声明的占用 |
|---|---|---|---|
| 1 成人 | 534 | 610 | 2 成人 0 儿童 |
| 2 成人 | 610 | 610 | 2 成人 0 儿童 |
| 3 成人 | 801 | 610 | 2 成人 0 儿童 |
| 2 成人 1 儿童(5 岁) | 758 | 610 | 2 成人 0 儿童 |

即缓存档只有「2 人」这一档是对的。刷价维度若只有 2 人，合批+缓存与逐店实时等值；一旦要刷
1 人、3 人或带儿童档，缓存档会把 2 人价当成那一档的价存下来。验价链路必须按客人真实占用，
故只能实时。

**② `TotalPrice` 两个接口口径不同**（官方 price-search / price-confirm）：
pricesearch 是**单间**住期总价（搜 3 间需自行 ×3），PriceConfirm 是**全部房间**的总价。
本仓缓存与出价按单间口径存，验价那一档直接用 PriceConfirm 的总价作 `salePrice`。

`IncludedFeeList` 的税费**已含在 TotalPrice 内**（官方明示「不要再把此税费列表里的价格跟
TotalPrice 运算」），故 `totalTaxes` 报 0，不做加减。`InventoryCount` 官方自己写着
「仅供参考，不准的」，故只在查价响应里原样透出，验价那一档**不报** `remainRoomNum`。

## 4.5 耗时：慢的是验价，不是查价

2026-09-08 实测（腾讯云 trip-offline → 道旅；**网络路径与 cursor 的阿里云张家口不同，绝对值
不可直接比，只看量级与相对关系**）：

| 调用 | p50 | p90 | 最大 |
|---|---|---|---|
| 查价 单店实时 | 736ms | 857ms | 857ms |
| 查价 单店缓存 | 697ms | 965ms | 965ms |
| 查价 10 家实时 | 454ms | 831ms | 831ms |
| 查价 10 家缓存 | 577ms | 716ms | 716ms |
| **验价 PriceConfirm** | **389~1869ms** | **~2.7~3.0s** | **2980ms** |

两条结论：

1. **查价从来不是超时来源**——半秒级，而且混批比单店还快（少发几次请求）。所以第 4 节那个
   丢货问题与超时**没有直接因果**。
2. **验价才是**：秒级且方差大，与 cursor 生产观测同量级（他们记录 Dida 验价
   `p50=1283ms / p90=2219ms / p95=2570ms`，为此单独给道旅配了 2300ms 预算，
   见其 `backend/specs/2026-07-18-dida-timeout-budget.md`）。`PreBook=true` 不额外变慢
   （实测 852/860ms）。

丢货与超时之间存在一条**间接**链，值得在 cursor 那边验证：报价丢了六成 → 曝光出去的报价更
容易已经失效 → 验价撞 `2005` → 触发按 productKey 的重解析（再查一次现货 + 再验一次价，
而按其 spec 这些**共用同一个 deadline**）→ 预算耗尽即记成超时。本仓没有量过这条链，
要坐实得看 cursor 侧 `RATE_PLAN_STALE_2005` 与超时归因的相关性。

**对本仓的直接影响**：曝光档只打一次查价（0.5~0.9s），塞得进上游 1200ms 那档预算；
下单前档要「查价 + 验价」≈ 2s 起步、p90 可能 3s 以上，**只能放在下单前那档的秒级预算里**，
不要指望它进 1200ms。这与艺龙"完整验价约 4.6s、塞不进渠道验价预算"的结论同形。

## 4.6 验价（PriceConfirm）文档明说的四件事

原文均出自官方 booking-api/price-confirm（2026-09-08 查阅）：

1. **它是房型级实时报价，且以它为准**：「如果 PriceConfirm 取消政策 (mealtype/rate/RoomName/
   available 等) 与 PriceSearch 不一致，请使用 priceconfirm 信息作为最终信息。」
   本仓已照此实现：退改与逐晚价一律以验价时点那份为准，解析不出才回落查价那份。
2. **官方给的耗时预期**：「一般情况下，Dida 会在 2 秒内返回验价结果, 最多不超过 20 秒。
   如果超过 20 秒还未收到结果，建议重发一次请求。」与 §4.5 实测吻合（实测最大见到 4.0 秒）。
3. **只验价不下单时不要开 PreBook**：「如果客人只是需要验价，并没有进入订单创建的流程，
   不需要设置 PreBook 为 true，这样能有更好的性能表现，减少超时的情况。」
   本仓的曝光档根本不打验价，下单前档必须签句柄故必须 PreBook=true——两档都合规。
4. **`ReferenceNo` 的有效期，接口页没写**。「2 小时」这个数只出现在错误码表 3006 的文案里
   （「订单参考号过期了。订单参考号有效时长为2小时」）。

关于第 4 条要说清楚一件事：**那 2 小时是"号自己最长能活多久"，不是"房和价被锁住 2 小时"**。
道旅自己的订单族错误码里就有 `3015 无房或变价`、`3001 订单信息不正确`——都是拿着尚未过期的
号去下单仍然失败的情形；而报价码本身实测 3 秒即换代。**当天入住**尤其不能指望：2026-09-08
北京时间 16:15 实测 T+0 仍可查价并成功拿到 ReferenceNo（3 家里 2 家有货），但一个晚上 22 点
签出的号显然跨不过酒店的当日截止时间，而这一点无法在不真下单的前提下验证。

故本仓不依赖它：句柄 TTL 上限 30 分钟，实际以 OfferStore 的 TTL（生产 600 秒）为准，
过期一律凭 productKey 现取现验。

## 5. 餐食：只认两种有实证的取值

官方注 8 要求用 `MealType` + `MealAmount`（`BreakfastType` 已过时），**但没有给 MealType 的
取值表**。2026-09-08 实测 606 条报价，出现四种组合 `(BreakfastType, MealType, MealAmount)`：

| 组合 | 条数 | 样例 |
|---|---|---|
| (1, 1, 0) | 344 | 无餐 |
| (2, 2, 2) | 210 | 含 2 份早餐 |
| (2, 3, 2) | 32 | 未知 |
| (2, 7, 2) | 20 | 日式「1泊2食」（一晚含两餐） |

关键在最后一行：**MealType=7 的 `BreakfastType` 也是 2**——照 `BreakfastType` 判会把
「早+晚」说成「仅含早」，那是卖错。故本仓只认 `MealType=1 且份数 0`（确定无餐）与
`MealType=2 且份数>0`（确定含早），其余一律 UNKNOWN：照常可售，但不进产品目录（R-5.4）。

**待办**：向道旅要 `MealType` 取值表，拿到后收窄 UNKNOWN（届时 productKey 会随成分变化而
换代，见 `productkey-change-needs-manual-recatalog` 的教训，要一并安排重建档）。

## 6. 退改：起始点列表 → 分段

道旅给的是「**从 FromDate 起**取消收 Amount」的起始点列表，FromDate 之前免费，
时刻是**北京时间**（官方 price-search 字段说明，2026-09-08 查阅）。本仓翻成分段：

- 段 i 的截止时刻 = 下一条的 `FromDate`；末段无截止（落 `before` 下限 25，表示"此后一直"）
- 首条 `Amount>0` 时补一段免费窗，其截止时刻即首条 `FromDate`
- 金额币种 = 本次报价币种（`supplier.dida.currency`，默认 CNY）——那张表只给数值不给币种
- 政策列表缺席即 UNKNOWN，**不兜成"不可退"也不兜成"免费"**（cursor 兜成不可退是防赔款的
  故意设计，SPA 用 UNKNOWN 达成同等防护而不说谎）
- 过期段一律滤掉（`CancelClassifier.liveSegments`）：作废的免费窗不能对外承诺

## 7. 国籍：填客人真实国籍，不填酒店所在国

官方 price-search 注 14：「请在 Nationality 中填入客人输入的真实国籍，以免后续到店出现
争议单」。上游契约不带国籍字段，故配置化为 `supplier.dida.nationality`，默认 `CN`
（我方客源国）。**不照 cursor 的做法填酒店所在国**——那正是文档点名要避免的争议单来源。
代价是可售集合与价格可能与 cursor 侧不同，比价时要记得这一条。

## 8. 非即时确认（on-request）

本批只卖即时确认：请求一律 `IsNeedOnRequest=false`，响应里万一仍带 `IsOnRequest=true`
的报价，在转换与找票两处都被排除并计入 `quote_dropped{reason="on_request"}`。
接 on-request 需要下单链路先就位（订单状态另有【6】OnRequest 档，120 分钟内出终态）。

## 9. 未解决 / 待确认

| 事项 | 现状 |
|---|---|
| `MealType` 取值表 | 文档无，已按实证收窄；待向客户经理索取 |
| QPS 配额 | 文档无（缓存建议页与 FAQ 均无数值）；现取自律值，见 `config/supplier-capability/dida.yaml` |
| `Metadata` | 官方标必填，但本账号 pricesearch **不下发**该字段；实测不传照样验价成功。需客户经理开通后再回传 |
| 多间（`NumOfRooms>1`） | 验价请求已按间数逐间列 `OccupancyDetails`，但**未做多间真实验证**；逐晚明细是否随间数变化未确认 |
| 与 cursor 的额度叠加 | 同一个 ClientID 两边共用，放量前须先确认 cursor 侧速率 |
| `ReferenceNo` 的真实寿命 | 接口页未写；错误码 3006 说 2 小时。当天入住能撑多久、有没有当日截止，都要问客户经理 |
| 多店实时为何丢货 | 文档反而推荐"多酒店要准确度就用实时"，与实测相反，机制未知，要问客户经理 |
| 静态内容接入 | `static-api.didatravel.com` 另有一套端点与 Basic 鉴权，本仓未接；刷价清单目前靠离线抓取播种 |
