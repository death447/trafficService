# 工单 ↔ 施救车多对多与推荐排序设计文档

## 概述

将「工单—施救车辆」从工单上单一 `vehicle_id` 改为**多对多**：一张工单可同时派多辆施救车，一辆施救车可挂多张工单。每辆车在工单上有独立进度（接单 → 签到 → 采集 → 完成）；**全部赋值终态且至少一车完成**后整单 `COMPLETED`。

推荐原则：**原则上一车一单**，但不硬限制——附近推荐纳入已有活跃单的车，并将其**推荐度置为最低**，调度员仍可强派。

本期**不做**车辆载车容量（`capacity`）字段、不做微信小程序适配。

## 背景与范围

### 已有基础
- `dispatch_order.vehicle_id` / `rescuer_id`：一单一车；派单要求车辆 `IDLE`，派后 `BUSY`
- 施救过程字段（接单/签到等）挂在工单行上
- `GET /api/vehicle/nearby`：仅 `IDLE`；片区优先 + 距离；区分新鲜/陈旧定位
- 移动端按工单 id 接单/签到/完成；任务列表按 `rescuer_id` 过滤

### 本轮目标
1. 新表 `dispatch_vehicle_assignment` 承载车×单赋值与本车状态机
2. 工单级状态由赋值行汇总；支持加派
3. 迁移现有 `vehicle_id` 数据为赋值行；过程字段下沉
4. nearby 推荐：可派含 `BUSY`；排序片区 → 无活跃赋值优先 → 距离/新鲜度
5. PC 详情多车列表与加派；移动端按「本人赋值行」操作

### 明确不做
- `rescue_vehicle.capacity` 及硬容量闸门
- 微信小程序适配
- 交警评价改多车模型（仍一单一评）
- 一单拆多子工单、协助车双轨模型

## 已确认决策

| 项 | 选择 |
|----|------|
| 关系 | 真多对多（关联表） |
| 整单完成 | 所有赋值终态，且至少 1 行 `COMPLETED` |
| 已有活跃单的车 | 可派；推荐垫底 |
| 施救流程 | 每车独立进度 |
| `PENDING` 预填车辆 | 不占「活跃赋值」（正式派单才建赋值行） |
| 载车容量字段 | 本期不做 |

## 技术方案

### 总体架构（方案 1）

```
dispatch_order (工单级事故信息 + 汇总 status)
       │ 1
       │
       │ N
dispatch_vehicle_assignment (vehicle_id, rescuer_id, 本车 status, 过程时间)
       │
       └── dispatch_field_record / dispatch_media（可选 assignment_id）
```

### 数据模型

#### `dispatch_vehicle_assignment`

| 列 | 说明 |
|----|------|
| `id` | PK |
| `dispatch_order_id` | 工单 |
| `vehicle_id` | 施救车 |
| `rescuer_id` | 派单时车辆绑定司机；操作权限按此校验 |
| `status` | `DISPATCHED` → `ACCEPTED` → `COMPLETED`；可 `REJECTED` / `ABORTED` |
| 过程字段 | 接单/签到/完成时间、签到经纬度等（从工单下沉） |
| `create_time` / `update_time` | |

约束建议：
- 同一工单同一车辆不得存在两行非终态赋值（或 `uk (dispatch_order_id, vehicle_id)` 若终态也可保留历史则改为「活跃唯一」部分索引/应用层校验）
- 实现采用：**应用层禁止**对同一工单重复派同一车（含已终态历史行时允许再派需产品确认——默认**不允许**同单同车第二次，除非先删除历史；首期简单规则：**同单同车仅一行**，`uk_order_vehicle`）

#### `dispatch_order`

- 保留事故信息、地址、当事人、车牌车型、工单级 `status`
- `vehicle_id` / `rescuer_id`：迁移后**停止作为写路径**；可读兼容可取「首个活跃赋值」或第一行，最终可在后续迁移中废弃列
- 工单上原施救过程字段：迁移进赋值行后不再更新

#### 媒体 / 现场

- `dispatch_field_record`、`dispatch_media` 增加可空 `assignment_id`
- 新采集默认挂当前施救员赋值；历史可空（归属整单）

#### 车辆 `status`

- 存在任意工单上 `DISPATCHED`/`ACCEPTED` 赋值 → `BUSY`
- 否则（非人工 `OFFLINE`）→ `IDLE`
- **可派性与 `BUSY` 分离**：`BUSY` 仍可加派到其它/同单（同单同车除外）

### 状态汇总

