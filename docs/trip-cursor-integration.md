# tg-trip-cursor 接入方案

> 目标：把 `trip-booking-spa` 作为供应商网关接入 `tg-trip-cursor`，首条纵向链路选择
> Expedia。本文只定义系统边界、跨服务契约与灰度顺序；供应商比价、渠道报价、订单、支付和
> canonical 聚合仍归 `tg-trip-cursor`。

## 1. 结论

两边不能以“再包一层 HTTP”的方式打通。正确接法是让 `tg-trip-cursor` 的供应商 strategy
调用 SPA，但不再理解 Expedia 的 token、错误码、状态原文和接口重试规则：

```text
渠道
  -> tg-trip-cursor（聚合、比价、渠道报价、订单、支付、换供）
       -> SPA adapter（只做统一契约转换和切流）
            -> trip-booking-spa（供应商语义、凭据、三态、限流、重试）
                 -> Expedia Rapid API
```

首期按“混合供应栈”设计。SPA 不读写 cursor 的业务表，cursor 也不读取 SPA 的 Redis 或数据库；
两边只通过版本化 HTTP 契约通信。这样可以独立发布、独立回滚，也不会形成共享库耦合。

## 2. 当前阻塞点

现有五个 SPA 端点已经覆盖查价、验价、下单、查单和取消的接口形状，但还不能直接承接真实切流：

| 阻塞点 | 当前表现 | 必须怎样处理 |
|---|---|---|
| 验价分态不对称 | SPA 有 `BOOKABLE/SOLD_OUT/RATE_DEAD/INDETERMINATE`；cursor 主要是 `bookable + TbBookingStatusEnum` | cursor 增加等价的供应商验价 outcome，禁止把 `RATE_DEAD` 和 `INDETERMINATE` 都映成 `UNAVAILABLE` |
| 下单分态不对称 | SPA 有 `SUCCESS/FAILED/UNKNOWN`；cursor 的供应商响应以 `success` 布尔值为主 | cursor 增加显式下单 outcome；`UNKNOWN` 必须进入查单确证，不能走确定失败退款 |
| `offerId` 链路断裂 | SPA 验价签发不透明句柄；cursor 的验价响应和下单请求没有对应字段 | 句柄仅作为内部执行材料，从验价结果进入本次下单请求；不得塞入 `ratePlanId`、`supplierExt` 或暴露给渠道解析 |
| 补提交会使用陈句柄 | cursor outbox 补提交前会重新验价，但当前只返回布尔结果，不能替换下单材料 | 补提交前验价改为返回“新请求材料”；只有 SPA 查单明确 `NOT_FOUND` 且新验价 `BOOKABLE` 时，才携新的 `offerId` 补提交 |
| 产品身份仍易腐 | SPA 当前 `productId` 仍可能是 Expedia `rate.id` | 增加稳定 `productKey`；由酒店、房型、餐食、退改、支付、床型和占用派生，供应商 rate token 留在 SPA 内部 |
| 占用契约不统一 | 当前主要是总成人、总儿童及字符串 `occupancies` | v1 契约只接受结构化 `rooms[].adults/childAges`，SPA 内部再转换为供应商口径 |
| 金额契约不够硬 | 部分 DTO 使用 `Integer`，字段注释没有统一声明总价/单房/含税口径 | v1 金额使用 `long` 最小货币单位，并显式带币种；统一为全部房间、全部晚、含税总价 |
| 生产交易尚不可放开 | Expedia 生产端点和真实下单仍有硬保护，取消能力尚未实现 | 真实切流前完成认证、取消链路和沙箱闭环；硬保护不得通过运行时开关绕过 |
| 服务边界缺少保护 | `/client/spa/**` 无明确版本和服务身份契约 | 新增 `/internal/v1/**`；入口采用内网 + mTLS（或等价服务认证），传递 request/trace id，旧端点保留兼容期 |

## 3. v1 交易契约

对 cursor 暴露供应商代码（如 `expedia`），不暴露 SPA 内部的数字 `supplierId=10005`。
数字 ID 的转换只允许存在于 SPA 的兼容层。

### 3.1 能力与健康

