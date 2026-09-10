# 停车场三步入库字段设计文档

## 概述

停车场移动端入库改为三步向导（车辆信息 → 施救信息 → 停放信息），截图中的字段全部落库，含现场/停放照片。PC 维护停放区域，并在扣车入库/编辑/列表中展示新字段。

本文件修正 [2026-09-10-mobile-parking-workspace-design.md](2026-09-10-mobile-parking-workspace-design.md) 中「扣押编号服务端生成、入库字段对齐 PC 短表单」的表述。

## 需求决议

| 项 | 决议 |
|----|------|
| 布局 | 单页三步向导，不拆路由 |
| 字段 | 截图字段全部保存，含照片 |
| 扣押编码 | 手填，写入 `detain_no`，吊牌 QR 仍为该原文 |
| 入场编号 | 服务端生成 `entry_no`，第三步只读「提交后生成」 |
| 停放区域 | `parking_area` 主数据（按停车场）；车位编号手填 |
| PC | 停车场页维护区域；扣车表单/列表展示新字段；PC 本轮不上传照片（只读） |
| 提交 | 本地填完 → 一次 `POST /api/detain` → 再上传照片 |
| 不做 | 未完成录入草稿、车牌键盘、品牌库、施救员入库写扣车表 |

## 数据

### `parking_area`

`parking_lot_id`、`name`、`status`（ENABLED/DISABLED）。同场名称唯一。

### `detained_vehicle` 新增/变更

- `detain_no`：请求传入，唯一；长度放宽到 64
- `entry_no`：服务端生成，唯一
- 车辆：`brand_model`、`vehicle_color`、`mileage`、`important_equipment`、`has_key`（YES/NO）
- 施救：`rescuer_name`、`rescue_reason`（ACCIDENT/ILLEGAL/RESCUE）、`rescue_method`、`rescue_time`、`rescue_address`
- 停放：`parking_area_id`、`stall_no`
- 保留：`plate_no`、`vehicle_type`、`parking_lot_id`、`detain_dept`、`dispatch_order_id`、`remark`

### `detain_media`

对齐 `dispatch_media`：`detain_id`、`biz_type`（SCENE/PARK）、`file_path`、`sort_order`、`uploaded_by`。文件 `uploads/detain/{id}/`。

## 接口（无新权限码）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/parking/{lotId}/areas` | `parking:query` |
| POST | `/api/parking/{lotId}/areas` | `parking:add` |
| PUT | `/api/parking/areas/{id}` | `parking:edit` |
| DELETE | `/api/parking/areas/{id}` | `parking:delete` |
| POST | `/api/detain` | `detain:add`（body 含手填 `detainNo` 及扩展字段） |
| GET/POST/DELETE | `/api/detain/{id}/media` | query / add / edit |

校验：`detainNo`、`plateNo`、`parkingLotId` 必填；同牌最多一条 IN_YARD；`parkingAreaId` 可空；若传入则必须属于该场且 ENABLED。区域删除时若仍被引用则拒绝。

入场编号：当日 `yyyyMMdd` 前缀 + 序号，保证唯一。

列表/详情 enrich：`parkingAreaName`、`entryNo`。

## 移动端

`pages/detain/checkin.vue` 本地 `step=1|2|3`。

1. 扣押编码*、车牌*、厂牌型号、车辆类型（启用字典）、颜色（色板+手填）、里程、重要装备、有无钥匙
2. 扣押部门、施救人员（默认 `/api/auth/me` 姓名）、原因三选一、施救方式、时间（默认现在）、地点（手填+一次性定位）、现场照片（先本地）
3. 入场编号占位、停车场*、区域（随场）、车位、停放照片 → 提交入库再上传

详情展示全部新字段与两类照片。扫码仍按 `detainNo` 全等查找。

## PC

停车场列表「区域」弹层 CRUD。扣车入库必填扣押编码；编辑/列表增加入场编号、区域及扩展字段；照片只读。
