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

## 8. 测试下单（p105、p113）

> 下单请求可加 HTTP header **`Test`**，取值决定要测试的响应形态；
> 加了该头的下单**不会扣款、也不会发送给酒店**。

即测试端点上还可用 `Test` 头模拟各种响应（含错误响应），这是验证三态判定的手段——
不必真的制造超时也能覆盖 `UNKNOWN` 分支。使用前须完整阅读 EPS 的 testing notes。
