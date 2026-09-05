# 飞猪国际酒店分销接口（TOP）——官方文档快照与判读

> **来源**：淘宝开放平台官方文档，经公开镜像 developer.alibaba.com 查阅（fliggy.open.taobao.com
> 站内搜索需登录，API 正文两边一致）。**查阅日期：2026-08-26**。
> **用途**：按 PROJECT.md §4.2.1，适配代码以本快照为依据；cursor 生产实证仅作线索，
> 与官方文档冲突时以实测为准并回写本文。
> **标注纪律**：〔官方〕=文档原文；〔实证〕=cursor 生产代码/日志；〔未确认〕=两边都没有，
> 接入前必须实测。

## 0. 接口清单与文档地址

| 能力 | TOP method | apiId（developer.alibaba.com/docs/api.htm?apiId=N） |
|---|---|---|
| 查价（ARI） | `taobao.xhotel.distribution.ari.availability` | 68596 |
| 验价 | `taobao.xhotel.order.international.distribution.validate` | 68688 |
| 创单 | `taobao.xhotel.order.international.distribution.create` | 68690 |
| 查单 | `taobao.xhotel.order.international.distribution.detail` | 68687 |
| 取消 | `taobao.xhotel.trade.international.distribution.cancel` | 68689 |
| 静态·按 ID 查 | `taobao.xhotel.distribution.foundation.hotel.query` | 75171 |
| 静态·全量爬 | `taobao.xhotel.distribution.feed.hotel.query` | （cursor 在用；有 foundation 后不建议） |

## 1. 通道事实

- 网关：`https://eco.taobao.com/router/rest`。〔实证〕旧网关 `gw.api.taobao.com` 已下线
  （2026-08-10 cursor 实测直连超时，f3653ce15）——**6 月「集成死」的真凶是网关下线**。
- 传输：POST form-urlencoded，TOP 公共参数（method/app_key/session/timestamp/format/v/sign）。
- 签名：TOP MD5，`md5(secret + 排序后的k1v1k2v2... + secret)` 大写。
- 鉴权：签名之外还需 **OAuth session**。〔实证〕expires_in=90 天、无自动刷新、
  只能人工重授权（上次 2026-08-10，账号 hotel1154651，推算 ≈2026-11-08 到期）。
  → 申报 `CredentialRenewal.HUMAN_ONLY`，`CredentialExpiry` bean 从环境变量读授权日期。
- 错误双层：平台级 `error_response{code,sub_code}`（签名/session/频控）与业务级
  `result.is_success/error_resp_code`。〔实证〕`code=27 / Invalid session / invalid-sessionkey`
  = 我方凭据病 → AUTH_CONFIG，永不拉黑、永不判无货。
- 频控：〔官方〕六页均无 QPS 说明。〔实证〕cursor 按 ~20/s 自设。

## 2. 查价 `ari.availability`（68596）

请求〔官方〕：`hotel_id`（单店）、`check_in/check_out`、`adults` 必填 + `children/children_ages`
（**顶层标量，无 per-room 结构**）、`language`、`distributor`。

响应〔官方〕：
- `request_trace_id`（**"定价策略key"**）、`search_id`——验价要回传，见 §3
- `properties[].rates[]`：`rate_key`（票据）、`room_id/room_name`、`rate_plan_name`
- **价格单位＝分**：`total_rate{inclusive/exclusive/tax/fees, currency}` + `daily_rates[]` 逐日同构
  ——含税/不含税/税/费四件套全给，币种字段自带（〔实证〕值为 USD）
- `meals{number,type}`：type 0=无餐/1=含早/2=两餐/3=三餐
  ——〔实证 2026-08-28，18 店 305 条报价采样〕只出现过 type 0（165）与 type 1（140），
  type 2/3 与 meals 缺席均未见
