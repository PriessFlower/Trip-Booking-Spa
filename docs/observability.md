# 观测规范（指标 / 漏斗 / 现场 / 证据 / 诊断）

**定位**：观测的实现规范。实现与评审按编号引用（如 O-2.1）。MUST=违反即 bug；SHOULD=偏离须书面说明理由。
**要解决的两个问题**（2026-08-21 与周涛定调，本文全部条款服务于此）：

1. **怎么让图表让人看懂**——分业务分系统，指标做全，每个指标和每个取值都自带解释。看图的人不必问人、不必翻代码。
2. **怎么让指标准确不乱**——一个概念只有一个说法，一件事只数一次。**指标宁缺毋滥**（与日志相反：日志宁多勿少），目的是出事能顺着数字查到根因，而不是靠 `grep` 和猜。

**为什么现在立**：本仓埋点 32 处、指标名 28 个，尚在“数得清”的窗口内。cursor 的 324 种日志前缀、234 个端点不是某次错误决定的产物，而是每次“顺手加一个”累积而成（PROJECT.md §3.9.2、§3.9.3）；等失控后再补，成本是清理而不是约定。
**正本声明**：O 规则正文只存本文一份（PROJECT.md §5.1.3）；PROJECT.md §3.9 是强制条款的短表述，本文是其可执行口径，冲突以 PROJECT.md 为准。
**前置**：PROJECT.md §3.9（观测与运维端点）、§6（日志规范）。
**供应商范围**：在产只有 **elong、expedia** 两家。七家遗留供应商已于 2026-08-21 从代码删除（预研结论留在 `product-identity.md` §4）；条款不为未接入的供应商预留设计。

## 0. 术语

| 术语 | 定义 |
|---|---|
| **埋点名** | 传给 `Monitor.record*` 的名字，**不含后缀**——`Monitor` 追加 `_count`/`_time`/`_value`，Prometheus 再给 counter 追加 `_total` |
| **序列** | 指标名 + 一组标签值。序列数（基数）是内存与查询成本的直接来源 |
| **可加** | 按某标签求和后等于事件总数。不可加的计数算不出比率，故不能做告警分母 |
| **stage** | 漏斗阶段：请求进入 → 出报价之间的固定环节 |
| **reason** | 某阶段“没出报价”的成因，取值必须枚举化 |
| **静默丢弃** | 业务上丢了一条报价，却既无日志也无指标的分支 |

---

# 第一部分：让人看得懂

## 1. 哪些数该进指标

- **O-1.1 (MUST)** 新增观测前按 PROJECT.md §3.9.1 的四问判载体。判不出来的默认“指标 + 现场”都落，不得因犹豫只打一行日志了事。
- **O-1.2 (MUST)** **要跨天比较的业务数字必须生在指标通道**，不得只存在于日志文本。日志窗口有物理上限（生产实测 40 万行仅覆盖 44 分钟，发版即清空），靠 `grep` 现算的数无法告警、无法看趋势。
- **O-1.3 (MUST)** **代码里已经算出来的计数，禁止只写进日志**。这是 O-1.2 最常见的违反形态，现存四处：`ElongPriceServiceImpl` 转换完成日志的五个数（产品总数、在售出报、三类跳过原因）、`ExpediaProductMappingService` 建档的 `upserted`/`skippedUnknown`、入缓存裁剪的四个数、Expedia 刷价轮末的成功失败。这四处正是“出报率”“刷价失败率”的分子分母。

## 2. 名字与标签

- **O-2.1 (MUST)** **维度进 tag，不进名字**（PROJECT.md §3.9.2）。证据两处：① `ChunkedFileAccess.monitorName` 把 supplier 与 interface 拼进名字，最多产生 1080 个独立指标名，而同包的 `BaseHttpAccess` 注释里写的却是相反约定——同包两种做法并存，是本条必须成文的直接理由；② 艺龙验价把**结果**拼进名字，拆成 `elong_validate_day_price_mismatch` / `_retry_ok` / `_retry_failed` 三个指标，于是“对齐失败率”这一个问题要查三个指标才能回答。
- **O-2.2 (MUST)** 埋点名禁止自带 `_count`/`_time`/`_value`/`_total`——后缀由框架追加，自带会得到 `xxx_count_count`。
- **O-2.3 (MUST)** `supplier` 标签值一律取 `SupplierSourceEnum.name()`，即**只有 `ELONG` 与 `EXPEDIA` 两个合法值**。禁止字面量（现存小写 `"elong"`/`"expedia"` 硬编码 4 处）、禁止 `getDesc()`、禁止数字 code（现存 `catalog_attribute_*` 用 `"10010"`）。
  后果不是不好看而是**不可用**：`catalog_attribute_hit{supplier="10010"}` 与 `supplier_io_access{supplier="ELONG"}` 在 PromQL 里拼不起来，“无房型映射丢多少”这个问题因此在指标通道上无解。
