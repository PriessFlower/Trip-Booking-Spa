# Expedia Rapid 下单契约要求

> **定位**：下单链路的实现依据。全部条目摘自 EPS 官方材料，非推测。
> **来源**：`Tripbooking AI_technical workshop.pdf`（2026-08-04 技术研讨会，142 页）
> 页码指该 PDF 页序：凭证与参数 p6、下单 API p103-114、下单后管理 p115+。
> **状态**：参数集与错误处理已确认；下单请求体的支付字段形态待向 EPS 确认（见 §5）。

## 1. 两套凭证参数（p6）

EPS 按「不同费率／渠道／预订流程」发放**多套 profile**，两条车道参数不可混用。

| 项 | B2C（移动端 App） | B2B（内部客服工具） |
|---|---|---|
| `partner_point_of_sale` | `B2C_SA_MOD_XSELL_APP` | `B2B_SA_PKG_MOD_AGENT` |
| `billing_terms` | `EAC` | `EAC` |
| `payment_terms` | `1` | `2` |
| `sales_channel` | `mobile_app` | `agent_tool` |
| `rate_option` | `member` / `cross_sell` | `member` |
| `sales_environment` | `hotel_only`（零售）<br>`hotel_package`（交叉销售） | `hotel_only`（零售）<br>`hotel_package`（打包） |

**两条车道都支持 `hotel_only` 与 `hotel_package`**——车道由凭证参数区分，不由售卖类型区分。

> ⚠️ 与 `tg-trip-cursor/docs/supplier-api/expedia/kickoff-2026-07-27.md` §3.2 的差异：
> 该纪要记 B2B 的 `sales_channel` 为 `pc_website`，本 PDF 为 `agent_tool`。
> **以本 PDF 为准**（技术研讨会晚于 kickoff，且此处是逐项参数表）。

## 2. 结算（p4、p6）

- 财务方案 **EAC**，结算币种 **CNY**
- 询价／预订／账单币种：USD 与 CNY 皆可，汇率源 Bloomberg
- `billing_terms=EAC` 与 `payment_terms` 由 CID 决定，每次调用必带

## 3. 下单流程与端点（p103-107）

- 下单请求发往验价响应中的 `links.book.href`（自带 token），不另拼路径
- 认证完成前只能打测试端点 `https://test.ean.com/v3`
- 下单成功响应含「取回行程」与「取消房间」的链接

### 3.1 锁单（hold & resume，p108）

| 项 | 说明 |
|---|---|
| 时长 | 10 分钟 |
| 退款政策 | 锁单不受退款政策约束 |
| 扣款 | 锁单阶段不扣款 |
| 流程 | 两步：请求锁单（`hold=true`）→ 确认下单（resume） |
| 未确认 | 超时未确认即视为取消 |

本服务当前不使用锁单（`hold=false`，一步成单）。

## 4. 制裁审查与会员号（p110-113）

- **制裁审查**：EPS 用旅客数据对照 UN／UK／EU 制裁名单筛查，**旅客姓名必须经 API 如实提交**。
  这是合规义务，不是可选项——不得为省事填占位姓名。
- **酒店集团会员号**：`rooms[].loyalty_id` 可选。仅当报价的 `value_adds` 标明该费率可积分时有效，
  能否累积由酒店决定。

一间房一个 `rooms[]` 条目，字段：`given_name`、`family_name`、`smoking`、`special_request`、`loyalty_id`。

## 5. 支付字段（p105、p107）— 待确认

两处表述需向 EPS 澄清后再定实现：

- p105：「请求中必须包含支付信息，含账单／持卡人联系信息」
- p107：EAC 场景的即时下单请求体标注为 **`affiliate_collect`**（我方代收款）

`affiliate_collect` 通常意味着不向 Expedia 传卡号。二者是否并存、需要哪些字段，
**在向 EPS 确认前不实现该部分**，以免照猜写出错误实现或误留卡号处理路径。

## 6. 错误处理（p113-114）— 强制要求

EPS 明确规定的处置流程，与本服务的三态契约一致（`BookingOutcome`）：

### 6.1 无响应 / 500 / 503 / 504

> 意味着**双方都不知道最终状态**。必须等待 **90 秒**，再用
> `affiliate_reference_id` 取回行程以确定状态。

对应实现：判 `UNKNOWN`，由上游在 90 秒后反查确证。**禁止**直接判失败。

### 6.2 409 / 410

> 等待 90 秒，用 `affiliate_reference_id` 核查是否已产生**重复订单**，
> 之后才可向旅客报新价或换房换店。

对应实现：判 `UNKNOWN`。

### 6.3 其他 4XX

> 依嵌套错误信息调整请求内容，**用同一 `affiliate_reference_id` 重试**。

对应实现：可判 `FAILED`（业务性拒绝），但幂等号必须复用。

**⚠️ 例外：`duplicate_itinerary` 必须判为成功，不可判失败。**

实测（2026-08-10，沙箱）：用同一 `affiliate_reference_id` 重复下单，返回

```
HTTP 400
{"type":"invalid_input","errors":[{"type":"duplicate_itinerary",
  "message":"An itinerary already exists with this affiliate reference id."}]}
```

该错误的真实含义是**订单已存在**（首次已成功），而非业务性拒绝。若按「其他 4XX
即失败」处理，上游会退款并释放库存，而 Expedia 侧订单仍在——正是要防的两头空。

