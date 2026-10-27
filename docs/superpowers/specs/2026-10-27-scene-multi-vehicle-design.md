# 现场采集支持多辆事故车 — 设计文档

## 概述

一个救援工单可能关联多辆事故车，但救援员移动端「现场采集」只预填第一辆车，`dispatch_field_record` 也只有单车字段，PC 与交警端现场摘要同样只显示一辆。

本轮把现场采集改为逐车填写：每辆事故车独立记录车型、受损描述和照片，下游三处页面同步逐车展示。

## 已确认决策

| 项 | 选择 |
|----|------|
| 移动端交互 | 卡片列表：每辆车一张卡，各自填车型/受损描述/照片 |
| 数据归属 | 受损描述与照片按车辆独立保存 |
| 下游同步 | PC 工单详情、救援员任务详情、交警详情都改为逐车展示 |
| 现场备注 | 整单共用一份 |
| 车牌 | 取自工单事故车列表，页面只读；无事故车的历史工单保留一张可手填车牌的卡片 |

## 数据模型

### 新表 `dispatch_scene_vehicle`

```sql
CREATE TABLE `dispatch_scene_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `assignment_id` BIGINT DEFAULT NULL,
  `accident_vehicle_id` BIGINT DEFAULT NULL,
  `plate_no` VARCHAR(20) DEFAULT NULL,
  `vehicle_type` VARCHAR(50) DEFAULT NULL,
  `damage_desc` VARCHAR(1000) DEFAULT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_accident_vehicle` (`dispatch_order_id`, `accident_vehicle_id`),
  KEY `idx_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场采集-逐车';
```

### `dispatch_media` 加列

```sql
ALTER TABLE `dispatch_media`
  ADD COLUMN `accident_vehicle_id` BIGINT DEFAULT NULL COMMENT '所属事故车' AFTER `assignment_id`,
  ADD KEY `idx_accident_vehicle_id` (`accident_vehicle_id`);