- **O-2.4 (MUST)** 指标名与标签键必须有**唯一出处**（常量类或枚举），禁止散落字面量。现存 7 个文件各写一遍 `"supplier"`/`"status"`，改名时无从知道改全了没有。
- **O-2.5 (MUST NOT)** 高基数字段禁止进标签：`hotelId`、`orderId`、`productKey`、日期、URL、异常消息、任何自由文本。判据：取值集合是否随业务量增长——增长即禁止。
- **O-2.6 (MUST)** 本仓自建的 `_time` **一律毫秒**；Micrometer 自带的 `*_seconds` 是秒，两者禁止进同一表达式或同一 Y 轴。证据：`supplier_io_access_time` 生产实测 `sum/count ≈ 1269`（艺龙查价约 1.3 秒），当秒读会差三个数量级。
- **O-2.7 (MUST)** **同一概念只许一个标签键、一套取值**。键与概念的对应关系固定为两条，不得混用：

  | 键 | 概念 | 取值出处 |
  |---|---|---|
  | `status` | 一次供应商调用的终态 | `CallStatus` 枚举，六个值，互斥且穷尽 |
  | `outcome` | 校验类检查结果、下载方式、分态结论这类非终态的结果 | 由该指标自行定义并在 `MetricNames` 注释里写明 |
  | `stage` | 漏斗阶段：报价丢在哪一环 | `FunnelStage` 枚举（O-4.6：小集合且稳定） |
  | `reason` | 报价为什么被丢弃 | `DropReason` 枚举（O-4.4） |
  | `source` | 查价这条腿走缓存还是实时 | `MetricTags.SOURCE_CACHE` / `SOURCE_LIVE` 两个常量 |

  反面即改造前：`supplier_io_access` 用 `status`（`ok`/`empty`/`error`/`limited`）、`pricing_supplier_query` 用 `outcome`（`all`/`empty`/`fail`/`success`），两个键都在说“这次调用的终态”，取值集合却不同——于是“全平台调用成功率”没有一条 PromQL 能回答，只能手工拼，而拼法因人而异、结论随之不同。

## 3. 取值必须说人话

- **O-3.1 (MUST)** **禁止无宾语的取值**：`ok`、`all`、`success`、`fail` 这类词看图时无法判断“什么 ok”。取值必须自解释，说清成立的是什么。调用终态的词表固定为六个，互斥且穷尽：

  | 取值 | 含义 |
  |---|---|
  | `quoted` | 供应商答了，且有可卖报价 |
  | `no_inventory` | 供应商答了，但无房/无价（业务正常态，非故障） |
  | `rejected` | 供应商答了，但返业务错误码（参数、权限、报价码过期） |
  | `throttled` | 被限流（本地闸门拦下或供应商返频控码） |
  | `timeout` | 超时无响应 |
  | `error` | 其余异常（连接失败、解析失败） |

- **O-3.2 (MUST)** 每个指标必须有**一句话解释**，两处同时给：埋点名常量的 javadoc，以及看板面板的 description（Grafana 里鼠标悬停即可见）。没有解释的面板不许合入。
- **O-3.3 (MUST)** 取值集合必须**互斥且穷尽**，使 `sum by (<键>)` 等于事件总数。现存两处违反：`BaseHttpAccess` 对空结果先记 `empty` 再无条件记一次 `ok`（`ok` 实际含义是“全部调用”，空结果占比因此被系统性低估）；`pricing_supplier_query` 的 `outcome=all` ≠ `empty+fail+success`。
- **O-3.4 (MUST)** 同一次事件在同一指标名下**只许计一次**。
- **O-3.5 (MUST)** 计时与计数同源：`_time` 的记录次数必须等于其所计事件次数。上述双计同时污染耗时——空结果被计两次，平均耗时被拉向空调用一侧。

## 4. 图要分业务分系统，且做全

