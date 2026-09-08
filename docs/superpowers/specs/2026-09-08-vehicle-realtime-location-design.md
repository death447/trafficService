# 施救车辆实时位置与就近派单设计文档

## 概述

调度员在派单详情选择施救车辆时，需要看到车辆**近期实时位置**，按距事故点**纯距离**优先推荐，并在地图上标注空闲车辆。施救员移动端（H5）在前台定时上报 GPS，写回绑定车辆坐标；`nearby` 按新鲜度拆分「实时」与「位置未知/过期」两段。

本轮采用：**扩展 `rescue_vehicle` 坐标新鲜度字段 + HTTP 定时上报 + 改造 nearby + 派单详情地图标车与轮询**（不做 WebSocket / 轨迹）。

## 背景与范围

### 已有基础
- `rescue_vehicle.longitude/latitude`：管理端可维护，**无** `location_updated_at`，移动端未持续上报
- `GET /api/vehicle/nearby`：IDLE 且有坐标 → Haversine；当前排序为**片区优先再距离**
- `DispatchDetail.vue`（PENDING）：高德地图仅标事故点；列表按片区分段；无车辆标记、无定时刷新
- 移动端签到时用 `uni.getLocation`；绑车 `POST /api/mobile/rescuer/bind-vehicle`

### 需求决议
| 项 | 决议 |
|----|------|
| 位置来源 | 施救员 App **前台定时上报** GPS（方案 A） |
| 推荐排序 | **纯距离优先**；片区仅作标签，不参与排序 |
| 地图标注 | 仅标 **IDLE 且位置新鲜** 的车（与主推荐列表一致） |
| 新鲜度阈值 | **180 秒（3 分钟）**；建议上报间隔约 **30 秒** |
| 无新鲜定位 | 主列表只含新鲜车；另开 **「位置未知/过期」** 折叠段，仍可派 |
| 实现路径 | 扩展车辆表 + HTTP 轮询（不做独立位置表 / WS / 第三方轨迹） |

### 本轮目标
1. `rescue_vehicle` 增加 `location_updated_at`；仅移动端上报刷新该字段与坐标
2. `POST /api/mobile/rescuer/location`；H5 绑车且前台时约 30s 上报
3. 改造 `nearby`：fresh 按距离；`staleVehicles` 分段；片区不改序
4. 派单详情：地图标 fresh 车；列表两段；约 20s 轮询刷新

### 明确不做
- 位置轨迹历史、WebSocket 推送、Redis
- H5 后台保活强制定位
- 车载 GPS / 高德轨迹服务对接
- 新建工单页选车地图
- 改派单状态机（PENDING→DISPATCHED 等）
- 管理端手工改坐标视为「实时新鲜」（本轮仅移动端上报刷新 `location_updated_at`）

## 技术方案

### 总体架构

| 模块 | 后端 | 前端 / 移动端 |
|------|------|----------------|
| 位置落库 | `RescueVehicle` + mapper 增量更新 | — |
| 上报 | `RescuerMobileController` + Service | H5 定时 `getLocation` → POST |
| 推荐 | `RescueVehicleService.findNearby` | `DispatchDetail` 消费新响应 |
| 地图 | — | 事故点 + fresh 车标记；轮询 |

**数据流：**
1. 施救员登录并绑车 → 前台每 ~30s 上报 `lng/lat` → 更新绑定车坐标与 `location_updated_at`
2. 调度打开 PENDING 详情 → `nearby(事故 lng/lat)` → fresh 按距离、stale 另段
3. 地图绘制事故点 + fresh 车辆标记；约 20s 重拉 nearby

### 新鲜度规则
- 常量：`LOCATION_STALE_SECONDS = 180`
- **fresh**：`longitude/latitude` 非空，且 `location_updated_at` 非空且距今 ≤ 180 秒
- 否则：**stale**（含从未上报、坐标空、或超过阈值）

## 数据库设计

### `rescue_vehicle` 增量

```sql
ALTER TABLE `rescue_vehicle`
  ADD COLUMN `location_updated_at` DATETIME DEFAULT NULL
    COMMENT '最近一次移动端 GPS 上报时间' AFTER `latitude`;
```

- 现有 `longitude` / `latitude` 继续表示最新已知坐标
- 管理端 CRUD 更新坐标时：**不**刷新 `location_updated_at`（避免静态维护被当成实时）
- `init.sql` 与 migrate 脚本同步；种子车可保持 `location_updated_at` 为空（演示需靠移动端上报或测试夹具）

## 后端设计

