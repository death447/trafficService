# PC 端：停车场管理与扣留车辆设计文档

## 概述

在现有 Vue3 + Spring Boot3 RBAC 与调度业务之上，落地 **停车场登记** 与 **扣留车辆出入库主线**（入库 / 编辑 / 出库 / 清理 / 吊牌打印），供 `PARKING_ADMIN` 与 `ADMIN` 使用。

本轮采用与片区/车辆模块同构的方案：两张业务表 + 标准 Controller/Service/Mapper + 两个列表页 + 浏览器吊牌打印页（前端 QR）。

本轮不包含：统计导出、移动端扫码、现场照片上传、PDF 服务端生成、第三方对接。

## 背景与范围

### 已有基础
- 认证：JWT + Spring Security + `@PreAuthorize`
- 权限种子已有 MODULE `parking:manage`（id=19）及角色 `PARKING_ADMIN`；尚无 BUTTON 细分与业务表
- 救援工单 `dispatch_order`、施救车辆 `rescue_vehicle` 已就绪；扣留车与施救车 **分表**，不混用
- 前端列表/表单模式与 `enterprise.css` 可复用

### 需求来源
- 《道路交通事故救援派单系统报价清单》功能清单：PC「停车场管理」「车辆管理」（扣留出入库/吊牌）
- 本轮范围决议：**B** — 停车场 CRUD + 出入库 + 清理 + 吊牌打印；不做统计导出与移动端

### 本轮目标
1. 停车场 CRUD：名称、编码、地址、联系人、启用/禁用
2. 扣留车入库：生成唯一扣押编号；可选关联救援工单；必选 ENABLED 停车场
3. 状态机：`IN_YARD` → `OUT` → `CLEARED`；在库可编辑；同牌全局同时最多一条在库
4. 吊牌：浏览器打印页 + 按扣押编号生成二维码
5. 权限：仅 `ADMIN` 与 `PARKING_ADMIN` 可操作

### 明确不做
- 统计分析 / 效能 / 评价及导出
- 移动端（施救员/交警/停车场）扫码与照片
- 服务端 PDF、本地文件存储
- 强制从工单入库；改派单/施救车辆逻辑
- 真分页改造（列表与现有模块一致：筛选后全量返回）

## 技术方案

### 总体架构（方案 1）

| 模块 | 后端 | 前端 |
|------|------|------|
| 停车场 | Entity/Mapper/Service/`ParkingLotController` | `/parkings` |
| 扣留车辆 | Entity/Mapper/Service/`DetainedVehicleController` | `/detained-vehicles`、吊牌页 |

**数据流：**
1. 管理员维护停车场 → `parking_lot`
2. 入库 → 写 `detained_vehicle`（`IN_YARD`），可选 `dispatch_order_id`
3. 出库 → `OUT`；清理 → `CLEARED`
4. 打印 → 前端用详情字段渲染吊牌并 `window.print()`

### 技术栈增量
- 前端：新增 npm 包 `qrcode` 生成吊牌二维码，无新 UI 组件库
- 后端：无新框架依赖

## 数据库设计

### `parking_lot`

```sql
CREATE TABLE `parking_lot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '停车场名称',
  `code` VARCHAR(50) NOT NULL COMMENT '编码，唯一',
  `address` VARCHAR(255) DEFAULT NULL,
  `contact_name` VARCHAR(50) DEFAULT NULL,
  `contact_phone` VARCHAR(20) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场';