- **O-4.1 (MUST)** 看板**按业务分块**：查价、验价、下单、刷价、建档各一块。每块内部按同一套 stage 纵向排列（入口 → 缓存 → 转换 → 供应商 IO），使“卡在哪一层”能直接看出来，而不需要跨块拼指标。
- **O-4.2 (MUST)** **一块业务的指标必须做全**：请求数、出报数（或成功数）、失败数按 reason 分、耗时，四件套缺一不许上线。只埋一半的后果是图上只能看出“少了”，看不出“少在哪”——等于没埋。
  证据：`SpaController` 的 `/client/spa/price` 现在只有耗时 `query_price_for_spa`，既无请求数也无出报数，“出报率 36%”这个已知结论目前无法用指标复现。
- **O-4.3 (MUST)** 漏斗用**一个指标名**覆盖两家供应商，supplier 作标签。禁止各家各造一套。此前艺龙有 `refresh_*` 七个指标而 Expedia 刷价零埋点，两家成功率无法同图对比——2026-08-25 两家一起搬上 `AbstractCPSQueryPriceService` 后，埋点跟着骨架走，新接一家自动具备，不再需要各自记得埋。
- **O-4.4 (MUST)** `reason` 取值必须来自**枚举**，禁止自由字符串、禁止异常消息、禁止拼接（同 O-2.5：丢弃分支散布在查价、刷价、入缓存三条链路上，放开自由文本则序列数随代码改动无界增长）。
- **O-4.5 (MUST)** **静默丢弃分支必须有 reason 落点**。证据：缓存读侧 `PriceCacheServiceImpl.getPrice` 在 `productMap.forEach` 的 lambda 里有五个 `return`（即跳过该产品）——总价为 0、逐日价条数≠住期天数、某日价为 0、quote 详情缺席（拿不到票据）、`productId` 为空——五个分支全部既无日志也无指标。其中“逐日价条数≠住期天数”必然吃掉多晚查询，是出报率的直接扣分项。
- **O-4.6 (MUST)** **“不丢货、只丢内容”的降级必须单独可数**。证据：房型属性为 null 时照样出报（`if (attr != null)` 无 `else`），SPA 自身不报错，而下游按房型装配整片落空——没有异常可抓，只能靠覆盖率数字暴露。

---

# 第二部分：宁缺毋滥

## 5. 指标的准入与退出

- **O-5.1 (MUST)** **新增指标必须同时给出消费方**（一个看板面板或一条告警规则），否则不许加。证据：`catalog_attribute_asked`/`catalog_attribute_hit` 已在生产采集却不在任何看板、任何告警——它恰是唯一能量化“无房型映射”损失的指标，埋了没人看等于没埋。
- **O-5.2 (MUST)** **只增不减即为失控**。每个涉及观测的 PR 顺手核对：无消费方的指标下线，描述同一根因的告警合并，阈值已随基线失效的规则按其关闭条件删除。
- **O-5.3 (MUST)** 指标名与标签键**视同接口**，改名或删除前必须证明无消费方（`deploy/monitoring/grafana/dashboards/*.json`、`deploy/monitoring/prometheus/rules/*.yml`）。理由与 §6.3.1“日志模板即接口”相同。
- **O-5.4 (MUST)** 埋点通道唯一：只经 `platform/observability/Monitor`，禁止业务代码直接持有 `MeterRegistry`、禁止 `@Timed`。现状已符合（32 处埋点无例外），成文为防退化——`@Timed` 一行就能加，且会绕过本文全部命名与取值规则。

## 6. 现场（日志）

日志写法全依 PROJECT.md §6，此处只管载体。注意方向与指标相反：**日志宁多勿少**。

- **O-6.1 (MUST)** 日志必须有能**活过一次发版**的落点。仅写 stdout 不满足本条。证据：`log4j2.xml` 定义了 6 个 RollingFile，无一产生日志——5 个没有任何 `AppenderRef`，第 6 个（`ThirdPartyAppender`）被 logger `monitor_company` 引用，而这个 logger 名在代码里零使用者。根目录还硬编码 `/tmp/logs`，在容器内不持久。实际只有 Console 生效，PROJECT.md §6 自述的“44 分钟窗口”正源于此。
- **O-6.2 (MUST)** 每条业务日志必须带**请求级关联 ID**，入口 → 供应商 → 数据库全链路一致。证据：模板里已有 `%X{traceId}` 槽位，但全仓零 MDC 写入、无 Filter，永远渲染为空；`Customer-Session-Id` 取自静态配置（进程内所有请求同值），不得充当关联 ID。
- **O-6.3 (MUST NOT)** 配置里不得留死 appender——无引用的 appender 会让读者以为日志有文件落点，据此做出错误的排障计划。