- `cancel_policy{code, cancellation_desc, rules[]{onward,before,inclusive_amount(分),currency}}`
  ——〔实证 2026-08-28，13 店 × T+1/T+13/T+30 三住期 613 条报价〕`inclusive_amount` 是
  **数字串**（`"10524"`，canConvertToInt 对文本节点恒 false，解析必须 parseInt(asText)）。
  形态只有两种、无第三态：**有免费窗**（罚金 0 的段存在）或**全程罚全款**（每段罚金=
  total_rate.inclusive，多为单段 onward=当下）；未见部分罚金/无规则/解析失败。
  比例随住期漂移：T+1 全款形态 100%（免费窗临近入住过期），T+13 43%、T+30 45%。
  `code` 官方无码表：code=2 几乎恒为全款形态（333/335），但 T+1 抓到 2 条 code=4
  （免费窗码）呈全款形态——**判"不可退"必须按规则内容（每段罚金≥全款），不可按码表**，
  已实施为双家通用判据（CancelPolicy.deductsFullPrice，艺龙 CutType=4 同义）。
  `rate_plan_name` 语种：中文为主（~85%），少量英文占位（ROOM_ONLY）与日文原文
  （带【Fliggy】尾缀，~1%）。**规则时刻（onward/before）＝北京时间**〔实证 2026-08-28〕：
  响应 `data.time` 毫秒戳＝首段 onward 按 GMT+8 解释（东京酒店，东京时间差 1 小时对不上；
  规则边界 23:00 北京＝东京午夜整点，即酒店本地政策换算成北京时间表达）——守护钉在
  FliggyRealPayloadTest.cancelRuleTimesAreBeijing

## 3. 验价 `...distribution.validate`（68688）

请求〔官方〕：`rate_key` 必填（"非库存商品唯一标识"）、`number_of_rooms`、`check_in/out`、
**`occupancies[]` 必填且每间一条**（`room_no/adult_num/children_num/children_ages`——
**官方原生支持各间不同**）、`request_trace_id`（注明 rateRelationKey，即查价响应那个）、
`intention`（render/create/change 三种意图）〔未确认：三种意图的语义差异〕、
`search_room_price/search_promotion_amount`（查价页价格回传，单位分）。

响应〔官方〕：
- **`create_key`**——"创单必须的 key"。票据两层：查价给 `rate_key` → 验价再发 `create_key`
  → 创单双钥齐交。OfferStore 凭据键：`rate_key` + `create_key` + `request_trace_id`。
- `rate_plan_info`：`total_room_price`（分）、逐日 `daily_price_info_list[]`（date/price/board_do）、
  `max_booking_num/max_occupancy_num/max_inventory`、`cancel_policy_do`、`bed_desc`、
  **`currency_code` + `currency_rate`（汇率由飞猪给）**
- `cancel_policy_desc[]{before,onward,inclusive_amout(原文如此,少个m)}`
- `invoice_config_do`（开票能力）、`room_info`

〔未确认〕`rate_key`/`create_key` 有效期——官方只字未提；cursor 的 30 分钟
（`rateKeyExpireMinutes: 30`）是自设 TTL 非官方承诺。**腐性申报需 A/B 实测**。

## 4. 创单 `...distribution.create`（68690）

请求〔官方〕：`create_key` + `rate_key` **双钥必填**、`out_order_id`（**我方单号**）必填、
`total_room_price` 必填（验价价回传，防漂移）、`customers`（"每间房入住人信息"，
结构文档未展开〔未确认〕）、`hotel_contact{name,phone,email}`、`number_of_rooms`、
`check_in/out`（带时刻）、`hotel_arrival_time{earliest,latest}`、发票三件套（可选）。

响应〔官方〕：`result.tid`（**飞猪订单号**）+ `out_order_id` 回显。
→ 与 Expedia affiliate_reference_id 模式同构：我方单号进请求，B5 成立。

〔未确认〕幂等语义——文档无重复下单说明。〔实证〕cursor 靠缓存整份验价响应 + 下单后删缓存防重。

