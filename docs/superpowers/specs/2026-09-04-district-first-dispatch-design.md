# 调度派单：片区优先推荐设计文档

## 概述

在已有片区围栏（`resolve`）与施救车辆 `district_id` 基础上，改造调度员派单详情的「附近空闲车辆」推荐：**事故点所属片区的空闲车优先，组内按距离；其余空闲车按距离**。前端分「本片区推荐」「其它车辆」两段展示；未匹配片区时仅提示并全部归入「其它车辆」。

本轮不落库工单片区、不强制落区派单、不做地图围栏/值班叠加。

## 背景与范围

### 已有基础
- `DistrictService.resolve(lng, lat)`：ENABLED 片区射线法，重叠按 id 升序
- `RescueVehicle.districtId` 可绑定片区
- `GET /api/vehicle/nearby`：当前仅按 Haversine 距离排序返回 `List<NearbyVehicleVO>`
- 前端 `DispatchDetail.vue`：PENDING 态加载 nearby 并选车派单

### 本轮目标
1. nearby 接口返回匹配片区元数据 + 带 `inMatchedDistrict` 的车辆列表
2. 排序：本片区 IDLE 优先（组内距离升序），再其它 IDLE（距离升序）
3. 派单详情 UI 分段展示与「未匹配到片区」提示
4. 派单 assign / 车辆状态机行为不变

### 明确不做
- `dispatch_order` 增加 `district_id`
- 事故点必须落在片区内才可派单
- 地图绘制片区多边形、当日值班车辆叠加、实时轨迹
- 新独立 `recommend` 接口
- 「本片区无车时隐藏其它车、需手动展开」交互

## 技术方案

### 总体架构（方案 1）

扩展现有 `GET /api/vehicle/nearby`：

1. `DistrictService.resolve(lng, lat)` → `matchedDistrict`（可 null）
2. 取全部 `IDLE` 且有坐标车辆，计算 `distanceMeters`
3. 标记 `inMatchedDistrict = matched != null && Objects.equals(vehicle.districtId, matched.id)`
4. 排序比较器：`inMatchedDistrict` 降序，再 `distanceMeters` 升序
5. `limit`（默认 20，最大 50）作用于合并后的列表

前端消费新响应形状，按 `inMatchedDistrict` 拆分两段列表。

### 响应形状变更（破坏性，本轮前后端同步）

**旧：** `Result.data` = `NearbyVehicleVO[]`  

**新：**

```json
{
  "matchedDistrict": { "id": 1, "name": "福田中心片区", "code": "FT-CENTER" },
  "vehicles": [
    {
      "vehicle": { },
      "distanceMeters": 320.5,
      "inMatchedDistrict": true
    }
  ]
}
```

- `matchedDistrict`：无匹配时 `null`（可不返回 name/code 的瘦 DTO，字段至少 `id`/`name`/`code`）
- `NearbyVehicleVO.inMatchedDistrict`：无匹配片区时全部为 `false`
- 车辆 `districtId` 为空 → 一律 `inMatchedDistrict = false`

### 场景矩阵

| 场景 | UI |
|------|-----|
| 匹配片区且有本片区空闲车 | 两段；本片区在前 |
| 匹配片区但本片区无空闲车 | 「本片区推荐」空文案；其它车辆可派 |
| 未匹配片区 | 提示「未匹配到片区」；仅「其它车辆」 |
| 工单无坐标 | 保持现有兜底：不走 nearby 距离排序，展示空闲列表（无片区分组要求） |

## 后端设计

### 涉及文件
- `dto/NearbyVehicleVO.java` — 增加 `boolean inMatchedDistrict`
- `dto/NearbyVehiclesResponse.java`（或同名）— `District matchedDistrict`（或精简 VO）+ `List<NearbyVehicleVO> vehicles`
- `service/RescueVehicleService.findNearby` — 注入/使用 `DistrictService`，返回 `NearbyVehiclesResponse`
- `controller/VehicleController.findNearby` — 返回类型改为新响应
- `test/.../RescueVehicleServiceTest` — 更新既有 nearby 用例；新增片区优先 / 未匹配用例

### 权限与错误
- 仍 `@PreAuthorize("hasAuthority('dispatch:dispatch')")`
- resolve 跳过坏围栏的既有行为保持；nearby 不因单条坏围栏失败

## 前端设计

### 修改
- `frontend/src/api/vehicle.js` — `nearbyVehicles` 调用方适配
- `frontend/src/views/dispatch/DispatchDetail.vue`：
  - 展示所属片区名称或「未匹配到片区」
  - 有匹配：两段「本片区推荐」「其它车辆」
  - 无匹配：仅「其它车辆」
  - 本片区空：`本片区暂无空闲车辆`
  - 选车 / 确认派单逻辑不变

## 测试与验收

| # | 标准 |
|---|------|
| 1 | 点在样例片区内且有本片区 IDLE → 该车在「本片区推荐」且排在其它车前 |
| 2 | 本片区无 IDLE → 本片区段空提示；其它车可派 |
| 3 | 点在围栏外 → 未匹配提示；仅其它车辆按距离 |
| 4 | 确认派单成功，车辆 BUSY |
| 5 | 单测覆盖排序/分组；旧纯距离断言改为新返回类型 |

## 后续衔接（非本轮）
- 工单落库事故片区便于统计
- 派单地图叠加围栏与当日值班车
- 片区负载饱和策略（本轮已用「本片区优先 + 其它按距离」近似需求文档机制）
