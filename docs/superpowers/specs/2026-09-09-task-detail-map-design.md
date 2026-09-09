# 任务详情地图（事故点 + 本单车辆）设计文档

## 概述

管理端派单详情与施救员移动端任务详情，在**已派单及之后**展示地图，标注**事故位置**与**本单绑定施救车辆的当前位置**；待派单管理端继续「事故点 + 附近空闲车选派」。车辆位置约 **20 秒**轮询刷新。

本轮采用：**管理端扩展现有高德图 + `getVehicle`；移动端 H5 嵌入高德，并由任务详情 enrichment 带回本单车坐标**（不给施救员开放全量 `vehicle:query`）。

## 背景与范围

### 已有基础
- 管理端 `DispatchDetail.vue`：**仅 PENDING** 显示地图（事故点 + nearby fresh 车 + 选派）；约 20s 轮询 nearby
- `createVehicleMapIcon`、`loadAmap`、`VITE_AMAP_KEY` 已就绪
- `GET /api/vehicle/{id}`：返回车辆含 `longitude` / `latitude` / `locationUpdatedAt`（需 `vehicle:query`，调度侧可用）
- 移动端位置上报：绑车后约 30s 写回车辆坐标（见车辆实时位置规格）
- 移动端 `pages/task/detail`：**无地图**；`RescuerTaskDetail` 仅有 `order` + `fieldRecord`
- 施救员角色**无** `vehicle:query`，不宜直调管理端车辆 API

### 需求决议
| 项 | 决议 |
|----|------|
| 端侧 | **管理端 + 移动端**均要 |
| 车辆标注 | PENDING = 附近空闲车（现有）；已派后 = **仅本单 `vehicleId` 车辆**当前 GPS |
| 刷新 | 已派后车辆位置约 **20 秒**轮询 |
| 移动端数据 | 任务详情 DTO **enrichment** `assignedVehicle`，不开放全量车辆查询 |
| 地图 SDK | 两端均用**高德 JS**（管理端已有；移动端 H5 新增，共用 Key 配置） |

### 本轮目标
1. 管理端：非 PENDING 且有事故坐标时展示地图；事故点 + 本单车；约 20s 刷新车辆坐标
2. 后端：`RescuerTaskDetail` 增加 `assignedVehicle`（本单绑定车的展示用坐标字段）
3. 移动端任务详情：嵌入高德图；事故点 + 本单车；有坐标时约 20s 重拉详情

### 明确不做
- 轨迹回放、导航路线、驾车路径规划、围栏
- 给施救员开放 `vehicle:query` / 新建独立车辆坐标公开 API
- 改派单状态机或附近选派逻辑（PENDING 行为保持）
- 新建工单页地图改动
- App 原生 map 组件（本轮以 H5 + 高德 JS 为准；非 H5 可后续）

## 技术方案

### 总体架构

| 模块 | 改动 |
|------|------|
| 管理端 | `DispatchDetail`：按状态切换「附近选派图」vs「本单跟踪图」 |
| 后端 | `RescuerTaskDetail.assignedVehicle`；`getTask` 填充 |
| 移动端 | 任务详情地图容器 + 轮询；环境变量高德 Key |

**数据流：**
1. **PENDING（管理端）**：不变 → nearby + 地图选派  
2. **已派后（管理端）**：事故坐标来自工单；车辆坐标 `GET /api/vehicle/{vehicleId}`；约 20s 重拉车辆  
3. **移动端详情**：`GET` 任务详情 → `order` 事故坐标 + `assignedVehicle` 坐标 → 绘点；约 20s 重拉详情  

### 状态与展示矩阵

| 端 | 状态 | 地图 | 标记 |
|----|------|------|------|
| 管理端 | PENDING | 有（现有） | 事故点 + nearby fresh 空闲车 |
| 管理端 | DISPATCHED / ACCEPTED / COMPLETED / ABORTED | 有事故坐标则显示 | 事故点 + 本单车（有坐标时） |
| 管理端 | 任意 | 无 Key / 无事故坐标 | 占位提示，不崩 |
| 移动端 | 任务可见且有事故坐标 | 显示 | 事故点 + 本单车（有则标） |
| 移动端 | 无事故坐标或无 Key | 占位提示 | — |

