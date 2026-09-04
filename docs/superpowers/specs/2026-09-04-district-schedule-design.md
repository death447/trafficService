# PC 端：片区管理与排班管理设计文档

## 概述

在现有 Vue3 + Spring Boot3 RBAC 与调度员工单/车辆能力之上，落地 **片区电子围栏** 与 **值班排班**，为后续「本片区优先派单」和「地图当日值班车辆」打底。

本轮采用与现有模块同构的方案：围栏存 JSON 多边形，点落区在 Service 层用射线法判定；排班支持自定义起止时间，施救员班次必须绑定车辆。

本轮不包含：改派单推荐算法、实时轨迹、移动端、停车场/扣留车辆、统计分析。

## 背景与范围

### 已有基础
- 认证：JWT + Spring Security + `@PreAuthorize`
- 施救车辆表已预留 `district_id`，尚无 `district` 表与围栏
- 高德地图 Web JS API 已用于工单选点
- 角色种子含 `ADMIN`、`DISPATCHER`、`TOW_DRIVER` 等

### 需求来源
- 《道路交通事故救援派单系统报价清单》功能清单：PC「片区管理」「排班管理」
- 排班：按日期与时间段，支持施救负责人（施救员）与调度员

### 本轮目标
1. 片区 CRUD：基础信息 + 地图绘制多边形围栏（`fence_json`）
2. 点落区接口：根据经纬度解析所属 `ENABLED` 片区
3. 车辆可绑定/解绑 `district_id`
4. 排班 CRUD：自定义起止时间；角色为调度员或施救员；可选片区；**施救员必须绑定施救车辆**
5. 权限：`ADMIN` 与 `DISPATCHER` 均可维护片区与排班

### 明确不做
- 派单推荐改为「本片区优先，否则按距离」
- 地图叠加当日值班车辆/人员轨迹与状态着色
- MySQL 空间类型 / 围栏点拆子表 / 重型日历组件
- 交警/施救员/停车场移动端
- 真分页改造（列表与现有模块一致：筛选后全量返回）

## 技术方案

### 总体架构（方案 1）

| 模块 | 后端 | 前端 |
|------|------|------|
| 片区 | Entity/Mapper/Service/`DistrictController` | `/districts` |
| 排班 | Entity/Mapper/Service/`ScheduleController` | `/schedules` |
| 车辆 | 既有 CRUD，补齐片区展示与绑定 | `/vehicles` 小改 |

**数据流：**
1. 管理员/调度员维护片区围栏 → `district.fence_json`
2. 车辆编辑绑定 `district_id`
3. 任意业务可调 `GET /api/district/resolve?lng&lat` 得到所属片区（本轮派单页可不接入）
4. 排班写入 `duty_schedule`；施救班次带 `vehicle_id`，供后续地图「当日值班车」查询

### 技术栈增量
- 前端：复用高德地图 JS API 2.0 做多边形编辑
- 后端：无新框架依赖；点落多边形用射线法（ray casting）在 Service 完成

## 数据库设计

### `district`

```sql
CREATE TABLE `district` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '片区名称',
  `code` VARCHAR(50) NOT NULL COMMENT '片区编码',
  `fence_json` TEXT NOT NULL COMMENT '多边形顶点 JSON：[{lng,lat},...]',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='片区电子围栏';
```

### `duty_schedule`

```sql
CREATE TABLE `duty_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `duty_date` DATE NOT NULL COMMENT '值班归属日（用于列表筛选）',
  `start_time` DATETIME NOT NULL COMMENT '班次开始',
  `end_time` DATETIME NOT NULL COMMENT '班次结束（可跨日）',
  `user_id` BIGINT NOT NULL COMMENT '值班人 user.id',
  `role_type` VARCHAR(30) NOT NULL COMMENT 'DISPATCHER/TOW_DRIVER',
  `district_id` BIGINT DEFAULT NULL COMMENT '可选片区',
  `vehicle_id` BIGINT DEFAULT NULL COMMENT '施救班次必填；调度班次必须为空',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_duty_date` (`duty_date`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_vehicle_id` (`vehicle_id`),
  KEY `idx_district_id` (`district_id`),
  KEY `idx_start_end` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='值班排班';