### 上报 API
- `POST /api/mobile/rescuer/location`
- 权限：新增 `rescuer:location`，赋给 `TOW_DRIVER`（及具备施救员移动端能力的角色）；种子写入 `init.sql` / migrate
- Body：`{ "lng": number, "lat": number }`（可选 `accuracy`）
- 逻辑：
  1. 当前用户绑定车辆（`driver_user_id = me`）；无则 400「请先绑定车辆」
  2. 校验经纬度合法范围
  3. 更新该车 `longitude`、`latitude`、`location_updated_at = now`
  4. 不修改 `status`
  5. 返回 `{ vehicleId, locationUpdatedAt }`
- 安全：禁止客户端指定 `vehicleId`；仅能更新自己绑定的车

### `GET /api/vehicle/nearby`
权限仍 `dispatch:dispatch`。

**响应（扩展）：**

```json
{
  "matchedDistrict": { "id": 1, "name": "...", "code": "..." },
  "vehicles": [
    {
      "vehicle": { },
      "distanceMeters": 320.5,
      "inMatchedDistrict": true,
      "locationFresh": true,
      "locationUpdatedAt": "2026-09-08T17:00:00"
    }
  ],
  "staleVehicles": [
    {
      "vehicle": { },
      "distanceMeters": null,
      "inMatchedDistrict": false,
      "locationFresh": false,
      "locationUpdatedAt": null
    }
  ]
}
```

**排序与过滤：**
1. 取全部 `IDLE` 车辆
2. 按新鲜度拆分：
   - `vehicles`：fresh → 按 `distanceMeters` **升序** → 应用 `limit`（默认 20，最大 50）
   - `staleVehicles`：其余 IDLE；有旧坐标可算参考距离，**不进入主排序**；无坐标则 `distanceMeters = null`
3. `inMatchedDistrict` / `matchedDistrict`：保留展示，**不参与排序**
4. 工单无坐标时的前端兜底逻辑保持：可不走距离排序；本接口仍要求 `lng/lat` 参数（与现网一致）

### 涉及文件（预期）
- `entity/RescueVehicle.java`、`mapper/RescueVehicleMapper.java`（及 XML 若有）
- `database/migrate_2026-09-08_vehicle_location.sql`、`database/init.sql`
- `controller/RescuerMobileController.java`、`service/RescuerMobileService.java`
- `dto/NearbyVehicleVO.java`、`dto/NearbyVehiclesResponse.java`
- `service/RescueVehicleService.java` + `RescueVehicleServiceTest`
- 权限种子（若新增 `rescuer:location`）

## 移动端设计（mobile-rescuer）

- 全局或布局级：已登录且已绑车、页面 **onShow** 时启动定时器（~30s），**onHide** 停止
- 进入主流程页（任务列表 / 我的等）立即上报一次
- `uni.getLocation` 成功 → `POST .../location`；失败静默跳过
- 未绑车：不上报；收到「请先绑定车辆」则停定时器并提示
- 不实现后台保活与离线补传队列

## 管理端前端设计

### `DispatchDetail.vue`（仅 PENDING 派单区）
- 列表：
  1. **附近空闲（实时）** ← `vehicles`（距离文案）
  2. **位置未知 / 过期**（默认折叠）← `staleVehicles`，仍可选派
- 取消「本片区推荐 / 其它车辆」作为排序分段；片区名称仍可在顶部提示
- 地图（有 `VITE_AMAP_KEY` 且工单有坐标）：
  - 事故点标记
  - 每个 fresh 车一个标记（车牌/距离简签）；点击可选中对应列表项
  - 标记集合与 `vehicles` 一致；stale 不上图
- 有坐标时约 **20 秒** 重拉 nearby 并刷新标记；失败保留上次数据并轻提示
- 无 Key：列表与轮询仍可用；地图占位提示不变

### API 封装
- `mobile-rescuer`：新增 `reportLocation`
- `frontend`：`nearbyVehicles` 适配 `staleVehicles` 与新字段

## 错误处理

| 场景 | 行为 |
|------|------|
| 未绑车上报 | 400 + 中文提示；客户端停报 |
| 定位失败 | 本轮跳过，不阻塞 UI |
| nearby 刷新失败 | 保留上次列表/标记，轻提示 |
| 车辆变 BUSY | 下次 nearby 自然移除 |

## 测试与验收

| # | 标准 |
|---|------|
| 1 | 绑车施救员前台运行时，约 30s 内管理端可见坐标与 `location_updated_at` 更新 |
| 2 | 超过约 3 分钟无上报 → 进入 stale 段，地图不再标该车 |
| 3 | fresh 列表按距事故点从近到远；片区标签不影响顺序 |
| 4 | 地图 fresh 标记数与主列表一致，可辅助选车派单 |
| 5 | stale 段仍可成功派单（车辆变 BUSY） |
| 6 | 单测：新鲜度拆分、纯距离排序、未绑车上报失败 |

## 后续衔接（非本轮）
- WebSocket / 短轮询优化为服务端推送
- 位置轨迹表与回放
- 新建工单页同步地图选车
- 片区负载策略与「距离+片区」混合排序可配置
