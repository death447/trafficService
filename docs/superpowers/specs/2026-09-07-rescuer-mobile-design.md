# 施救员移动端（uni-app）设计文档

## 概述

在现有 Vue3 PC + Spring Boot3 救援调度系统之上，落地 **施救员移动端** 完整能力清单：个人中心与资料编辑、扫码绑车、任务列表（待办/已办/中止）、接单/退单、500m 自动签到与无定位手动签到、现场数据采集与入库登记（含照片）、工单完成。

客户端采用 **uni-app（Vue3）** 独立工程 `mobile-rescuer/`，首期编译 **H5**；后端新增 `/api/mobile/rescuer` 竖切 API，JWT + `TOW_DRIVER` 权限；照片存本地磁盘 `uploads/`。

本轮不包含：交警端、停车场端、效能/评价统计、对象存储、原生壳上架、写入 `detained_vehicle`、实时轨迹推送。

## 背景与范围

### 已有基础
- 认证：JWT + Spring Security + `@PreAuthorize`
- 工单：`dispatch_order` 状态 `PENDING → DISPATCHED → COMPLETED/ABORTED`；派单占车、完成/中止释车
- 施救车辆：`rescue_vehicle.driver_user_id` 可绑施救员
- 角色：`TOW_DRIVER`；PC 调度/车辆/片区/排班/停车场/扣留已就绪
- 需求来源：《道路交通事故救援派单系统功能清单》— 移动端（施救员）

### 范围决议
- 客户端：**C** uni-app
- 功能深度：**D** 完整清单（含扫码绑车、自动签到、现场/入库照片）
- 照片存储：**A** 本地磁盘
- 状态机：**A** 增加 `ACCEPTED`；退单 → **A** `PENDING` 并清空人车、车辆 `IDLE`
- 入库登记：**B** 只写工单现场表，不创建 `detained_vehicle`
- 签到：**A** 距事故点 ≤500m 自动成功；无定位才可手动签到并记原因

### 本轮目标
1. uni-app 施救员端可登录并完成任务闭环
2. 工单状态扩展与接单/退单/签到/完成 API
3. 现场与入库文本 + 照片上传回看
4. 扫码 `RV:{vehicleId}` 绑车；PC 车辆页出示二维码
5. PC 工单状态文案识别「已接单」

### 明确不做
- 交警端 / 停车场端移动应用
- 统计分析、效能分析、评价分析
- MinIO/S3、服务端 PDF、第三方对接
- 微信小程序审核与 App Store 上架（工程可预留）
- 后台静默自动签到轮询
- 强制从施救端写入扣留车正式台账

## 技术方案

### 总体架构（方案 1）

| 层 | 内容 |
|----|------|
| 移动端 | `mobile-rescuer/` uni-app Vue3；登录、我的、任务、现场、入库 |
| 后端 | `RescuerMobileController` + Service；扩展 `DispatchOrderService` |
| 存储 | `uploads/dispatch/{orderId}/...`；Web 映射 `/uploads/**` |
| PC 增量 | 车辆二维码展示；工单 `ACCEPTED` 文案 |

**数据流：**
1. 调度派单 → `DISPATCHED`，指定 `rescuer_id` + `vehicle_id`
2. 施救员待办见单 → 接单 `ACCEPTED` 或退单回 `PENDING`
3. 定位签到（AUTO≤500m / MANUAL+原因）→ 写签到字段
4. 现场/入库 upsert 文本 + media 上传
5. 完成 → `COMPLETED`，车辆 `IDLE`

### 技术栈增量
- 新建 uni-app 工程（Vue3 + Vite 系官方模板）
- 后端：无强制新框架；multipart 文件上传；静态资源映射
- PC：可用现有 `qrcode` 包展示车辆码

## 状态机

```
PENDING → DISPATCHED → ACCEPTED → COMPLETED
              ↓            ↓
           ABORTED    退单 → PENDING（清空 vehicle_id、rescuer_id；车辆 IDLE）
```

| 动作 | 前置 | 结果 |
|------|------|------|
| 调度派单 | `PENDING` | `DISPATCHED`，绑人车，车 `BUSY`（现有） |
| 接单 | `DISPATCHED` 且 `rescuer_id = 当前用户` | `ACCEPTED`，`accepted_at` |
| 退单 | `DISPATCHED` 或 `ACCEPTED`（**未签到**） | `PENDING`，清空人车，`reject_reason`，车 `IDLE` |
| 签到 / 现场 / 入库 | 须 `ACCEPTED` | 写签到或现场/媒体表 |
| 完成 | `ACCEPTED` 且已签到 | `COMPLETED`，释车 |
| PC 中止 | `PENDING` / `DISPATCHED` / `ACCEPTED` | `ABORTED`；若已占车则释车 |

