# 新建工单多选施救车并即时派单 — 设计文档

## 概述

新建工单时，调度员可**多选**施救车辆；若选了车，保存时在同一事务内**立刻派单**（复用现有 `assign`）。可不选车，仅创建 `PENDING` 工单。

## 已确认决策

| 项 | 选择 |
|----|------|
| 选车效果 | 创建即派（非仅预填） |
| 车辆是否必填 | 否；空选 = 仅待派 |
| 实现路径 | `POST /dispatch` 接受 `vehicleIds`，插入后同事务 `assign` |
| 详情页加派 UI | 本期不改（仍可单车加派） |

## 背景

- 已有 `dispatch_vehicle_assignment` 与 `assign(orderId, List<Long> vehicleIds)`。
- 新建页目前单选 `vehicleId`，仅预填工单列，**不建赋值、不占车**。
- 详情「派车/加派」才真正派单；后端 `/assign` 已支持 `vehicleIds` 批量。

## 行为

1. **无车**：插入工单，`status=PENDING`，无 assignment。
2. **有车（1+）**：插入工单后对去重后的 `vehicleIds` 调用现有 `assign`：每车一条 `DISPATCHED` assignment、`markBusy`、刷新工单状态；主表 `vehicle_id`/`rescuer_id` 指向最后一辆（与现加派一致）。
3. **任一轮车辆不可派**（离线、未绑施救员等）：整笔事务回滚，前端展示错误。
4. **兼容**：仅传旧字段 `vehicleId` 时合并进 `vehicleIds`；二者并存时去重合并。

创建成功后仍返回 `findById`（含 `assignments`）。

## 接口

### `POST /dispatch`

Body 在现有工单字段基础上增加**非表字段**：

| 字段 | 说明 |
|------|------|
| `vehicleIds` | `Long[]`，可选；空/缺省不派车 |
| `vehicleId` | 可选；与 `vehicleIds` 合并 |

实体：`DispatchOrder` 增加非持久化字段 `List<Long> vehicleIds`（MyBatis 不映射；仅 JSON 入参）。

`create` 流程调整：

1. 先合并 `vehicleId` + `vehicleIds` → 去重列表 `toAssign`，并清空入参上的预填车/员（避免 insert 写入半成品镜像列）。
2. `applyDispatcherAndPrefill` **仅**校验调度员；不再根据单车预填写 `rescuerId`。
3. 归一化事故车、插入工单（初始 `PENDING`，`vehicle_id`/`rescuer_id` 为空）。
4. `toAssign` 非空 → 同事务 `assign(orderId, toAssign)`。

不改 `POST /dispatch/{id}/assign`。

## 前端（`DispatchCreate.vue`）

- 救援车辆：单选改为**复选列表**（可选车集合与排序不变：空闲靠前、任务中靠后）；支持清空已选。
- 地图标记：点击**切换**选中；已选有视觉态；可多选。
- 施救员：只读汇总——按已选车列出「车牌 → 绑定施救员」。
- 提交：`vehicleIds: number[]`（可 `[]`）；不再依赖单独预填 `rescuerId`。
- 成功：跳转详情，可见多条已派车辆。

## 测试

- 单测：`create` 无车 → PENDING、无 assignment。
- 单测：`create` + 多车 → 多条 assignment、工单非 PENDING、车辆 BUSY。
- 单测：多车中一辆非法 → 抛错且无残留工单（事务回滚）。
- 手测：新建多勾/地图多点 → 详情列表正确；不选车可提交。

## 明确不做

- 详情页派车改为多选 UI
- 移动端/事故车模型变更
- 废弃工单表 `vehicle_id` 列

## 错误处理

沿用 `requireAssignable` / `assertHasRole` 文案；创建接口 catch 后 `Result.error(message)`。