## 7. 证据·报文快照

- **O-7.1 (MUST)** **快照表必须先存在，才允许按 §6.1.1.1 对成功路径的大对象采样**。二者是同一笔交易的两面：不建表就采样，等于取证与现场两头落空。现状没有任何「每次调用一行」的表，而 §6.1.1.1 点名的巨型日志仍在全量直打（占生产一小时日志量 17%）。注意别被名字骗了：`ExpediaPropertySnapshotRow` 叫 Snapshot，写的却是覆盖写的内容档案（见 O-7.2）。
- **O-7.2 (MUST)** 快照形状是**每次调用一行**，含关联 ID、时间、供应商、接口、URL、状态码、耗时、报文原文。**禁止用覆盖写的内容表充当快照**：`expedia_property_content` 主键是 `property_id+language`、每家一行覆盖写、无状态码无耗时，它是内容档案不是报文证据。
- **O-7.3 (MUST)** 留存期必须明示且有上限，并说明超期后靠什么回答取证问题。

## 8. 诊断端点

形状与分置依 PROJECT.md §3.9.3、§3.9.4，不复述。

- **O-8.1 (MUST)** 诊断端点必须能回答“**这一个对象现在为什么没出报价**”：按对象组织，入参为业务标识，返回把该对象在各 stage 的判定一次给全。它与漏斗指标共用同一套 reason 词表——指标答“整体流失在哪一档”，诊断答“这一家为什么”。
- **O-8.2 (MUST)** 诊断端点只读。会改状态的属运维动作，分置 `ops/`——现状 `ops/BackDoorController` 的 8 个端点都是运维动作，分置本身是对的；缺的是诊断侧，一个都没有。

## 9. 告警与通知

- **O-9.1 (MUST)** 阈值必须**锚实测基线**，并在规则文件里写明基线值与倍数关系，禁止拍整数。现存 `deploy/monitoring/prometheus/rules/spa.yml` 即按此写法（刷价失败率基线 0.17% → 阈值 2%；艺龙查价基线 1.3 s → 阈值 5 s）。
- **O-9.2 (MUST NOT)** 告警表达式不得把**不可加**的指标（违反 O-3.3 的）用作分母——双计的分母会系统性低估比率，使告警永不触发，比没有告警更坏。

以下四条约束**投递**。直接理由：cursor 已出现大量无人处理的通知，收件人对通知整体脱敏（2026-08-21 周涛口述）。通知一旦脱敏，要紧的那条会被连带忽略，**所以“多投一条”不是稳妥，而是在消耗唯一的注意力预算**。

- **O-9.3 (MUST)** 通知必须**可动作**。判据：写不出“收到后第一步做什么”就不许投递，降级为看板可见。
- **O-9.4 (MUST)** **一个根因只许产生一条通知**。供应商挂了会同时触发 IO 失败、空结果、出报率下跌、刷价失败四条，必须靠分组与抑制合并。
- **O-9.5 (MUST)** 每条告警必须有 `for` 持续期。单次超时、一次限流命中属常态抖动，不构成通知。
- **O-9.6 (MUST)** 每条告警必须写明**关闭条件**——什么情况下这条规则应当被删除（基线变了、指标下线、业务形态变更）。没有关闭条件的规则只会累积成噪音。

---

## 10. 当前欠账

按依赖排序——前项不做，后项就建在错数上。「状态」列记录对齐进度。

