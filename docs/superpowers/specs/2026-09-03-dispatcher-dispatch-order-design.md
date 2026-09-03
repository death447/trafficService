# 调度员端：救援工单与施救车辆设计文档

## 概述

在现有 Vue3 + Spring Boot3 RBAC 系统上，按**调度员竖切**落地第一块业务：任务列表、新建工单（高德地图选点）、调度派单（按距离推荐空闲车）、完成/中止，以及施救车辆最小管理。

本轮不包含：交警/施救员/停车场端、移动端、片区电子围栏、实时轨迹、扣留车辆出入库。

## 背景与范围

### 已有基础
- 认证：JWT + Spring Security + `@PreAuthorize`
- 权限种子已含 `dispatch:manage`（角色 `DISPATCHER`）
- 前后端分层与 `Result` 统一响应已就绪

### 本轮目标
1. 调度员可创建救援工单，地图选点写入经纬度与地址
2. 按事故点直线距离推荐空闲施救车辆并完成派单
3. 工单状态：`PENDING` → `DISPATCHED` → `COMPLETED` / `ABORTED`（调度员本轮可点完成，便于单端演示）
4. 施救车辆 CRUD（车牌、类型、坐标、空闲/忙碌等）
5. 片区：仅预留 `district_id` 字段，围栏与片区优先推荐留待后续

### 明确不做
- 片区电子围栏管理与「本片区优先」推荐算法
- 车辆实时轨迹、值班排班
- 高德服务端 Key 代理（本轮仅前端 Web JS API）
- 交警评价、施救员接单/签到、停车场出入库
- 真分页改造（列表可与现有模块一致：筛选后全量返回）

## 技术方案

### 总体架构（方案 1）

与现有 User/Role 同构，拆成两个业务模块：

| 模块 | 后端 | 前端 |
|------|------|------|
| 施救车辆 | Entity/Mapper/Service/`VehicleController` | `/vehicles` |
| 救援工单 | Entity/Mapper/Service/`DispatchController` | `/dispatches*` |

**数据流：**
1. 调度员登录 → JWT 含 `dispatch:*` / `vehicle:*`
2. 新建工单：前端高德选点 → `POST /api/dispatch` → `PENDING`
3. 派单：`GET /api/vehicle/nearby` → 选车 → `POST /api/dispatch/{id}/assign` → 工单 `DISPATCHED`、车辆 `BUSY`
4. 完成/中止：更新工单状态，事务内尝试将车辆恢复 `IDLE`

### 技术栈增量
- 前端：高德地图 JS API 2.0（`VITE_AMAP_KEY`）
- 后端：无新框架依赖；距离计算用 Haversine（或等价公式）在 Service 层完成

## 数据库设计

### `rescue_vehicle`