**赋值行**
- 派车 / 加派 → 建行 `DISPATCHED`，`rescuer_id` = 车辆当时 `driver_user_id`（必须已绑定）
- 接单 → `ACCEPTED`；退单 → `REJECTED`
- 本车完成 → `COMPLETED`；单车或整单中止 → 相关行 `ABORTED`

**工单级 `status`（与现枚举对齐）**
| 条件 | 工单 status |
|------|-------------|
| 无赋值行 | `PENDING` |
| 存在 `DISPATCHED`（且无「全部已接」） | `DISPATCHED` |
| 无 `DISPATCHED`，存在 `ACCEPTED` | `ACCEPTED` |
| 全部终态，且 ≥1 行 `COMPLETED` | `COMPLETED` |
| 全部为 `REJECTED`/`ABORTED`（无完成） | 回到 `PENDING`（可再派）；若调度显式整单中止且无完成 → `ABORTED` |

约定：最后活跃车退单且无其它完成车时，工单回 `PENDING`。

整单 `COMPLETED` 仅在「全部赋值终态 ∧ 至少一车 `COMPLETED`」时自动达成（最后一车 complete 时触发汇总）。调度员「完成」按钮：若仍有非终态赋值则拒绝或仅作强制（首期：**拒绝，提示先处理未完成车辆**）。

### 派单 / 加派规则

- `PENDING` 与进行中均可加派
- 校验：车辆非 `OFFLINE`；已绑司机；同单未派过该车；**不要求** `IDLE`
- 建单预填车辆：仅 UI/工单草稿意向，**不建赋值、不占活跃**；正式 assign 再建行

### 附近推荐排序

`GET /api/vehicle/nearby` 变更：

1. 候选：非 `OFFLINE`、已绑司机、有坐标策略与现网一致（新鲜/陈旧分桶可保留）
2. 每车附加 `hasActiveAssignment`（或 `activeAssignmentCount`）：是否存在 `DISPATCHED`/`ACCEPTED` 赋值
3. 排序（组内再按距离升序；新鲜桶优先于陈旧桶可保持现状）：
   1. `inMatchedDistrict` 降序
   2. **`hasActiveAssignment` 升序**（无活跃单在前，已有任务垫底）
   3. `distanceMeters` 升序
4. 前端标注「已有任务」，仍可选

### 接口要点

| 接口 | 变更 |
|------|------|
| `POST /api/dispatch/{id}/assign` | 支持 `vehicleId` 或 `vehicleIds[]`；进行中可加派 |
| `GET /api/dispatch/{id}` | 响应含 `assignments[]` |
| `POST /api/dispatch/{id}/abort` | 整单：活跃赋值 → `ABORTED`；可选后续「单车中止」`.../assignments/{aid}/abort` |
| `GET /api/vehicle/nearby` | 见上 |
| 移动 `.../tasks/{id}/accept\|reject\|checkin\|scene\|park\|complete\|media` | 按当前用户匹配该工单赋值行；无匹配则 403 |
| 移动任务列表 | 按赋值 `rescuer_id` 列出（一车多单多条） |

### PC / 移动 UI

**PC**
- 详情：已派车辆表（车牌、司机、本车状态）；加派；附近列表按新序，已有任务车靠后并角标
- 建单：可选多车意向；保存后仍 `PENDING`，需 assign 生效

**移动**
- 操作仅影响本人赋值；完成后若整单未结束，提示「整单尚有其他车辆未完成」
- 同单其他车摘要：本期可选，默认不做

### 迁移

1. 建 `dispatch_vehicle_assignment`
2. 对已有 `vehicle_id IS NOT NULL` 的工单插入一行，拷贝 `rescuer_id` 与过程字段，status 映射自工单 status
3. 媒体/现场表加 `assignment_id`（可空）
4. 应用切换读写路径后再视情况废弃工单列写路径

### 错误与边界

- 未绑司机、重复派同车、操作非本人赋值、删车时仍有非终态赋值 → 明确错误
- 一车多单：任务列表自然多条；推荐垫底不禁止

### 测试要点

- 一单派两车：各自接单/完成；仅一车完成时工单未 `COMPLETED`；两车都完成 → `COMPLETED`
- 一车两单：均可接；nearby 中该车 `hasActiveAssignment=true` 且排在无任务车之后
- 退单至无车 → 工单 `PENDING` 可再派
- 迁移后旧单移动端仍能按赋值操作

## 非目标回顾

- 载车容量字段与硬上限
- 小程序、交警多评价、子工单拆分