**约定：** 已签到后禁止退单（须完成或由调度中止）。

PC「处理中」类展示应对齐 `DISPATCHED` + `ACCEPTED`。

## 数据库设计

### `dispatch_order` 增量

```sql
ALTER TABLE `dispatch_order`
  ADD COLUMN `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
  ADD COLUMN `checked_in_at` DATETIME DEFAULT NULL COMMENT '签到时间',
  ADD COLUMN `checkin_lng` DECIMAL(10,7) DEFAULT NULL,
  ADD COLUMN `checkin_lat` DECIMAL(10,7) DEFAULT NULL,
  ADD COLUMN `checkin_mode` VARCHAR(20) DEFAULT NULL COMMENT 'AUTO/MANUAL',
  ADD COLUMN `checkin_remark` VARCHAR(500) DEFAULT NULL,
  ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '最近退单原因';
```

`status` 注释扩展为：`PENDING/DISPATCHED/ACCEPTED/COMPLETED/ABORTED`。

进行中占车统计：`status IN ('DISPATCHED','ACCEPTED')`（替换原先仅 `DISPATCHED` 的计数，避免已接单被误判为空闲）。

### `dispatch_field_record`

```sql
CREATE TABLE `dispatch_field_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `plate_no` VARCHAR(20) DEFAULT NULL COMMENT '事故车辆车牌',
  `vehicle_type` VARCHAR(50) DEFAULT NULL,
  `damage_desc` VARCHAR(1000) DEFAULT NULL COMMENT '受损描述',
  `scene_remark` VARCHAR(500) DEFAULT NULL,
  `park_address` VARCHAR(255) DEFAULT NULL COMMENT '停放位置说明',
  `park_remark` VARCHAR(500) DEFAULT NULL,
  `scene_submitted_at` DATETIME DEFAULT NULL,
  `park_submitted_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场与入库登记';
```

### `dispatch_media`

```sql
CREATE TABLE `dispatch_media` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(20) NOT NULL COMMENT 'DAMAGE/PARK',
  `file_path` VARCHAR(500) NOT NULL COMMENT '相对 uploads 路径',
  `sort_order` INT DEFAULT 0,
  `uploaded_by` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_biz` (`dispatch_order_id`, `biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场媒体';
```

### 权限种子（建议 id 自 53 起，实现时以库中 max(id)+1 为准）

| code | 说明 |
|------|------|
| `mobile:rescuer` | MODULE |
| `rescuer:profile` | 资料查询/编辑 |
| `rescuer:bind-vehicle` | 扫码绑车 |
| `rescuer:task` | 任务列表/详情 |
| `rescuer:accept` / `rescuer:reject` | 接单/退单 |
| `rescuer:checkin` | 签到 |
| `rescuer:scene` / `rescuer:park` | 现场/入库 |
| `rescuer:complete` | 完成工单 |

授予 `TOW_DRIVER`（role_id=3）与 `ADMIN`（role_id=5）。

### 扫码绑车

- 载荷明文：`RV:{vehicleId}`（本轮不做短签；YAGNI）
- 写入 `rescue_vehicle.driver_user_id`；同一用户绑新车时清除其旧绑
- PC `VehicleList` 增加二维码展示/打印（前端生成）

## API 设计

前缀：`/api/mobile/rescuer`。统一 `Result<T>`；业务冲突抛 `RuntimeException`，Controller 捕获为 `Result.error(message)`。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/profile` | `rescuer:profile` | 当前用户 |
| PUT | `/profile` | `rescuer:profile` | 仅更新 `realName`/`phone`/`email`；不可改密码、用户名、角色 |
| POST | `/bind-vehicle` | `rescuer:bind-vehicle` | `{ "qrPayload": "RV:123" }` |
| GET | `/vehicle` | `rescuer:bind-vehicle` | 当前绑定车 |
| GET | `/tasks` | `rescuer:task` | `tab=todo\|done\|aborted` |
| GET | `/tasks/{id}` | `rescuer:task` | 详情含现场摘要 |
| POST | `/tasks/{id}/accept` | `rescuer:accept` | → ACCEPTED |
| POST | `/tasks/{id}/reject` | `rescuer:reject` | `{ reason }` |
| POST | `/tasks/{id}/checkin` | `rescuer:checkin` | `{ lng, lat, mode, remark? }` |
| POST | `/tasks/{id}/scene` | `rescuer:scene` | upsert 现场字段 |
| POST | `/tasks/{id}/park` | `rescuer:park` | upsert 入库说明 |
| POST | `/tasks/{id}/media` | scene/park 对应权限 | multipart：`file` + `bizType` |
| GET | `/tasks/{id}/media` | `rescuer:task` | 列表 |
| POST | `/tasks/{id}/complete` | `rescuer:complete` | 已签到 → COMPLETED |

