# Expedia Rapid B2B（SA）上线要求

> **定位**：B2B 代理商后台（`b2b` 包 + `trip-booking-b2b-web` 前端）过 Site Review 的唯一依据。
> **来源**：`B2B-SA-Requirement-List_2026.docx`（官方检查表，与本文件同目录）。
> 下载地址 `https://a.travel-assets.com/documentation-hubs/prod/rapid/latest/en-US/resources/assets/B2B-SA-Requirement-List_2026.docx`，
> 经 [Lodging launch requirements](https://developers.expediagroup.com/rapid/setup/launch-requirements/lodging-launch-reqs) 的
> 「B2B implementation specifics」一节给出。**查阅日期 2026-09-03。**
> **状态**：第 1 节为官方原文逐条转录；第 2 节的「我方现状」是 2026-09-03 的实测与代码走查结论，会过期。

---

## 0. 为什么单独立一份

**官方按业务模式发不同检查表，B2B 与 B2C 不是同一份。** 此前项目内唯一的清单是
`expdia` 仓的 `EXPEDIA_LAUNCH_REQUIREMENTS_CHECKLIST.md`，其开头自述「默认业务范围：B2C 单独酒店预订（非套餐）」——
**它不能作为 B2B 的依据**，两份的条目集合与口径都不同。差异见 §3。

**「SA」= Standalone（单卖）。** 本清单**不含** `AP4`（房型层标注套餐价）与 `BP11`（代理确认套餐组合销售规则）——
那两条属打包价，另有一份 PKG 清单。据此：

> 只要查价一直发 `sales_environment=hotel_only`，就只需过本清单；
> 一旦取 `hotel_package`，打包价那一整套要求随即生效。

我方合同车道名为 `B2B_SA_PKG_MOD_AGENT`，SA 与 PKG 都在其中，故「碰不碰打包价」是我方的选择，不是合同强制。
车道与价格类型的关系见 `价格类型.md`。

---

## 1. 官方清单（逐条转录）

原文为三列表：区块／标记、条目、Feedback（空栏，供填写反馈）。共 7 个区块。

**`B2B SA Specific`** 标记出现在三条上——`GR3`、`BP5`、`ER6`。这三条有 B2B 专属口径，
**不得照搬 B2C 站的做法**。

**`BP6` 在原文中不存在**（`BP5` 直接跳到 `BP7`），此处照原样保留，不要以为是转录漏了。

### General Requirements

| 条目 | 原文 |
|---|---|
| GR1 | Use of Expedia Group names and Logos |
| GR2 | Links to the Expedia Group Terms & Conditions URL to be displayed |
| **GR3** ⭐ | Downstream agents must agree to Expedia Group's Terms and Conditions for accessing Expedia Group inventory |
| GR4 | Expedia Group MOR - Proper use and display regarding credit card regulations |
| GR5 | Expedia Group MOR or Property Collect - Evidence of PCI compliance supplied for applicable partners |

### Search Page

| 条目 | 原文 |
|---|---|
| SP1 | Where affiliate allows children to be included in bookings, proper messaging and input of child ages is implemented |

### Hotel/Room Availability

| 条目 | 原文 |
|---|---|
| AP1 | Bed type descriptions are present on each room |
| AP2 | Non-refundable flag is clearly visible |
| AP3 | Display Check-In & Special Check-in instructions |

### Booking Page

| 条目 | 原文 |
|---|---|
| BP1 | SSL encryption is present for personal data |
| BP2 | Display Check-In & Special Check-in instructions |
| BP3 | Cancellation policy & non-refundable tag clearly displayed |
| BP4 | Display charges due at the property separately within the price breakdown |
| **BP5** ⭐ | Price Display must include the total price and breakdown of the taxes and fees |
| BP7 | If applicable, child ages must be reiterated |
| BP8 | Expedia Group MoR or Property Collect: State when payment will be taken from the end traveler |
| BP9 | Compliance with European Economic Area Regulations: Payment Services Directive 2 (PSD2) |
| BP10 | Expedia Group MoR – Payment processing location displayed on checkout pages |

### Confirmation Page

| 条目 | 原文 |
|---|---|
| CP1 | Price Display must include the total price and taxes and fees if a breakdown is provided |

### Confirmation Email

| 条目 | 原文 |
|---|---|
| ER1 | Itinerary IDs displayed properly |
| ER2 | Customer support to be clearly displayed, including links to online customer service tools |
| ER3 | Bed type descriptions are present on each room |
| ER4 | Display Check-In & Special Check-in instructions |
| ER5 | Display charges due at the property separately within the price breakdown |
| **ER6** ⭐ | Price Display must include the total price and breakdown of the taxes and fees |

### Technical / Sanctions

| 条目 | 原文 |
|---|---|
| TR1 | Provide unique Affiliate Reference ID with each booking request |
| TR2 | Provide the traveler Country Code with each request |
| TR3 | Billing Information —— TR3a) Payor Name · TR3b) Billing Country · TR3c) Billing Zip |
| TR4 | Provide the customer email address or OR monitored email mailbox |
| TR5 | Provide accurate Traveler Information —— TR5a) Traveler Name · TR5b) Traveler Phone Number |
| TR6 | Multi-room bookings |
| TR7 | Rapid Error Handling recommendations |