```

### 关联与业务规则

1. `rescue_vehicle.district_id` → `district.id`（可空）
2. **围栏校验**：创建/更新时 `fence_json` 至少 3 个有效 `{lng,lat}` 点；服务端可自动闭合（首尾相同则不重复存）
3. **点落区**：仅匹配 `status = ENABLED`；多片区重叠时按 `id` 升序取第一个命中；无命中返回 `null`
4. **禁用片区**：不可作为新车辆绑定目标，不可写入新排班的 `district_id`；已有引用保留只读展示
5. **删除片区**：若仍被车辆或排班引用则禁止物理删除（提示改 `DISABLED`）
6. **排班角色**：
   - `role_type = DISPATCHER`：用户须具备调度员角色；`vehicle_id` 必须为空
   - `role_type = TOW_DRIVER`：用户须具备施救员角色；`vehicle_id` 必填且车辆存在
7. **排班冲突**（创建/更新拒绝）：
   - 同一 `user_id` 与已有班次时间段重叠（半开或闭区间约定：`start < other.end AND end > other.start`）
   - 同一 `vehicle_id`（非空）时间段重叠
8. **时间**：`end_time` 必须晚于 `start_time`；允许跨日；`duty_date` **一律取 `start_time` 的本地日期**（后端写入时覆盖前端传入值，避免不一致）

### 权限增量

新增 MODULE + BUTTON：

| 权限码 | 类型 | 说明 |
|--------|------|------|
| `district:manage` | MODULE | 片区管理 |
| `district:query` | BUTTON | 查询 |
| `district:add` | BUTTON | 新增 |
| `district:edit` | BUTTON | 编辑 |
| `district:delete` | BUTTON | 删除 |
| `district:resolve` | BUTTON | 点落区解析 |
| `schedule:manage` | MODULE | 排班管理 |
| `schedule:query` | BUTTON | 查询 |
| `schedule:add` | BUTTON | 新增 |
| `schedule:edit` | BUTTON | 编辑 |
| `schedule:delete` | BUTTON | 删除 |

角色授权：
- `ADMIN`、`DISPATCHER`：上述全部权限（含模块码）

DDL/DML：增量脚本 `database/migrate_2026-09-04_district_schedule.sql`；同步更新 `database/init.sql`。

### 种子数据
- 2～3 个深圳附近样例片区（简单多边形，`ENABLED`）
- 若干排班样例（绑定现有 `dispatcher` / 若有施救员用户与车辆）
- 可选：将部分样例车辆的 `district_id` 指向种子片区

## 后端设计

### 包与分层

```
entity/District.java, DutySchedule.java
mapper/DistrictMapper.java, DutyScheduleMapper.java
service/DistrictService.java, DutyScheduleService.java
  （含 GeoUtils 或 DistrictService 内射线法）
controller/DistrictController.java (/api/district)
controller/ScheduleController.java (/api/schedule)
dto/ 按需：DistrictRequest、ScheduleRequest、DistrictResolveVO 等
```

### API

统一前缀 `/api`，响应 `Result<T>`。

**片区** `/api/district`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/list` | `district:query` | keyword、status |
| GET | `/{id}` | `district:query` | 详情含围栏 |
| POST | `/` | `district:add` | 创建 |
| PUT | `/{id}` | `district:edit` | 更新 |
| DELETE | `/{id}` | `district:delete` | 无引用才删 |
| GET | `/resolve` | `district:resolve` | 参数 `lng`、`lat` |

**排班** `/api/schedule`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/list` | `schedule:query` | 日期范围、roleType、districtId、userId |
| GET | `/{id}` | `schedule:query` | 详情 |
| POST | `/` | `schedule:add` | 创建 |
| PUT | `/{id}` | `schedule:edit` | 更新 |
| DELETE | `/{id}` | `schedule:delete` | 删除 |

**车辆**：既有接口读写 `districtId`；无需新接口。列表筛选可后续加 `districtId`（非本轮必须）。

### 错误处理
冲突、角色不符、施救未绑车、片区禁用、删除仍有引用、围栏点数不足 → 业务错误（`Result` 非成功码），文案明确。

## 前端设计

### 路由

| 路径 | 页面 | meta 权限 |
|------|------|-----------|
| `/districts` | `DistrictList.vue` | `district:manage` |
| `/schedules` | `ScheduleList.vue` | `schedule:manage` |
| `/vehicles` | 现有页增强 | `vehicle:manage` |

导航：工作台/侧栏增加「片区管理」「排班管理」。

### 页面要点
- **片区**：列表 + 新建/编辑；高德地图多边形绘制/编辑，保存为 `fence_json`；支持启用/禁用
- **排班**：按日期或日期范围列表；表单含人员、角色类型、起止时间、可选片区；施救员时必选车辆
- **车辆**：表格增加「所属片区」；表单下拉选择启用中片区（可清空）

### API 封装
`frontend/src/api/district.js`、`schedule.js`；请求层复用现有 Axios + JWT。

## 测试与验收

| # | 标准 |
|---|------|
| 1 | 调度员可维护片区围栏，详情可见多边形 |
| 2 | `resolve` 围栏内命中正确片区，围栏外为空 |
| 3 | 车辆可绑定/解绑片区 |
| 4 | 调度员/施救员自定义时段排班；施救员必须选车 |
| 5 | 同人/同车时间重叠创建失败并有明确提示 |
| 6 | 无权限账号无菜单且接口 403 |
| 7 | 现有派单与 nearby 行为不变（仍按距离） |

补充用例：顶点 &lt; 3 拒绝；禁用片区不可新绑/新排；有引用删片区失败；跨日班次冲突检测正确。

## 后续衔接（非本轮）
1. 派单推荐：事故点 `resolve` → 优先该片区 `IDLE` 车，否则按距离
2. 派单地图：按当日 `duty_schedule` 展示值班车辆与状态着色
3. 施救员端接单后与排班/车辆绑定联动