```

### `detained_vehicle`

```sql
CREATE TABLE `detained_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `detain_no` VARCHAR(32) NOT NULL COMMENT '扣押编号，唯一',
  `plate_no` VARCHAR(20) NOT NULL COMMENT '车牌',
  `vehicle_type` VARCHAR(50) DEFAULT NULL COMMENT '车辆类型文本',
  `parking_lot_id` BIGINT NOT NULL COMMENT '所属停车场',
  `dispatch_order_id` BIGINT DEFAULT NULL COMMENT '可选关联救援工单',
  `detain_dept` VARCHAR(100) DEFAULT NULL COMMENT '扣留部门',
  `status` VARCHAR(20) NOT NULL DEFAULT 'IN_YARD' COMMENT 'IN_YARD/OUT/CLEARED',
  `in_time` DATETIME NOT NULL COMMENT '入库时间',
  `out_time` DATETIME DEFAULT NULL,
  `cleared_at` DATETIME DEFAULT NULL,
  `operator_in_id` BIGINT DEFAULT NULL COMMENT '入库操作人 user.id',
  `operator_out_id` BIGINT DEFAULT NULL COMMENT '出库操作人 user.id',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_detain_no` (`detain_no`),
  KEY `idx_plate_no` (`plate_no`),
  KEY `idx_status` (`status`),
  KEY `idx_parking_lot_id` (`parking_lot_id`),
  KEY `idx_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扣留车辆';
```

### 关联与业务规则

1. `detained_vehicle.parking_lot_id` → `parking_lot.id`（必填）
2. `detained_vehicle.dispatch_order_id` → `dispatch_order.id`（**可选**；若传则须存在）
3. **停车场禁用**：不可作为新入库或「编辑换场」的目标；已有引用保留展示
4. **删除停车场**：若存在任意 `IN_YARD` 扣留车则禁止物理删除（提示改 `DISABLED`）
5. **状态机**：
   - 入库：固定 `IN_YARD`，写 `in_time`、`operator_in_id`；生成 `detain_no`
   - 编辑：仅 `IN_YARD` 可改 `plate_no` / `vehicle_type` / `parking_lot_id` / `dispatch_order_id` / `detain_dept` / `remark`
   - 出库：仅 `IN_YARD` → `OUT`，写 `out_time`、`operator_out_id`
   - 清理：仅 `OUT` → `CLEARED`，写 `cleared_at`；之后只读
6. **同牌在库**：全库同一 `plate_no`（入库/编辑前 `trim`，按存库原样精确匹配，不做大小写折叠）同时最多一条 `status = IN_YARD`；编辑改车牌时若目标牌已有其它在库记录则拒绝；`OUT`/`CLEARED` 后可再入库（新 `detain_no`）
7. **扣押编号**：服务端生成，格式固定为 `DV` + `yyyyMMdd` + 当日序号（至少 4 位，不足左补 0），保证唯一

### 权限增量

保留已有 `parking:manage`（MODULE）。新增：

| 权限码 | 类型 | 说明 |
|--------|------|------|
| `parking:query` | BUTTON | 停车场查询 |
| `parking:add` | BUTTON | 新增 |
| `parking:edit` | BUTTON | 编辑 |
| `parking:delete` | BUTTON | 删除 |
| `detain:manage` | MODULE | 扣留车辆管理（侧栏） |
| `detain:query` | BUTTON | 查询 |
| `detain:add` | BUTTON | 入库 |
| `detain:edit` | BUTTON | 编辑 |
| `detain:out` | BUTTON | 出库 |
| `detain:clear` | BUTTON | 清理 |
| `detain:print` | BUTTON | 吊牌打印 |

授予：`ADMIN`、`PARKING_ADMIN`（含原 `parking:manage` 与上述全部 BUTTON）。`DISPATCHER` / `TOW_DRIVER` / `TRAFFIC_POLICE` **不**授予。

### 种子数据
- 2～3 个样例停车场（至少一个 `ENABLED`）
- 若干扣留车：至少 1 条 `IN_YARD`、1 条 `OUT`（便于验收出库/清理与吊牌）

## 后端设计

### 包与类（与现有风格一致）

```
entity/ParkingLot.java, DetainedVehicle.java
mapper/ParkingLotMapper.java, DetainedVehicleMapper.java
service/ParkingLotService.java, DetainedVehicleService.java
controller/ParkingLotController.java (/api/parking)
controller/DetainedVehicleController.java (/api/detain)
dto/ 按需：ParkingLotRequest、DetainInRequest、DetainUpdateRequest 等
```