---

## 2. 我方现状（2026-09-03 实测）

落点两处：后端 `b2b` 包（端点 `/b2b/**`）、前端 `trip-booking-b2b-web` 仓。
「数据已有」指后端接口已返回该字段、只是前端未渲染——这类补起来只动前端。

| 条目 | 现状 | 说明 |
|---|---|---|
| GR1 | 待核 | 站上有「Expedia 行程号」「Expedia 下游代理协议」字样。展示来源与品牌宣传是否算违反，未查官方口径 |
| GR2 | **缺** | 只放了下游代理协议链接（GR3），Expedia 条款链接没有 |
| GR3 ⭐ | **已具备** | 协议弹窗 + 签署留痕（`b2b_agreement_accept` 记账号／版本／时间／IP／UA）；后端下单前复查，未签回 403 |
| GR4 / GR5 | 待确认 | 我方 `billing_terms=EAC`、`payments.type=affiliate_collect`，支付链路不在本站。适用性需向客户经理确认 |
| SP1 | 部分 | 有儿童年龄输入框，**无提示文案** |
| AP1 | 已具备 | 房型表逐条列床型描述 |
| AP2 | 已具备 | 「可退／不可退」标签 |
| AP3 | **缺（数据已有）** | `checkinPolicy`（`begin_time`/`end_time`/`min_age`/`instructions`）、`policies.know_before_you_go` 均已返回，前端未渲染 |
| BP1 | 生产已配 | nginx 虚拟主机 TLS + 80 全量跳转；本地调试走 HTTP |
| BP2 | **缺（数据已有）** | 同 AP3，结账弹窗未展示入住说明 |
| BP3 | 部分 | 只有可退标签；`cancelPenalties`（罚金时间窗）与 `nonrefundableDateRanges` 已返回但未展示 |
| BP4 | **缺（数据已有）** | `totals.property_fees` 与 `fees.optional` 已返回，未在价格明细中单列 |
| BP5 ⭐ | 部分 | 有含税总额与税费一行；**B2B 专属口径未核** |
| BP7 | **缺** | 结账页未复述儿童年龄 |
| BP8 / BP9 / BP10 | **需重新确认** | `expdia` 仓清单记「2026-08-11 按 Expedia 要求确认本期无需实现」——**那是 B2C 口径**下的确认，本清单三条在列 |
| CP1 | 部分 | 下单后直接跳订单详情（含总额），无独立确认页 |
| ER1 | 已具备 | 订单列表与详情均展示 `itineraryId`，详情另列每间房的 Expedia 确认号 |
| ER2 | **缺** | 无客服入口、无在线客服工具链接 |
| ER3 / ER4 / ER5 / ER6 ⭐ | **缺** | **整个「Confirmation Email」区块没有落点**——既无确认邮件也无电子凭证页。这是当前最大的空白 |
| TR1 | 已具备 | 我方单号 `TB<时间戳><随机>` 作 `affiliate_reference_id`，下单结果不确定时凭它反查 |
| TR2 | 已具备 | `country_code=CN`（通道层写死，见 `价格类型.md` §5.1） |
| TR3 | 待确认 | 账单信息取自平台固定联系人 |
| TR4 | 过渡方案 | 对 Expedia 用平台固定邮箱；该邮箱一经使用必须永久稳定（反查行程要求下单时的原始邮箱） |
| TR5 | 过渡方案 | 旅客真实姓名与电话只落本地 `bff_order`，不出境 |
| TR6 | **缺** | 前端下不了逐间不同人数；后端与网关支持 `occupancy` 重复下发 |
| TR7 | 已具备 | 下单三态 + 重复参考 ID 反查确证 + 同句柄幂等回放，均在 `bff` 包并由 `b2b` 复用 |

---

## 3. 与 B2C 那份清单的差异

`expdia` 仓 `EXPEDIA_LAUNCH_REQUIREMENTS_CHECKLIST.md` 是 B2C 口径，作为 B2B 依据会错在三处：

1. **少了整块「Confirmation Email」的强制性。** 那份把「确认邮件或电子凭证」列为 P1
   建议项；本清单是六条（ER1–ER6）的独立区块。
2. **`BP8/BP9/BP10` 的「不适用」结论不可移植**（见 §2）。
3. **多了 `AP4`/`BP11`。** 那两条属打包价，不在本 SA 清单内（见 §0）。

---

## 4. 尚未确认

1. `GR1` 对「展示供应商行程号与协议名称」的口径——是否与「不得使用 Expedia 名称／Logo」冲突。
2. `BP5` 与 `ER6` 的 **B2B 专属口径**具体差别：官方页面对 AP1／AP2／BP5 各给了
   "Example user interface - B2B" 配图，配图内容未取回，需逐张比对后再定前端形态。
3. `GR4`／`GR5`／`BP8`／`BP9`／`BP10` 在我方 `affiliate_collect` 形态下的适用性。
4. 业务模式的正式申报：B2C 还是 B2B。`expdia` 仓清单第 12 节「确认业务模式」至今未打勾，
   而合同车道自接入起就是 B2B（`B2B_SA_PKG_MOD_AGENT` / `agent_tool`）。