- `GET /internal/v1/suppliers/{supplierCode}/capabilities`
- 返回 `SHOP/CHECK/BOOK/QUERY/CANCEL` 的可用性、契约版本和限制摘要。
- “服务健康”与“某能力已获准生产使用”必须分开表达；例如进程健康但 Expedia 生产下单未获准时，
  `BOOK.productionReady=false`。

### 3.2 查价与验价

- `POST /internal/v1/suppliers/{supplierCode}/offers:search`
- `POST /internal/v1/suppliers/{supplierCode}/offers:check`
- 查价返回稳定 `productKey` 和价格事实，不返回可解析的供应商 token。
- 验价返回 `outcome`；仅 `BOOKABLE` 时返回 `offerId`、`offerTtlSeconds` 和含税总价。
- `RATE_DEAD` 表示重新查价，`SOLD_OUT` 表示确定售罄，`INDETERMINATE` 表示稍后重试；
  cursor 禁止对后两者做数据库价“可订”兜底。

### 3.3 下单、查单与取消

- `POST /internal/v1/suppliers/{supplierCode}/bookings`
- `GET /internal/v1/suppliers/{supplierCode}/bookings/by-client-reference/{orderNo}`
- `POST /internal/v1/suppliers/{supplierCode}/bookings/by-client-reference/{orderNo}:cancel`
- `orderNo` 是唯一幂等坐标，并原样映射到 Expedia `affiliate_reference_id`。
- 下单必须携带同一次有效验价产生的 `offerId`。写接口不做 transport retry；若响应不确定，返回
  `UNKNOWN`，由 cursor 查单确证。
- 查单只有供应商明确回答无单时才返回 `NOT_FOUND`；超时、限流、5xx 和无法判读一律返回
  `INDETERMINATE`。
- 取消也需要三态（成功、确定失败、结果不确定）以及 `cancelFee + currency`；不能沿用当前
  `sOrderStatus=0/1/2` 的含糊表达作为 v1 契约。

## 4. cursor 侧接线

### 4.1 领域模型

需要新增或扩展以下内部字段，名称可按 cursor 代码风格调整，但语义不可丢失：

- `TbSupplierPriceCheckResponse.outcome`
- `TbSupplierPriceCheckResponse.gatewayOfferId`
- `TbSupplierPriceCheckResponse.gatewayOfferTtlSeconds`
- `TbSupplierOrderCreateRequest.gatewayOfferId`
- `TbSupplierOrderCreateResponse.outcome`
- 查询与取消响应的显式三态 outcome

`gatewayOfferId` 是 cursor 内部执行材料，不是渠道产品身份。不得拼进 `md5#subSaleId`，不得写进
长期产品表，也不得要求渠道理解或回传。

### 4.2 adapter strategy

新增 Expedia 的 price/order strategy，职责限制为：

1. 从 cursor 的 `RoomSubSale`/mapping 取得稳定供应商酒店与房型坐标；
2. 把结构化占用、住期和金额转换成 SPA v1 请求；
3. 调 SPA，并逐态映射响应；
4. 填充 cursor 现有诊断 detail 和原始请求/响应审计字段。

strategy 不解析 Expedia 响应、不持有 Expedia 密钥、不实现供应商重试，也不自行建立 token 缓存。

### 4.3 下单前验价和 outbox

Expedia 的所有真实下单必须先执行严格验价，并在同一执行链中把新 `offerId` 绑定到最终
`TbSupplierOrderCreateRequest`。这不能只依赖目前“仅部分渠道启用”的 `preOrderVerify` 标志。

outbox 的顺序固定为：

```text
查单 FOUND          -> 回填，不补提交
查单 INDETERMINATE  -> 保持确认中，稍后再查
查单 NOT_FOUND
  -> 重新严格验价
       -> BOOKABLE：用新 offerId 补提交
       -> 其他结果：不补提交，按结果进入重试或确定失败流程
```

绝不能把旧 `gatewayOfferId` 原样用于下一轮补提交。

## 5. 目录与价格数据面

交易面接通不代表 Expedia 能在 cursor 中自然出价。cursor 的 getPrice 依赖自己的 canonical、
`room_sub_sale` 和 `room_price`，所以需要单独的数据面：