```sql
CREATE TABLE `rescue_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plate_no` VARCHAR(20) NOT NULL COMMENT '车牌',
  `vehicle_type` VARCHAR(50) NOT NULL COMMENT '车辆类型：TOW/CLEARANCE/OTHER 等',
  `color` VARCHAR(30) DEFAULT NULL,
  `equipment` VARCHAR(200) DEFAULT NULL COMMENT '配备装备',
  `longitude` DECIMAL(10,7) DEFAULT NULL,
  `latitude` DECIMAL(10,7) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/BUSY/OFFLINE',
  `district_id` BIGINT DEFAULT NULL COMMENT '预留片区',
  `driver_user_id` BIGINT DEFAULT NULL COMMENT '绑定施救员 user.id',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plate_no` (`plate_no`),
  KEY `idx_status` (`status`),
  KEY `idx_district_id` (`district_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='施救车辆';
```

### `dispatch_order`

```sql
CREATE TABLE `dispatch_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(32) NOT NULL COMMENT '业务单号',
  `accident_address` VARCHAR(255) NOT NULL,
  `longitude` DECIMAL(10,7) DEFAULT NULL,
  `latitude` DECIMAL(10,7) DEFAULT NULL,
  `rescue_reason` VARCHAR(500) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DISPATCHED/COMPLETED/ABORTED',
  `dispatcher_id` BIGINT NOT NULL COMMENT '创建调度员 user.id',
  `vehicle_id` BIGINT DEFAULT NULL,
  `rescuer_id` BIGINT DEFAULT NULL COMMENT '施救员 user.id',
  `abort_reason` VARCHAR(500) DEFAULT NULL,
  `dispatched_at` DATETIME DEFAULT NULL,
  `completed_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status` (`status`),
  KEY `idx_dispatcher_id` (`dispatcher_id`),
  KEY `idx_vehicle_id` (`vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='救援工单';
```

### 状态机

```
PENDING --assign--> DISPATCHED --complete--> COMPLETED
   |                    |
   +--------abort-------+------> ABORTED
```

规则：
- 仅 `PENDING` 可编辑核心字段、可 `assign`
- `assign` 要求目标车辆 `status = IDLE`；成功后车辆 → `BUSY`
- `complete` 仅 `DISPATCHED`；成功后若该车无其他 `DISPATCHED` 工单则 → `IDLE`
- `abort` 允许 `PENDING` 或 `DISPATCHED`；需填写 `abort_reason`；已派车则按上条释放车辆
- 非法流转返回业务错误

### 种子数据
- 若干 `IDLE` 车辆，坐标落在同一城市附近（便于高德联调）
- 可选：测试调度员用户（`DISPATCHER` 角色）若尚无则在增量脚本中补充

### 权限增量

在现有 `dispatch:manage`（MODULE）下增加 BUTTON：
- `dispatch:query`、`dispatch:add`、`dispatch:edit`
- `dispatch:dispatch`、`dispatch:complete`、`dispatch:abort`

新增 MODULE `vehicle:manage` 及 BUTTON：
- `vehicle:query`、`vehicle:add`、`vehicle:edit`、`vehicle:delete`

角色授权：
- `DISPATCHER`：上述全部 dispatch + vehicle 权限（含模块码）
- `ADMIN`：同上，便于管理员联调

DDL/DML 以增量 SQL（或更新 `database/init.sql`）形式交付；已有库用增量脚本，新环境以完整 init 为准。

## 后端设计

### 包与分层

```
entity/RescueVehicle.java, DispatchOrder.java
mapper/RescueVehicleMapper.java, DispatchOrderMapper.java
service/RescueVehicleService.java, DispatchOrderService.java
controller/VehicleController.java (/api/vehicle)
controller/DispatchController.java (/api/dispatch)
dto/ 按需：CreateDispatchRequest, AssignDispatchRequest, AbortRequest 等
```

### API

**车辆 `/api/vehicle`**

| Method | Path | Authority | 说明 |
|--------|------|-----------|------|
| GET | `/list` | `vehicle:query` | 筛选车牌、状态、类型 |
| GET | `/{id}` | `vehicle:query` | 详情 |
| POST | `/` | `vehicle:add` | 登记 |
| PUT | `/{id}` | `vehicle:edit` | 更新 |
| DELETE | `/{id}` | `vehicle:delete` | 有进行中工单（PENDING/DISPATCHED）则拒绝 |
| GET | `/nearby` | `dispatch:dispatch` | `lng,lat,limit`；仅 IDLE；直线距离升序 |

**工单 `/api/dispatch`**

| Method | Path | Authority | 说明 |
|--------|------|-----------|------|
| GET | `/list` | `dispatch:query` | 筛选单号、状态、地点、调度员 |
| GET | `/{id}` | `dispatch:query` | 详情（含车辆/施救员摘要字段） |
| POST | `/` | `dispatch:add` | 新建；`dispatcher_id`=当前用户；`order_no` 服务端生成 |
| PUT | `/{id}` | `dispatch:edit` | 仅 PENDING |
| POST | `/{id}/assign` | `dispatch:dispatch` | body: `vehicleId`, 可选 `rescuerId` |
| POST | `/{id}/complete` | `dispatch:complete` | DISPATCHED → COMPLETED |
| POST | `/{id}/abort` | `dispatch:abort` | body: `abortReason` |

`order_no` 固定为：`RO` + `yyyyMMdd` + 4 位当日序号（如 `RO202609030001`），服务端生成并保证唯一。

### 事务与并发
- `assign` / `complete` / `abort` 使用 `@Transactional`
- `assign` 再次校验车辆仍为 `IDLE`（防双派）；冲突返回明确文案

## 前端设计

### 路由

| Path | 组件 | meta.permissions |
|------|------|------------------|
| `/dispatches` | 任务列表 | `['dispatch:manage']` |
| `/dispatches/new` | 新建工单 | `['dispatch:add']` |
| `/dispatches/:id` | 详情/派单操作 | `['dispatch:query']` |
| `/vehicles` | 车辆管理 | `['vehicle:manage']` |

路由注册时 `/dispatches/new` 必须写在 `/dispatches/:id` 之前，避免 `new` 被当成 id。

侧栏增加「任务管理」「施救车辆」，`v-auth` 控制。

说明：当前 `init.sql` 中 `ADMIN` 仅绑定权限 id 1–15，`DISPATCHER` 仅有 `dispatch:manage` 模块码。本轮权限脚本需同时补齐按钮权限，并为 `ADMIN`、`DISPATCHER` 重新授权（含 `vehicle:manage`）。

### 页面要点
- **任务列表**：筛选、状态徽章、跳转新建/详情
- **新建**：施救原因、地址；高德地图选点 → lng/lat；逆地理编码填地址（可手改）；无 Key 时提示配置并允许手填坐标
- **详情**：PENDING 展示附近车地图选点派单；DISPATCHED 可完成/中止；中止弹窗填原因
- **车辆**：表格 + 弹窗 CRUD；支持维护经纬度

### 高德集成
- 使用官方 JS API；Key：`VITE_AMAP_KEY`（`.env.local`，gitignore）
- 封装薄工具（加载 script、创建 Map、选点、Marker），避免把地图逻辑散落各处
- 安全密钥/域名白名单在高德控制台配置，文档中说明联调步骤

### API 模块
- `frontend/src/api/vehicle.js`
- `frontend/src/api/dispatch.js`
- 继续走 `utils/request.js` 拦截器

## 错误处理

- 业务异常：车牌重复、非法状态、车辆非空闲、删除被占用车辆 → `code ≠ 200` + 可读 `message`
- 401/403 行为与现网一致
- 地图加载失败不阻断：允许手填坐标提交工单

## 测试策略

### 后端单测（最低集）
- `DispatchOrderService`：assign / complete / abort 状态机；非法流转失败
- 车辆占用与释放；`nearby` 距离排序正确
- 车牌唯一；删除有进行中工单的车辆失败

### 手工联调
1. 调度员登录 → 车辆 CRUD
2. 新建工单（地图或手填坐标）→ 附近车列表有序
3. 派单 → 车辆变 BUSY → 完成 → 车辆回 IDLE
4. 中止路径验证
5. 无 `dispatch:manage` 的账号侧栏不可见且路由进 403

## 交付物清单

- `database` 增量/更新脚本（表 + 权限 + 种子车 + 可选调度员账号）
- 后端 Vehicle / Dispatch 全栈分层与单测
- 前端 4 路由页面 + 侧栏 + 高德封装 + `.env.example`
- 本设计文档

## 后续迭代（不在本轮）

1. 片区围栏 + 片区优先推荐
2. 施救员端：接单、签到、现场采集
3. 交警端：任务查看与评价
4. 停车场与扣留车辆出入库
5. 统计分析 / 效能分析
