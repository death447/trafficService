# 任务详情地图（事故点 + 本单车辆）设计文档

## 概述

管理端与施救员移动端的**任务/工单详情**需展示地图：标注**事故地点**与**车辆位置**。待派单（PENDING）管理端保留现有「附近空闲车 + 选派」；已派单后两端均标注**本单绑定车辆**的当前 GPS，并约 20 秒轮询刷新。

本轮采用：**管理端扩展 `DispatchDetail` 地图模式 + 移动端 H5 嵌入高德 + 任务详情 enrichment 带出本单车坐标**（不开放施救员全量 `vehicle:query`）。

## 背景与范围

### 已有基础
- 管理端 `DispatchDetail.vue`：**仅 PENDING** 展示高德地图（事故点 + 附近 IDLE fresh 车选派，约 20s 轮询 nearby）
- 车辆实时位置：`rescue_vehicle.longitude/latitude/location_updated_at`；移动端约 30s 上报；`GET /api/vehicle/{id}` 需 `vehicle:query`（TOW_DRIVER **无**此权限）
- 移动端 `pages/task/detail`：无地图；`RescuerTaskDetail` 仅含 `order` + `fieldRecord`
- 黄卡车图标：`frontend/src/utils/amap.js` → `createVehicleMapIcon`

### 需求决议
| 项 | 决议 |
|----|------|
| 端 | **管理端 + 移动端**均要 |
| 车辆标注 | PENDING：附近空闲车（选派）；已派后：**仅本单 `vehicleId` 对应车**当前 GPS |
| 刷新 | 已派后约 **20 秒**轮询刷新车辆位置 |
| 移动端取坐标 | 任务详情 **enrichment**（不直调 `/api/vehicle/{id}`） |

### 本轮目标
1. 管理端：非 PENDING 且有事故坐标时展示地图（事故点 + 本单车）；约 20s 刷新车位置
2. 后端：`RescuerTaskDetail` 增加 `assignedVehicle`（本单绑定车快照）
3. 移动端任务详情：H5 高德地图标事故点 + 本单车；约 20s 重拉详情刷新

### 明确不做
- 轨迹回放、导航路线、围栏
- 给施救员开放全量 `vehicle:query` / 新公开车辆坐标列表 API
- WebSocket 推送
- 改派单状态机
- App 原生 `map` 组件（本轮 H5 高德 JS；与管理端视觉一致）
- COMPLETED/ABORTED 历史轨迹（仅展示当时库内最新坐标，若有）

## 技术方案

### 总体架构

| 模块 | 改动 |
|------|------|
| 管理端 | `DispatchDetail`：PENDING 保留；其它状态「跟踪地图」模式 |
| 后端 | `RescuerTaskDetail.assignedVehicle`；`getTask` 填本单车 |
| 移动端 | `task/detail` 嵌入高德；消费 `assignedVehicle` |

**数据流：**
1. **PENDING（管理端）**：不变 — nearby + 选派地图  
2. **已派（管理端）**：`getVehicle(order.vehicleId)` → 地图事故点 + 本单车；约 20s 重拉  
3. **移动端详情**：`getTask` → `order` 事故坐标 + `assignedVehicle`；约 20s 重拉 `getTask`

### 地图模式（管理端）

| 工单状态 | 地图行为 |
|----------|----------|
| PENDING | 现有：事故点 + nearby fresh 车；选派；20s 轮询 nearby |
| DISPATCHED / ACCEPTED / COMPLETED / ABORTED | 跟踪模式：事故点 + 本单车（有 `vehicleId` 且有坐标）；20s 轮询 `getVehicle` |
| 无事故坐标或无 Key | 占位提示，不渲染地图 |

### 本单车辆标记规则
- 有 `vehicleId` 且 `lng/lat` 非空：黄卡车图标（复用 `createVehicleMapIcon`）
- 有 `vehicleId` 但无坐标：地图仅事故点 + 文案「车辆暂无位置」
- 无 `vehicleId`（异常/历史脏数据）：仅事故点
- 新鲜度：本轮**不强制**隐藏过期车（跟踪场景仍标库内最新点；可选在文案展示 `locationUpdatedAt` / 是否超过 180s，非必须）

## 后端设计

### `AssignedVehicleSnapshot`（或内嵌 Map/DTO）
建议字段：
- `id`、`plateNo`
- `longitude`、`latitude`
- `locationUpdatedAt`（可选，便于前端提示）

### `RescuerTaskDetail`
```text
order
fieldRecord
assignedVehicle  // nullable；order.vehicleId 非空时由 RescueVehicleService.findById 填充
```

- 仅填充**本单**绑定车；找不到车则 `assignedVehicle = null`
- 无新权限码；仍走现有施救员任务鉴权（仅本人任务）
- 管理端继续用已有 `GET /api/vehicle/{id}`（`vehicle:query` / 调度相关角色已具备）

## 管理端前端

### `DispatchDetail.vue`
- 条件从「仅 PENDING」扩展为：`hasCoords && amapReady` 时均可展示地图区域
- PENDING：现有 nearby UI + 地图逻辑不变
- 非 PENDING：简化面板（标题如「位置跟踪」）；加载/轮询 `getVehicle(vehicleId)`；`fitView` 兼顾事故点与车点
- 离开页 / 切换工单：清跟踪轮询与 nearby 轮询（互斥）
- 图标与 PENDING 附近车一致（黄卡车）

## 移动端

### 环境
- 增加 `VITE_AMAP_KEY`（与管理端同 Key 或独立 Key）；无 Key 时文案占位
- 复用或移植高德加载逻辑（可抽轻量 `utils/amap.js`，或内联 script 加载；与管理端同安全密钥配置方式）

### `pages/task/detail.vue`
- 有事故 `lng/lat` 时渲染地图容器
- 事故点默认标记；有 `assignedVehicle` 坐标则标黄卡车（实现可简化为高德默认/自定义 icon，视觉尽量接近管理端）
- `onShow` 拉详情；有车坐标时约 20s `setInterval` 重拉；`onHide`/`onUnload` 清定时器
- 无坐标：不展示地图或展示「暂无位置」提示

## 测试与验收

| # | 标准 |
|---|------|
| 1 | PENDING 管理端：附近车选派地图行为与现网一致 |
| 2 | DISPATCHED/ACCEPTED 管理端：可见事故点 + 本单车；约 20s 车点更新（上报变化后） |
| 3 | 本单车无坐标：仅事故点 +「车辆暂无位置」 |
| 4 | 移动端任务详情：事故点 + 本单车；约 20s 刷新 |
| 5 | 施救员无需 `vehicle:query`；详情带 `assignedVehicle` |
| 6 | 无 `VITE_AMAP_KEY`：两端友好占位，不白屏报错 |

## 后续衔接（非本轮）
- 导航/路线规划
- 过期位置弱化样式（半透明/灰标）
- 原生 App `map` 组件
- WebSocket 实时推送