| 序 | 事项 | 违反 | 位置 | 状态 |
|---|---|---|---|---|
| 1 | 空结果双计：记了 `empty` 又无条件补记 `ok` | O-3.3、O-3.5 | `BaseHttpAccess.access` | 已修 |
| 1b | 重试双计：每抛一次异常记一条终态，一次调用最多 N+1 条 | O-3.1 | `BaseHttpAccess.query` | 已修 |
| 1c | `!isSucc()`（HTTP 非 2xx、业务错误码）被算成 `ok` | O-3.3 | `BaseHttpAccess.access` | 已修 |
| 1d | Expedia 缓存刷价路只记不可加的 `all`，失败分支零落点 | O-3.1、O-3.3 | `ExpediaPriceServiceImpl` | 已修 |
| 2 | 取值不说人话（`ok`/`all`） | O-3.1 | `BaseHttpAccess`、`ExpediaPriceServiceImpl` | 已修（`CallStatus` 六词表） |
| 3 | `supplier` 标签两种方言：数字 1 处、小写字面量 5 处 | O-2.3 | `ProductAttributeReader`、`QueryProductAccess`、`ElongCatalogService`、`ElongCPSQueryPriceServiceImpl`、`ElongPriceServiceImpl`、`ExpediaPriceServiceImpl` | 已修 |
| 4 | 同一概念两个键（`status` / `outcome`） | O-2.7 | 同上 | 已修（键与概念一一对应） |
| 5 | 维度拼进指标名：两处 | O-2.1 | `ChunkedFileAccess.monitorName`、艺龙验价三个名字 | 已修 |
| 6 | 指标名/标签键无唯一出处 | O-2.4 | 7 个文件 | 已修（`MetricNames`/`MetricTags`/`CallStatus`） |
| 7 | 指标无解释 | O-3.2 | 32 个埋点 + 看板 27 面板 | 部分：看板 description 已补全，埋点注释随迁移补在 `MetricNames` |
| 8 | 覆盖率指标无消费方 | O-5.1 | `catalog_attribute_*` | 未修 |
| 9 | 入口缺请求数/出报数 | O-4.2 | `SpaController` | 已修（`spa_price_leg`/`spa_price_quoted`，腿=请求×供应商） |
| 10 | 已算出的计数只落日志 | O-1.3 | 四处（见 O-1.3） | 未修（第 2 步） |
| 11 | 静默丢弃无 reason | O-4.5、O-4.6 | 读侧 `PriceCacheServiceImpl.getPrice` 的 forEach 五个 `return`（全无落点）；艺龙查价三类跳过（有日志、无指标）；写侧 `productToCache` 两个 `continue` 是异常价拦截，拦截时有 `log.warn`、但无指标 | 读侧与艺龙转换已修（`quote_dropped`，stage/reason 见 `DropReason`）；写侧异常价拦截的指标待做 |
| 12 | 看板未按业务分块 | O-4.1 | `spa-overview.json`（现按技术层分组） | 未修（第 3 步） |
| 13 | 日志无落点、无 traceId | O-6.1~O-6.3 | `log4j2.xml`：6 个 RollingFile 无一产生日志（5 个零 `AppenderRef`，第 6 个被 logger `monitor_company` 引用而该 logger 名在代码里零使用者）；根目录硬编码 `/tmp/logs`；`%X{traceId}` 3 处槽位、`MDC.put` 全仓 0 处 | 未修 |
| 14 | 报文快照表不存在 | O-7.1、O-7.2 | 名字里带 Snapshot 的 `ExpediaPropertySnapshotRow`/`Mapper` 写的是 `expedia_property_content`——主键 `property_id+language`、覆盖写、无状态码无耗时，是内容档案不是报文证据 | 未修 |
| 15 | 诊断端点不存在 | O-8.1 | `ops/BackDoorController` 的 8 个端点全是运维动作（建档、清洗、拉地理数据、手动刷价），没有一个回答“这一家为什么没出报价” | 未修 |
| 16 | 告警不投递 | O-9.3 | `spa.yml` 12 条规则 | 未修（`for` 与关闭条件已补） |

「已修」指 2026-08-21 的口径统一那批。超时与连接/解析失败目前仍混在 `error` 一态里——
底层把它们都抛成普通 `Exception`，要分出 `timeout` 得先在 `request()` 里辨别异常类型，
这笔留在欠账里，不因为词表里有 `timeout` 就假装已经分开了。

两项待评估，暂不立规则：

- `MonitorService.getSummary` 给每个 `_time` 附加常量标签 `avg_label`（值是 `<name>_sum/<name>_count` 这样的公式字符串）。不影响聚合，但每条序列多挂一个恒定标签；历史框架遗留，撤除前需确认无消费方（O-5.3）。
- 供应商 QPS 有两套并行实现：`RedisRecordLogServiceImpl` 按小时 `INCR`（仅 Expedia 在用，艺龙没有）与 Micrometer 的 `supplier_io_access`。两套口径无人对齐，出分歧时无法判断谁对；建议以后者为准、前者下线，但需先确认无人查那批 Redis 键。
