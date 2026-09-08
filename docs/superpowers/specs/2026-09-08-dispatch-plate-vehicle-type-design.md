# 工单车牌与事故车型字典设计文档

## 概述

在新建/编辑救援工单时增加**事故车辆车牌**与**车型**选填字段；车型由独立维护表管理，挂在系统管理下，支持启用/停用。施救员移动端现场采集预填工单值并可修改，保存写入现场记录，不回写工单快照。

本轮采用：车型字典表 + 工单存 `vehicle_type_id` 与名称快照 + `plate_no`。

## 背景与范围

### 已有基础
- 工单 `dispatch_order`：事故地点、原因、调度员/车辆/施救员、状态机已就绪；**无**事故车牌/车型字段
- 现场记录 `dispatch_field_record`：已有自由文本 `plate_no`、`vehicle_type`（施救员现场填写）
- 管理端新建工单页、列表/详情；移动端 `scene` 页已有车牌/车型手填
- RBAC：JWT + `@PreAuthorize`；工作台系统管理区已有用户/角色/权限

### 需求决议
| 项 | 决议 |
|----|------|
| 新建工单必填性 | 车牌、车型均为**选填** |
| 移动端关系 | 预填工单值，施救员可改 |
| 种子「厢式火车」 | 纠正为**厢式货车** |
| 车型生命周期 | **启用/停用**；停用后不可新建选用；历史用工单快照名展示 |
| 存储方案 | 方案 1：字典表 + `vehicle_type_id` + `vehicle_type_name` 快照 + `plate_no` |

### 本轮目标
1. 事故车型字典 CRUD（无硬删除）+ 17 项种子数据
2. 工单扩展车牌/车型；创建与更新校验启用中的车型并写快照
3. 系统管理入口「车型管理」；新建工单/列表/详情展示
4. 移动端现场采集预填逻辑；车型改为启用字典下拉

### 明确不做
- 车型硬删除
- 现场保存回写 `dispatch_order` 车牌/车型快照
- 扣留车、施救车辆类型字段改造为同一字典（本轮仅事故车型）
- 真分页改造

## 技术方案

### 总体架构

| 模块 | 后端 | 前端 / 移动端 |
|------|------|----------------|
| 车型字典 | Entity/Mapper/Service/`VehicleTypeController` | `/vehicle-types` |
| 工单扩展 | `DispatchOrder` + create/update 校验与快照 | 新建页字段；列表/详情列 |
| 现场预填 | 任务详情已含工单字段 | `scene` 预填 + 下拉 |

**数据流：**
1. ADMIN 维护 `accident_vehicle_type`（启停）
2. 调度建单可选车牌 + 启用车型 → 写 `dispatch_order.plate_no` / `vehicle_type_id` / `vehicle_type_name`
3. 施救员打开现场采集 → 优先现场记录，否则用工单预填 → 保存仅写 `dispatch_field_record`

## 数据库设计

### `accident_vehicle_type`

```sql
CREATE TABLE `accident_vehicle_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '车型名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事故车型字典';
```

### 种子数据（`sort_order` 1–17，均为 ENABLED）

轿车、大客车、半挂货车、黄牌大货车、蓝牌大货车、厢式货车、面包车、房车、越野车、三轮机动车、三轮电动车、人力三轮车、二轮摩托车、二轮电动车、自行车、残疾车、其他。

### `dispatch_order` 增量

```sql
ALTER TABLE `dispatch_order`
  ADD COLUMN `plate_no` VARCHAR(20) DEFAULT NULL COMMENT '事故车辆车牌' AFTER `rescue_reason`,
  ADD COLUMN `vehicle_type_id` BIGINT DEFAULT NULL COMMENT '事故车型字典 id' AFTER `plate_no`,
  ADD COLUMN `vehicle_type_name` VARCHAR(50) DEFAULT NULL COMMENT '车型名称快照' AFTER `vehicle_type_id`;
```

提供迁移脚本 `database/migrate_2026-09-08_dispatch_plate_vehicle_type.sql`，并同步更新 `database/init.sql`。

## API 设计

### 车型字典 `/api/vehicle-type`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/list` | `vehicle-type:query` | 全量列表；可选 `status` 筛选 |
| GET | `/enabled` | 已登录 | 仅 ENABLED，按 `sort_order`；供工单下拉 |
| GET | `/{id}` | `vehicle-type:query` | 详情 |
| POST | `/` | `vehicle-type:add` | 新增（名称唯一） |
| PUT | `/{id}` | `vehicle-type:edit` | 改名称/排序/备注/启停 |

不提供 DELETE。

### 工单变更

- 创建/更新请求体可选：`plateNo`、`vehicleTypeId`
- 空字符串规范为 `null`
- 若 `vehicleTypeId` 非空：必须存在且 `ENABLED`，否则 400；成功时写入对应 `vehicleTypeName`
- 若清空车型：`vehicleTypeId`、`vehicleTypeName` 均置空
- 列表/详情响应包含 `plateNo`、`vehicleTypeId`、`vehicleTypeName`

### 权限种子

- MODULE：`vehicle-type:manage`（系统管理）
- BUTTON：`vehicle-type:query` / `vehicle-type:add` / `vehicle-type:edit`
- 赋给角色 `ADMIN`（及现有 admin 用户角色关联按 init 惯例）

调度员建单不依赖字典管理权限，使用 `/enabled`（已认证即可）。

## 前端设计

### 系统管理 · 车型管理

- 路由：`/vehicle-types`，`meta.permissions: ['vehicle-type:manage']`
- 工作台 Home 增加入口（`v-auth="'vehicle-type:manage'"`），标签「系统」
- 列表：名称、排序、状态、启停操作、编辑；新增弹窗
- 无删除按钮

### 新建工单

- 字段：车牌号码（文本选填）、车型（下拉选填，数据 `/api/vehicle-type/enabled`，可清空）
- 提交携带 `plateNo`、`vehicleTypeId`

### 列表 / 详情 / 编辑

- 列表增加「车牌」「车型」列（快照名，空显示 —）
- 详情事故信息区展示两项
- PENDING 可编辑时同步可改车牌/车型；改车型按新选项重写快照

## 移动端设计

### 现场采集 `scene`

- 加载任务详情后：
  1. 若现场记录已有车牌/车型 → 用现场记录
  2. 否则用工单 `plateNo` / `vehicleTypeName` 预填
- 车型改为启用字典下拉（与管理端同源 `/enabled` 或任务详情附带启用列表）；保存仍写 `dispatch_field_record.vehicle_type`（名称字符串，与现表一致）
- **不**回写 `dispatch_order` 的车牌/车型字段

## 异常处理

| 场景 | 行为 |
|------|------|
| 车型名称重复 | 400，提示已存在 |
| 引用停用/不存在的 `vehicleTypeId` | 400 |
| 停用车型 | 仅改 status；历史工单继续显示快照名 |
| 权限不足 | 现有 403 |

## 验收标准

1. ADMIN 可维护车型（增改、启停）；种子 17 项含「厢式货车」
2. 新建工单可选填车牌与启用车型；列表/详情可见
3. 停用后下拉不再出现；旧工单仍显示快照名
4. 移动端预填工单值；修改保存进现场记录；工单快照不变
5. 未填车牌/车型仍可成功建单

## 测试建议

- 后端：车型名称唯一；停用 id 不可绑工单；快照写入正确；清空车型清空 id+name
- 前端：权限入口可见性；下拉仅启用项
- 移动端：无现场记录时预填；有现场记录时不覆盖