**任务 Tab 过滤（`rescuer_id = 当前用户`）：**
- `todo`：`DISPATCHED`、`ACCEPTED`
- `done`：`COMPLETED`
- `aborted`：`ABORTED` 且 `rescuer_id = 当前用户`（中止时保留原 `rescuer_id` 不清空，以便本人可见；退单回 `PENDING` 会清空 `rescuer_id`，故退单成功的单不再出现在本人列表）

**签到规则：**
- `mode=AUTO`：必须有 lng/lat；与工单事故点 Haversine ≤ 500m，否则失败
- `mode=MANUAL`：无有效定位或客户端声明无法定位；`remark` 必填；可不传坐标或传近似坐标
- 距离计算后端执行，与 `GeoUtils` 同库工具扩展

**上传约束：** jpg/png/jpeg/webp；单文件 ≤ 5MB；路径不可跳出 `uploads/`。

**PC 既有接口调整：**
- `complete`：允许从 `ACCEPTED` 完成（若仍仅 PC 完成则移动 complete 专用；本轮两边均支持 `ACCEPTED→COMPLETED`）
- `abort`：允许 `ACCEPTED`
- 占车计数含 `ACCEPTED`

## uni-app 页面

工程目录：`mobile-rescuer/`（仓库根下与 `frontend/` 并列）。

| 页面 | 路径 | 职责 |
|------|------|------|
| 登录 | `pages/login/index` | `/api/auth/login`，校验施救相关权限 |
| 我的 | `pages/mine/index` | 资料、绑定车辆、退出 |
| 资料编辑 | `pages/mine/edit` | PUT profile |
| 扫码绑车 | `pages/mine/bind` | 扫码或手输 `RV:` |
| 任务列表 | `pages/task/list` | Tab 待办/已办/中止，默认待办 |
| 任务详情 | `pages/task/detail` | 接单/退单/签到/跳转现场与入库/完成 |
| 现场采集 | `pages/task/scene` | 文本 + 拍照 `DAMAGE` |
| 入库登记 | `pages/task/park` | 停放说明 + 拍照 `PARK` |

请求：`baseURL` 可配置；Header `Authorization: Bearer`；401 清 token 回登录。

## 错误处理与安全

- 非本人任务：拒绝
- 非法状态：中文业务错误信息
- 文件类型/大小校验；下载仅通过受控 URL
- 移动 API 全部需认证 + 方法级权限
- 绑车仅操作 `driver_user_id`，不改车辆状态机越权

## 测试要点

- 接单 → ACCEPTED；非派给自己的单接单失败
- 退单 → PENDING + IDLE；已签到后退单失败
- AUTO 501m 失败；499m 成功；MANUAL 无 remark 失败
- 完成未签到失败；签到后完成释车
- PC abort ACCEPTED 释车
- 媒体 bizType 非法拒绝
- 无 `rescuer:*` 权限 403

## 验收标准

| # | 标准 |
|---|------|
| 1 | `TOW_DRIVER` 登录移动端可见派给自己的待办 |
| 2 | 接单/退单状态与车辆占用符合状态机 |
| 3 | ≤500m AUTO 签到成功；超距失败；MANUAL+原因成功 |
| 4 | 现场/入库文本与照片可保存回看 |
| 5 | 扫码绑车一人一车；PC 可出示 `RV:{id}` |
| 6 | 已签到可完成并释车 |
| 7 | 无权限账号 mobile API 403 |
| 8 | PC 工单可区分已接单 |

## 实现分期建议（同一 Spec，计划内拆 Task）

1. DB + 权限 + 状态机后端（接单/退单/签到/完成）+ 单测  
2. 现场表/媒体上传 API + 单测  
3. 绑车 API + PC 车辆二维码  
4. uni-app 骨架登录/列表/详情动作  
5. 现场/入库页与上传联调  
6. 回归与文档