### API

统一前缀 `/api`，响应 `Result<T>`。

**停车场** `/api/parking`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/list` | `parking:query` | keyword、status |
| GET | `/{id}` | `parking:query` | 详情 |
| POST | `/` | `parking:add` | 创建 |
| PUT | `/{id}` | `parking:edit` | 更新 |
| DELETE | `/{id}` | `parking:delete` | 无在库扣留车才删 |

**扣留车辆** `/api/detain`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/list` | `detain:query` | plateNo、detainNo、status、parkingLotId、detainDept |
| GET | `/{id}` | `detain:query` | 详情；enrich 停车场名、可选工单号 |
| POST | `/` | `detain:add` | 入库 |
| PUT | `/{id}` | `detain:edit` | 仅 IN_YARD |
| POST | `/{id}/out` | `detain:out` | → OUT |
| POST | `/{id}/clear` | `detain:clear` | 仅 OUT → CLEARED |

不单独提供 hangtag API：打印页使用 `GET /{id}` 详情字段即可。

### 错误处理
编码冲突、禁用场入库、同牌已在库、工单不存在、非法状态流转、有在库车删场 → 业务错误（`Result` 非成功码），中文文案明确。

## 前端设计

### 路由

| 路径 | 页面 | meta 权限 |
|------|------|-----------|
| `/parkings` | `ParkingList.vue` | `parking:manage` |
| `/detained-vehicles` | `DetainedVehicleList.vue` | `detain:manage` |
| `/detained-vehicles/:id/hangtag` | `HangtagPrint.vue` | `detain:print` |

导航：工作台/侧栏增加「停车场」「扣留车辆」（`v-auth`）。

### 页面要点
- **停车场**：列表 + 新建/编辑；启用/禁用；删除前校验失败时展示后端文案
- **扣留车辆**：筛选（车牌、扣押号、状态、停车场）；入库表单；在库行：编辑 / 出库 / 打印；已出库行：清理 / 打印；已清理：只读 + 可打印
- **吊牌页**：扣押编号、车牌、停车场、入库时间、扣留部门等 + QR（内容为 `detain_no`）；「打印」触发浏览器打印；尽量无侧栏壳（独立布局或打印 CSS 隐藏导航）

### API 封装
`frontend/src/api/parking.js`、`detain.js`；请求层复用现有 Axios + JWT。

## 测试与验收

| # | 标准 |
|---|------|
| 1 | `PARKING_ADMIN` / `ADMIN` 可维护停车场；禁用场不可新入库 |
| 2 | 入库生成唯一 `detain_no`，状态 `IN_YARD`；可选关联工单 |
| 3 | 同牌已有 `IN_YARD` 时再入库失败并有明确提示 |
| 4 | 在库可编辑、可出库 → `OUT`；出库后可清理 → `CLEARED`；非法流转失败 |
| 5 | 吊牌页展示关键字段 + QR，可浏览器打印 |
| 6 | 有 `IN_YARD` 车时删除停车场失败 |
| 7 | 无相关权限账号无菜单且接口 403 |

补充用例：出库后再入库同牌成功（新编号）；关联不存在工单拒绝；`OUT`/`CLEARED` 不可编辑；停车场 `code` 唯一冲突。

### 后端单测（最低集）
- `DetainedVehicleService`：入库 / 出库 / 清理状态机；同牌在库冲突；非法流转
- `ParkingLotService`：有在库不可删；禁用场不可被入库选用

## 后续衔接（非本轮）
1. 扣留车统计维度与导出
2. 移动端停车场出入库扫码与照片
3. 施救员端「车辆入库登记」与 PC 扣留记录打通
4. 工单完成后一键带入入库表单

## 交付物清单
- `database` 增量脚本 + `init.sql` 同步
- 后端 Parking / Detain 全栈分层与单测
- 前端 3 路由页面 + 侧栏 + QR 依赖
- 本设计文档