```

### `dispatch_field_record` 不变

继续存第一辆车的 `plate_no / vehicle_type / damage_desc` 摘要，以及 `scene_remark`、`scene_submitted_at`。历史数据与旧统计不受影响。

## 后端

### 请求 / 响应模型

`SceneRequest`：

```java
String sceneRemark;                 // 整单共用
List<SceneVehicleItem> vehicles;    // 每辆车一项
String plateNo;                     // 兼容旧客户端（vehicles 为空时使用）
String vehicleType;
String damageDesc;
```

`SceneVehicleItem`：`accidentVehicleId`、`plateNo`、`vehicleType`、`damageDesc`。

`SceneVehicleVO`：`id`、`accidentVehicleId`、`plateNo`、`vehicleType`、`damageDesc`、`medias`（`List<DispatchMedia>`）。

`RescuerTaskDetail`、`PoliceTaskDetail`、`DispatchOrder` 各增加 `List<SceneVehicleVO> sceneVehicles`。

### `saveScene(userId, orderId, req)`

1. `requireAssignmentAccepted(orderId, userId)`
2. `vehicles` 非空时：取出工单事故车列表，逐个校验 `plateNo` 命中（归一化比较），不命中抛「车牌与工单事故车不一致」
3. `deleteByOrderId(orderId)`，按顺序逐条 `insert`，`sort_order` 用下标
4. 摘要回写：`field_record.plateNo = vehicles[0].plateNo`，`vehicleType`、`damageDesc` 同理；`vehicles` 为空时用旧字段（兼容）
5. `sceneRemark = req.sceneRemark`，`sceneSubmittedAt = now()`

保存整体在 `@Transactional` 中，删旧插新保持幂等。

### 媒体

`addMedia(userId, orderId, bizType, accidentVehicleId, file)`：

- `accidentVehicleId` 非空时必须是该工单事故车之一，否则抛「事故车辆不属于该工单」
- 仅 `DAMAGE` 允许携带 `accidentVehicleId`；`PARK` 传了就报「媒体类型无效」
- 写入 `dispatch_media.accident_vehicle_id`

`RescuerMobileController` 的 `POST /tasks/{id}/media` 增加 `@RequestParam(required = false) Long accidentVehicleId`。

### 组装 `sceneVehicles`

新增 `SceneVehicleAssembler`（`@Component`）：

```java
List<SceneVehicleVO> assemble(Long orderId, List<DispatchAccidentVehicle> accidentVehicles);
```

- 读 `dispatch_scene_vehicle` 行；为空时返回空列表（前端回退旧摘要）
- 读该工单 `DAMAGE` 媒体，按 `accident_vehicle_id` 挂到对应车辆；未挂车辆的照片不进任何卡片（PC 端保留「其他照片」展示为可选项，本轮不做，未关联照片仍在整体照片区可见）

调用点：`RescuerMobileService.getTask`、`PoliceMobileService.getTask`、`DispatchOrderService.findById`。

## 前端（移动端）

### `pages/task/scene.vue`

- 移除单一 `plateNo` 表单，改为 `vehicles` 数组，初始由 `order.accidentVehicles` 生成；无事故车时生成一张空卡片
- 每张卡：车牌（只读文本）、车型（`picker`，取值 `listEnabledVehicleTypes`）、受损描述（`textarea`）、照片网格 + 「拍照/选图」（上传时带 `accidentVehicleId`）
- 页面底部一张卡：「现场备注」整单共用
- 保存：`POST /tasks/{id}/scene`，body `{ sceneRemark, vehicles: [...] }`
- 回显：优先用 `detail.sceneVehicles`；为空时用 `fieldRecord` 填第一张卡，保持历史数据可编辑

### `api/rescuer.js`

`uploadMedia(id, filePath, bizType, accidentVehicleId)` 增加第四个可选参数，通过 `formData` 传给后端。

### `pages/task/detail.vue` 与 `pages/police/detail.vue`

「现场摘要」改为逐车列表：每辆车显示车牌/车型/受损描述及其照片；`sceneVehicles` 为空时回退现有 `fieldRecord` 单车展示。

## 前端（PC）

`frontend/src/views/dispatch/DispatchDetail.vue` 现场采集面板：

- 保留顶部「采集时间」「现场备注」
- 下方逐车渲染：每辆车一块，含车牌、车型、受损描述、该车照片
- `sceneVehicles` 为空时回退到 `fieldRecord` + 全部 DAMAGE 照片的现有布局

## 数据库脚本

- 新增 `database/migrate_2026-10-27_scene_multi_vehicle.sql`（建表 + `ALTER TABLE dispatch_media`）
- `database/init.sql` 同步：新表定义、`dispatch_media` 列与索引

## 测试

后端单测（`RescuerMobileServiceTest`）：

- `saveScene` 多车：插入条数、`sort_order`、摘要回写第一辆、`sceneRemark` 与提交时间
- `saveScene` 车牌不属于工单事故车 → 抛错且不写库
- `saveScene` 未接单 → 抛错（已有用例保留）
- `addMedia` 带合法 `accidentVehicleId` → 写入该字段
- `addMedia` 带他单车辆的 `accidentVehicleId` → 抛错
- `addMedia` `PARK` + `accidentVehicleId` → 抛错
- `getTask` 返回 `sceneVehicles` 且照片按车分组

手工验证：

1. 两辆事故车的工单 → 移动端现场采集显示两张卡，逐车填受损描述、各传一张照片 → 保存
2. 退出重进，两张卡内容与照片正确回显
3. PC 工单详情现场采集逐车展示，照片各归其车
4. 移动端任务详情、交警详情逐车展示
5. 历史单车工单（无 `scene_vehicles` 数据）三处仍正常显示原摘要

## 明确不做

- 不改 `dispatch_field_record` 表结构
- 不做图片在车辆之间拖拽调整归属
- 不做现场采集的历史版本/审计