1. SPA 提供稳定、可分页、可增量的规范化酒店/房型目录；
2. cursor 的 ingestion adapter 拉取并写入自己的 mapping，不共享 SPA 数据库；
3. cursor 的 Expedia flush adapter 调 SPA 查价，写入 cursor 自己的 `room_price`；
4. SPA 返回供应商原生稳定 ID 和结构化房型属性，cursor 负责 canonical 匹配；
5. 首批 pilot 可人工种入少量已核验映射，但不得把人工 SQL 当作长期同步方案。

数据面未完成前只能做沙箱交易联调，不能宣布供应商网关已经完整接入。

## 6. 容量、闸口与观测

cursor 新增供应商必须声明 `tb.supplier.capacity.expedia`，并经统一
`SupplierCapacityGuard/RedisClusterRateLimiter`。该层保护 cursor 自身与 SPA 调用预算；SPA 的
`BaseHttpAccess` 仍是 Expedia 账号真实配额的最终闸门。两层指标分开命名，避免把一次请求重复算成
两次供应商调用。

新增 `supplier.spa-expedia-route` 闸口，实现 cursor 的 `AdmissionGate`：

- `off`：不让 Expedia 进入可售候选；
- `shadow`：只对 pilot 样本执行查价/验价并记录差异，不参与赢家、不创建订单；
- `on`：仅对白名单国家/酒店/渠道真实参与，逐档扩量；
- `FailPolicy=CLOSED`：交易路由配置异常时不允许误下真实单。

`description()` 必须写清 on/shadow/off 的业务含义和误切资损。观测至少按供应商、方法、outcome
记录调用量、延迟、限流、`RATE_DEAD`、`INDETERMINATE`、`UNKNOWN`、查单确证结果和补提交次数，
并传递统一 `requestId/traceId/orderNo`。

## 7. 实施顺序与验收门

### Phase 0：契约锁定

- 在 SPA 增加 v1 facade、能力发现、服务认证和契约测试；旧 `/client/spa/**` 不删除。
- 在 cursor 增加三态与 `gatewayOfferId` 的内部模型，先不接真实路由。
- 用固定 JSON fixture 做双仓 consumer/provider contract test。

验收：四种验价结果、三种下单结果、三种查单结果都能无损往返；任何未知枚举均 fail closed。

### Phase 1：Expedia 沙箱交易闭环

- 接通 check -> book -> query；补齐 cancel 后再做完整闭环。
- 验证同一 `orderNo` 重放不重复成单；人工制造超时，确认走 `UNKNOWN -> query`。
- 验证 offer 过期后不会使用旧句柄补提交。

验收：真实沙箱完成可订、售罄、死报价、超时未知、查到订单、明确无单、取消成功七类证据。

### Phase 2：数据面与 shadow

- 接入规范化静态目录和 flush；pilot 酒店形成 cursor 自己的 mapping/room_price。
- route gate 切 `shadow`，至少观察价格、库存、取消政策、房型和错误分态差异。

验收：映射正确率、含税总价口径和三态分类达到发布阈值；shadow 不产生订单。

### Phase 3：小流量生产切换

- 前提是 Expedia 认证完成、生产硬保护经评审解除、取消可用、容量配置生效。
- 按内部测试渠道 -> 白名单酒店 -> 白名单国家逐档切 `on`；每档可独立回 `off`。
- 不在同一发布中删除旧供应商实现或扩大渠道范围。

验收：订单终态可对账，`UNKNOWN` 全部进入查单闭环，无重复单、无确定失败误退款、无金额口径差异。

## 8. 首个实现批次

第一个代码批次只做 Phase 0，不同时改数据面和真实订单行为：

1. SPA：增加版本化 capability/check/book/query 契约 facade 和 contract tests；
2. cursor：增加无损三态、内部 `gatewayOfferId`、SPA client 与 Expedia adapter；
3. cursor：新增默认 `off` 的 route gate、容量声明和单测；
4. 双仓：固定 fixture 验证序列化字段、未知枚举和超时分类；
5. 本地：启动两个服务，用 Expedia 沙箱完成一次 check -> book -> query，不接生产流量。

这个批次完成后，才开始目录/价格数据面与 shadow 对账。