说明：COMPLETED / ABORTED 仍展示**最后已知**车辆坐标（若有），不做「冻结快照表」；坐标随车辆表当前值变化。

## 后端设计

### `AssignedVehicleBrief`（或内嵌于 `RescuerTaskDetail`）

建议字段：
- `id`（Long）
- `plateNo`（String）
- `longitude` / `latitude`（BigDecimal，可空）
- `locationUpdatedAt`（可空）

### `RescuerTaskDetail`
- 新增 `assignedVehicle`：当 `order.vehicleId` 非空时查 `rescue_vehicle` 填充；无绑定车则为 `null`
- 仅返回本单车，不返回 nearby 列表
- 无新权限码（仍走现有 `rescuer:*` 任务读权限与任务归属校验）

### 管理端
- 复用现有 `GET /api/vehicle/{id}`，**不改**接口契约（已含坐标字段即可）

## 管理端前端

### `DispatchDetail.vue`

**PENDING（保持）**
- 现有 nearby 地图、列表、选派、约 20s nearby 轮询

**非 PENDING 跟踪图**
- 条件：`status !== 'PENDING'` 且工单有 `longitude` / `latitude` 且已配置 `VITE_AMAP_KEY`
- 标记：
  - 事故点：默认标记（与现有一致）
  - 本单车：`order.vehicleId` 存在则 `getVehicle(id)`；有 lng/lat 时用 `createVehicleMapIcon` 黄卡车图标；无坐标文案「车辆暂无位置」
- 轮询：约 **20s** 仅刷新本单车辆坐标并更新 marker；离开页/切回 PENDING 时清除定时器
- `fitView`：尽量兼顾事故点与车辆两点（仅一点时单点居中）
- 无 `vehicleId`：仅标事故点

**生命周期**
- 与现有 PENDING 轮询互斥：状态切换时停一种、启另一种，避免双定时器

## 移动端

### 环境
- 增加高德 Key 配置（如 `VITE_AMAP_KEY`，与管理端同名便于本地共用 `.env`）；无 Key 时地图区提示不可用

### `pages/task/detail`
- 有事故坐标时渲染地图容器（H5 加载高德 JS；可复用/移植管理端 `loadAmap` 思路，保持包体简单）
- 标记：事故点 + `assignedVehicle` 有坐标时的车辆点（图标尽量与管理端黄卡车一致或简化圆点+车牌文案）
- 轮询：有 `assignedVehicle` 时约 **20s** 调用 `getTask` 刷新坐标；`onUnload` / 离开页清除
- 无事故坐标：不显示地图或显示简短占位

### 明确不做（移动端）
- 选派 nearby、改派
- 非 H5 原生 map 专项适配（若 uni 编译到 App，本轮可降级为提示「请在 H5 查看地图」或后续迭代）

## 测试与验收

| # | 标准 |
|---|------|
| 1 | 管理端 PENDING：仍可地图选派附近空闲车，行为与现网一致 |
| 2 | 管理端 DISPATCHED/ACCEPTED：有事故坐标时见地图；有本单车坐标时见黄卡车标记 |
| 3 | 管理端约 20s 车辆标记位置随 `getVehicle` 更新（可用 mock/改库坐标验证） |
| 4 | 本单车无坐标：事故点仍显示 +「车辆暂无位置」类提示 |
| 5 | 移动端任务详情：有事故坐标时见地图；enrichment 带回本单车坐标时可标车 |
| 6 | 移动端约 20s 重拉后车辆标记更新；离开页无残留定时器 |
| 7 | 无 AMap Key：两端不白屏，有明确提示 |
| 8 | 施救员仍无法调用管理端 `/api/vehicle/{id}`（权限不变） |

## 后续衔接（非本轮）
- 驾车路线 / 导航外链
- 完成态车辆位置快照冻结
- App 端 `map` 组件原生实现
- 管理端列表页迷你地图
