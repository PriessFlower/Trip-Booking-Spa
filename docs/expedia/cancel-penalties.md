# Expedia Rapid `cancel_penalties` 实测形态与转换判据

> **来源**：test.ean.com `/v3/properties/availability` 实测采样，**2026-08-28**。
> 生产凭据（`/opt/trip-booking-spa/app.env`）当日仅在 test.ean.com 有效——对
> api.ean.com 打同签名请求返回 403 `request_unauthorized`，且生产
> `EXPEDIA_API_HOST=https://test.ean.com`。**线上链路当下消费的就是本文形态**；
> 切换 api.ean.com 时须按本文方法重采并回写。
> **方法**：目录表 `expedia_property_content` 按国别分层随机 30 家 × 7 国（ID/JP/KR/MY/SG/TH/VN，
> 共 210 家），住期 T+1 / T+13 / T+30 各 1 晚，occupancy=2、CNY，`rate_plan_count=250`，
> 共 2,154 条含罚金 rate。单住期比例不可外推到别的住期（T+1 与 T+30 分布差异巨大，见下）。

## 1. 段语义

`cancel_penalties[]` 每段是**时间窗**：在 `[start, end)` 内取消，收该段罚金。
时刻带毫秒与 UTC 偏移（如 `2026-07-19T03:00:00.000+08:00`），偏移即酒店当地时区。

罚金载体三选一：`amount`（定额，请求币种）、`percent`（如 `"100%"`）、`nights`（扣几晚）。

## 2. 采样事实（判据的依据）

| 事实 | T+1 | T+13 | T+30 |
|---|---|---|---|
| 含罚金 rate 数 | 457 | 792 | 905 |
| 最早段 `start` ≤ 当下（罚金窗已开） | 322（70%） | 258（33%） | 279（31%） |
| 其中 `refundable=false` | 322（全部） | 258（全部） | 279（全部） |
| `percent` 段中 100% 占比 | 247/251 | 504/513 | 569/587 |
| 多段阶梯（nseg=2，如 50%→100%） | 2 | 7 | 16 |

- **`start` ≤ 当下 ⇔ `refundable=false`，采样内零例外**：罚金窗已开=从下单那刻起取消就有罚，
  没有免费期。对这类 rate 垫「免费头段」就是把不能免费退说成能退（方向与艺龙 26,011 事故相反且更糟）。
- `percent` 取值仅见 10%/30%/50%/100%，**100% 占 96%**（=全款，语义同艺龙 CutType=4）。
- 载体只见 `percent` 与 `nights`；`amount` 段与三载体皆缺的段**均未出现**（转换仍保留处理分支，
  表外形态一律 UNKNOWN，R-5.4）。
- `cancel_penalties` 缺席未出现（2,154 条全带）；`nonrefundable_date_ranges` 偶发（46 条），
  调用方已单独处理为不可退。

## 3. 转换判据（ExpediaProductKeyDeriver.convertCancelPolicy）

1. **免费头段只在最早段 `start` 晚于当下时垫**，截止=该 `start`；
2. **逐段全部转出**（旧实现只取 start 最早一段）：`percent`→`DEDUCT_BY_PERCENT`（100% 即 value=100，
   不许丢段）、`amount`→`DEDUCT_BY_AMOUNT`、`nights`→`DEDUCT_DAY_NIGHT`、罚 0→`NO_DEDUCTION`；
3. **载体不认识 / 时间解析失败 → 空列表=UNKNOWN**（R-5.4），不得兜成不可退；
4. 三分类：有免费窗=FREE_CANCELLABLE；每段确定罚≥全款（percent≥100）=NON_REFUNDABLE；
   其余（按晚/定额/比例<100，判不出全款）=UNKNOWN——可售不进目录（`isCatalogEligible`）。
   该分类随住期可变：免费窗过期后同一卖法如实转不可退，与 FREE 的窗口性同理。

守护测试：`ExpediaCancelPolicyConvertTest`（报文形态取自本采样原样）。

## 4. 判据回归验证（与供应商自标结论交叉比对）

把 §3 判据在 2,154 条采样上重跑，与同一响应里供应商自标的 `refundable`（该 rate 可否退）逐条比对：

| §3 判据结果 | `refundable=true` | `refundable=false` |
|---|---:|---:|
| FREE_CANCELLABLE | 1,295 | 0 |
| NON_REFUNDABLE | 0 | 756 |
| UNKNOWN | 0 | 103 |

对角线之外为 0，**采样内零分歧**。旧实现称「可免费取消」而新判据改判的 813 条中，
710 条改判不可退、103 条改判不确定，供应商侧**全部** `refundable=false`；改判为
不可退者当中 `refundable=true` 的有 **0 条**，即无误伤——不会把可退的卖成不可退而少卖。

`refundable` 仅两态，表达不了 UNKNOWN，也不含罚金额度与起算时刻，故只用作判据的
**校验尺**，不用作实现；对客条款展示仍须从 `cancel_penalties` 逐段转出。

UNKNOWN 的 103 条按住期分布 T+1 96 / T+13 1 / T+30 6，成因为按晚扣 92、比例<100% 9、
混合载体 2。建档查价占位住期默认 +9 天（`ExpediaProductMappingService.syncProducts`），
落在 T+13 档附近，故 UNKNOWN 对建档覆盖的实际影响约 0.1%。按晚扣一类待
`CancelPolicy.deductsFullPrice(totalCents)` 合入后可进一步判定（该方法当前尚未合入，
本次转换不依赖它）。