正确处置：识别 `duplicate_itinerary` 后转为反查，取回既有 `itinerary_id` 并判
`SUCCESS`。这也说明 **Expedia 侧的幂等确实生效**：重复提交不会产生第二笔订单，
故本服务的本地幂等是为了少打一次无用请求，而非防重复下单的唯一手段。

### 6.4 201 Created

即下单成功。

### 6.5 日志（强制）

> 记录全部下单请求与响应日志（含时间与 header）；记录全部错误响应日志。

⚠️ 与本项目要求的取舍：日志**禁止**打印支付字段与旅客证件信息。
留存范围应为时间、header、业务单号、错误码与响应体，敏感字段脱敏。

## 7. 反查订单（p115）

三种方式，优先级由上到下：

```
GET /v3/itineraries?affiliate_reference_id={我方单号}&email={邮箱}
GET /v3/itineraries/{itinerary_id}?email={邮箱}
GET /v3/itineraries/{itinerary_id}?token=...        ← EPS 明确「不推荐」
```

第一种是本服务确证下单结果的手段：仅凭我方单号与邮箱即可查回真实状态，
无需持有 Expedia 订单号——这正是下单超时后唯一可用的路径。

`include=history_v2` 可取回行程变更历史（created／modified／canceled）。

### 7.1 反查响应实测含有的字段（2026-08-10 沙箱）

反查回来的信息比下单响应丰富，下述字段均已实见，可直接用于对外回报与后续操作：

| 字段 | 实测值示例 | 用途 |
|---|---|---|
| `itinerary_id` | `7717630846973` | Expedia 订单号 |
| `rooms[].confirmation_id.expedia` | `879600704286433` | **酒店确认号**，旅客到店核对 |
| `rooms[].status` | `booked` | 房间状态 |
| `rooms[].links.cancel.href` | `/v3/itineraries/{id}/rooms/{uuid}?token=...` | **取消该房间**，逐房调用 |
| `rooms[].rate.cancel_refund` | `-65.98 CNY` | 取消可退金额 |
| `rooms[].rate.cancel_penalties` | 带起止时间的罚金区间 | 取消政策 |
| `rooms[].rate.pricing.totals.inclusive` | `1926.31 CNY` | 含税总价 |
| `rooms[].rate.pricing.totals.marketing_fee` | `4.00 USD` | 佣金 |
| `rooms[].rate.merchant_of_record` | `expedia` | 记录商 |
| `trader_information` | Travelscape LLC + 条款链接 | 合规展示 |

两点值得注意：

- **酒店确认号只在反查响应里，下单响应中没有**。旧 soa 实现的
  `hotel_confirmation_number` 长期为空，根因即在此——它只读下单响应。
  要拿确认号必须在下单成功后再反查一次。
- 取消链接同样只在反查响应里，故取消流程必然是「先反查取链接 → 再逐房 DELETE」。

## 7.2 旅客联系方式不出境（策略）

**提交给 Expedia 的邮箱与电话固定为我方运营联系方式**（Nacos
`supplier.expedia.booking-contact`），**不传旅客真实邮箱与电话**。

这是一条有意的业务保护，而非固定邮箱的副作用：**旅客的真实联系方式一旦出境，
供应商或酒店可直接联系旅客，使取消、改单绕开我方平台发生**，我方将失去订单控制权
与后续服务机会。

同一策略在 tg-trip-cursor 生产代码中已实施，且有专门组件承担
（`SupplierContactObfuscator`，注释原文：「供应商侧脱敏：真实手机/邮箱不出境——
克隆改写，落库/通知仍用原件」）。本服务采取更简的形式：直接用固定运营联系方式，
不做逐单克隆改写。

固定邮箱同时带来一个实现便利：反查要求邮箱与下单时完全一致，固定为一个值后仅凭
上游订单号即可反查，无需持久化「这一单用了哪个邮箱」。

### 旅客信息一律不出境（已与 Expedia 商定）

| 数据 | 是否出境 | 提交什么 |
|---|---|---|
| 旅客姓名 | **否** | 固定值 `NEO / NEO` |
| 旅客邮箱、电话 | **否** | 我方运营邮箱与电话 |

即下单请求中不含任何旅客真实信息。**该安排已与 Expedia 商定**，不是技术推断。

> 备查：§4 记录了官方材料中的制裁审查条款（EPS 用旅客数据对照 UN／UK／EU 名单筛查）。
> 现行安排与该条款的关系由商务侧与 Expedia 约定，实测沙箱下单亦不校验姓名真实性。
> 若日后 EPS 就此提出要求，需回到此处重新评估。

### 由此确定的分工

Expedia 的订单确认与取消通知只会发到我方运营邮箱，旅客收不到 Expedia 的邮件。
故「订单确认邮件 / 入住凭证」须由**持有旅客真实联系方式的一方**发送——即上游订单系统，
不是本服务。本服务作为供应商网关，本就不应持有旅客联系方式。

该项是 Launch Requirements 前端要求之一（下单后 · 订单确认邮件 / 入住凭证），
责任方为上游。

## 8. 测试下单（p105、p113）

> 下单请求可加 HTTP header **`Test`**，取值决定要测试的响应形态；
> 加了该头的下单**不会扣款、也不会发送给酒店**。

即测试端点上还可用 `Test` 头模拟各种响应（含错误响应），这是验证三态判定的手段——
不必真的制造超时也能覆盖 `UNKNOWN` 分支。使用前须完整阅读 EPS 的 testing notes。
