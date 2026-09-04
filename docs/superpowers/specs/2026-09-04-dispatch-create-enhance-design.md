# 新建工单增强：人员预填与地图搜索定位设计文档

## 概述

完善调度员「新建工单」页：补齐事故地点、施救原因、调度员、施救员、施救车辆表单项；高德地图支持**当前位置默认视野**、**地点模糊搜索**、点击/拖拽选点。提交后工单仍为 `PENDING`；施救员/车辆仅为预填，不占用车辆、不自动派单。

## 背景与范围

### 已有基础
- `DispatchCreate.vue`：原因、地址、经纬度、点击选点地图
- `DispatchOrderService.create`：强制 `PENDING`，调度员取 JWT，清空 `vehicleId`/`rescuerId`
- `assign` 在详情页完成真正派单
- 高德 Key：`VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE`

### 决策摘要
- 落库策略：**A** — PENDING + 可选预填人员车辆
- 定位失败：**C** — 中国视野，靠搜索或点击
- 方案：**1** — 增强创建页 + 创建/更新接口允许预填字段

### 本轮目标
1. 新建表单字段完整（上表）
2. 地图：定位成功居中当前位置；失败中国视野；模糊搜索落点；点击/拖锚
3. 后端 create/update 接受预填且保持 PENDING、不 markBusy
4. 详情页对预填空闲车默认选中

### 明确不做
- 创建即 DISPATCHED / 占车
- 强制施救员+车辆必填
- 创建页叠围栏/轨迹
- 新 UI 库

## 技术方案

### 前端
- 改造 `DispatchCreate.vue`：调度员/施救员/车辆下拉（复用用户列表按角色过滤、车辆 list）
- 扩展 `amap.js`：`createPickerMap` 增加
  - `Geolocation`（成功：zoom≈15 居中；**不**自动写入事故点，除非后续点击/搜索）
  - 失败：中国范围（zoom 约 4–5，中心大致 [105, 35]）
  - `AutoComplete` 或 `PlaceSearch` 绑定搜索输入，选中后 Marker + `onPicked`
  - Marker 可拖拽，拖完逆地理
- 无 Key：隐藏地图，手填坐标

### 后端
- `create(order, currentUserId)`：
  - `dispatcherId`：请求体有则用（须为 DISPATCHER 或 ADMIN 角色用户），否则 `currentUserId`
  - 可选 `rescuerId`（须 TOW_DRIVER）、`vehicleId`（须存在）；**不**校验 IDLE、**不** markBusy
  - 状态固定 PENDING；清空 abort/dispatched/completed
- `update`（仅 PENDING）：允许改地址/原因/坐标及上述三预填字段
- 权限码不变（`dispatch:add` / `dispatch:edit`）

### 详情联动
- PENDING 加载 nearby 后：若 `order.vehicleId` 存在且对应项在列表且空闲 → `selectedVehicleId` 默认该值
- 否则提示预填车不可派，不选中
- 确认派单时 `assign` 传入预填或用户另选的 `rescuerId`（与现有 Assign 请求对齐）

## 数据流

```
打开新建 → 拉用户/车辆下拉 → 地图定位或中国视野
搜索/点击/拖拽 → 地址+坐标
提交 → POST /api/dispatch（PENDING + 可选预填）
→ 跳转详情 → 默认选预填空闲车 → 用户确认派单
```

## 验收

| # | 标准 |
|---|------|
| 1 | 表单含原因/地点/调度员/施救员/车辆；调度员默认本人 |
| 2 | 定位成功居中当前位置；拒绝则中国视野并可搜索/点击 |
| 3 | 模糊搜索结果与 Marker/地址/坐标一致 |
| 4 | 仅必填提交 → PENDING，预填空 |
| 5 | 带预填提交 → PENDING，库有 id，车辆仍 IDLE |
| 6 | 详情默认选中预填空闲车；派单后 DISPATCHED/BUSY |

## 测试要点
- create 预填不调用 `markBusy`
- 非法角色 dispatcher/rescuer 拒绝
- 不存在的 vehicleId 拒绝
- 前端无 Key 可手填提交
