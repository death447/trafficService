# 施救员详情：车辆坐标自动签到与地图 500m 圈设计文档

## 概述

在施救员移动端任务详情页，改为用**派单车辆最近上报坐标**相对事故点判距；位置新鲜且 ≤500m 时**自动签到**（无需点按钮）。详情页车辆位置 **10 秒**轮询，地图同步更新救援车标记，并以事故点为圆心绘制 **500m** 范围圆。保留手动签到兜底；去掉手机 GPS「签到」按钮。

判距与新鲜度在**前端**完成，仍调用现有 `POST /api/mobile/rescuer/tasks/{id}/checkin`（`mode=AUTO`）；后端 500m 校验与状态机不变。

## 背景与范围

### 已有基础
- 详情 `GET /api/mobile/rescuer/tasks/{id}` 返回 `order` + `assignedVehicle`（含 `longitude` / `latitude` / `locationUpdatedAt`）
- 车辆位置由 H5 绑车后约 30s 上报；管理端 nearby 新鲜度常量 `LOCATION_STALE_SECONDS = 180`
- 详情地图：事故点标记 + 救援车标记；现约 **20s** 静默轮询 `getTask` 并 `syncVehicleMarker`
- 签到：点「签到」取手机 GPS → `AUTO`；无定位可 `MANUAL`+原因；后端 AUTO 要求相对事故点 ≤500m

### 需求决议
| 项 | 决议 |
|----|------|
| 自动签到触发 | 进详情后自动；无需点「签到」 |
| 位置来源 | 工单派单车辆 `assignedVehicle` 上报坐标（非当次手机定位） |
| 新鲜度 | `locationUpdatedAt` 距今 ≤ **180s**；过期/缺失不自动签 |
| 距离 | 与事故点 Haversine ≤ **500m** |
| 轮询 | 详情页 **10s** 刷新任务（含车辆坐标）；未签到且满足条件时每次轮询可再试自动签到；签成后不再试 |
| 地图 | 同步更新救援车位置；事故点画 **500m** 圆 |
| 兜底 UI | 去掉手机签到按钮；保留「手动签到（需填写原因）」 |
| 实现路径 | **前端判距** + 现有 checkin API（方案 1） |

### 本轮目标
1. `ACCEPTED` 且未签到时，按车辆新鲜坐标自动 `AUTO` 签到
2. 轮询间隔改为 10s，地图车标随数据更新
3. 事故点 500m 范围圆
4. UI：去掉手机签到，保留手动签到

### 明确不做
- 新后端「服务端读车自动签到」专用接口
- 改派单状态机 / 后端新鲜度强制（新鲜度仅前端）
- 轨迹、WebSocket
- 用「我的绑车」车辆替代工单 `vehicleId`（以派单车辆快照为准）

## 技术方案

### 数据流
1. 施救员打开/回到详情 → `load` → 得 `order` + `assignedVehicle`
2. 初始化/复用地图：事故点标记 + **500m Circle** + 救援车标记（有坐标时）
3. `tryAutoCheckin()`：未签到且条件满足 → `checkin({ mode:'AUTO', lng, lat })` → 成功 toast 并 `load`
4. `setInterval` **10s** → `load({ silent:true })` → 更新车标；若仍未签到再 `tryAutoCheckin()`
5. 用户可随时「手动签到」走 `MANUAL`

### 自动签到条件（前端）
同时满足：
- `order.status === 'ACCEPTED'`
- `order.checkedInAt` 为空
- 事故 `order.longitude/latitude` 有效
- `assignedVehicle.longitude/latitude` 有效
- `assignedVehicle.locationUpdatedAt` 存在且距今 ≤ 180 秒
- Haversine(事故, 车辆) ≤ 500

不满足：不调接口、不弹失败 toast（避免轮询刷屏）。  
进行中加内存锁，防止并发重复提交；成功后置「已尝试成功」标志，本页周期内不再调 AUTO。

### 距离与新鲜度
- 前端 Haversine（可复用小工具函数，或与现有地图逻辑同文件）
- `LOCATION_STALE_SECONDS = 180`（与 `RescueVehicleService` 常量对齐，移动端本地常量即可）
- 后端仍对 AUTO 请求坐标做 ≤500m 校验；前端已过滤后一般应通过

### 地图
- 事故点：保留现有 Marker
- 新增 `AMap.Circle`：`center=事故点`，`radius=500`（米），半透明填充 + 描边；地图实例创建时画一次，事故点不变则不重复销毁重建
- 救援车：沿用 `syncVehicleMarker`；轮询后更新 position（或重建标记）
- `setFitView`：事故点、圆、车（有车时）尽量同屏；圆需参与 fit（或按 500m 估算合适 zoom）

### UI 文案
- 删除：「签到」按钮及 `uni.getLocation` 自动签到入口
- 保留并改文案：「手动签到（需填写原因）」（原「无法定位，手动签到」）
- 已签到展示逻辑不变

### 涉及文件（预期）
| 文件 | 变更 |
|------|------|
| `mobile-rescuer/src/pages/task/detail.vue` | 自动签到、10s 轮询、圆、去掉手机签到按钮 |
| （可选）`mobile-rescuer/src/utils/geo.js` | Haversine + 新鲜度判断，便于单测/复用 |
| 后端 | **无必须变更** |

## 测试要点
1. 车辆新鲜且 ≤500m：进详情自动签到成功，按钮区不再出现签到入口
2. 坐标过期或 >500m：不自动签；可手动签到
3. 轮询中车辆进入 500m 且变新鲜：下一次 10s 周期自动签到
4. 已签到后轮询不再发 AUTO checkin
5. 地图可见 500m 圆；车标随轮询移动
6. 无高德 Key / 无事故坐标：地图降级逻辑与现网一致，自动签到在无事故坐标时不触发

## 决议记录
- 自动签到形态：B（进详情自动）
- 位置源：A（派单车辆上报坐标）
- 超距/无坐标兜底：B（仅手动签到，去掉手机签到）
- 过期坐标：B（不自动签）
- 实现路径：1（前端判距）
- 轮询与自动签：A（10s 轮询；满足条件则每次可试，成功后停止）