## 5. 查单 `...distribution.detail`（68687）

请求〔官方〕：**`dis_order_id`（我方单号）与 `fliggy_order_id` 二者不可同空**——
即我方单号足够反查，符合 B5。`distributor` 必填。

响应〔官方〕：`order_base_info{order_status/order_status_desc/currency_code/...}`、
`daily_info_list[]`（含 `currency_rate`、`buyer_real_refund`、`is_checked_in`）、
`order_fulfill_info{check_in/out, out_confirm_code(确认号), order_guest_list[]}`、
`room_info`、`hotel_info{shid,...}`。

〔未确认〕`order_status` 的取值枚举——文档未列。三态映射（OrderPresence）判据需实测/考古。

## 6. 取消 `trade...distribution.cancel`（68689）

请求〔官方〕：`order_base_req{dis_order_id, fliggy_order_id, distributor}`——两个单号
均标"可选"（至少给一，同查单口径）。**我方单号足够，cursor 传双号是超额**。

响应〔官方〕：`result{cancel_success(Boolean), forfeit_fee(Number, 示例 10000)}`。
- **`forfeit_fee` 无币种字段、文档未标单位**〔官方确认缺失〕。〔实证〕cursor 按 USD 分
  处理并换汇；汇率取不到时**用原值不阻断（这是资损口子，SPA 侧纪律：取不到→不确定）**。
  〔未确认〕真实币种，**接入必验（钱）**。
- 无"取消受理中"三态——`cancel_success` 布尔。UNKNOWN 判定只能靠超时/无响应兜底。

## 7. 静态数据 `foundation.hotel.query`（75171）——cursor 没用上的接口

〔官方〕入参 `hotel_static_info_top_param{language, shid_list[]}`——**按 shid 批量/单店查**。
响应含酒店全套（中英文名、坐标、星级、设施、图片视频）+ `room_list[]` 房型全套
（`srid`、中英文名、**床型 JSON、窗型、`max_occupancy/max_adults/max_children`**、加床政策）。

〔实证〕cursor 只用了 `feed.hotel.query`（全量分页爬、无单店查询，因此被排除出单店
拉取白名单）。**SPA 接入直接用 foundation，B8（结构化房型属性）的数据源现成。**
〔未确认〕`missing_shids` 语义之外的覆盖率、以及该接口是否也吃分销授权。

## 8. 错误码：官方全空白

六页的"错误码解释"表**全部只有表头没有条目**（仅通用示例 `code=50 isv.invalid-parameter`、
`101 参数错误`）。分类器判据只能来自 cursor 生产实证 + 上线后逐码考古，
按纪律：**码义未核实一律判不确定**；`27/Invalid session/invalid-sessionkey` → AUTH_CONFIG。

## 9. 接入前必测清单（腐性申报与资损门）

| # | 事项 | 为什么 |
|---|---|---|
| 1 | `rate_key` 跨时段/跨代稳定性、`create_key` 有效期 | 腐性申报硬门（R-4.1），OfferStore TTL 依据。〔实证 2026-09-05〕`rate_key` **会换代**：神户 50366597 同一报价，高德回传 `V3\|f605…7319`，当刻现货为 `V3\|f605…7319_FR111881050001`；验价 RATE_DEAD 8/18 全因精确匹配落空。已接模板 resolve 按 productKey 换票（闸口 `supplier.fliggy.resolve-enabled`）。`create_key` 有效期仍未测 |
| 2 | `forfeit_fee` 币种 | 钱；cursor 的 USD 假设无文档背书 |
| 3 | `order_status` 取值枚举 | OrderPresence 三态映射 |
| 4 | `customers` 结构 | 创单必填而文档未展开 |
| 5 | `intention` 三值语义 | 验价即刷 vs 下单前验价可能应声明不同意图 |
| 6 | 是否有 IP 白名单 | 两仓均无记录（艺龙有先例）；本机出口在美国，实测须在腾讯云生产机 |
