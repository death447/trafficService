# 扣车入库改由救援员填写 — 设计文档

## 概述

把「扣留车辆入库」的唯一入口从停车场管理员移到救援员移动端：救援员在任务详情「入库登记」中完成三步表单（车辆信息 / 施救信息 / 停放信息），提交后生成在库扣留车辆记录。停车场管理员只保留在库查看、出库、清理；PC 后台同样不再提供入库。

## 已确认决策

| 项 | 选择 |
|----|------|
| 停车场管理员入口 | 完全移除（移动端 + PC） |
| 救援员入口 | 任务详情「入库登记」，改成三步完整表单 |
| 事故车选择 | 弹出该工单事故车列表；单辆自动选中；车牌可改 |
| 扣押编码 | 救援员手填（纸质凭证） |
| 接口方案 | 新增救援员专用入库接口（`rescuer:park`），复用 `DetainedVehicleService.checkIn` |
| 权限收紧 | 从 `PARKING_ADMIN` 移除 `detain:add` |

## 背景

- 现有移动端入库表单：`mobile-rescuer/src/pages/detain/checkin.vue`（三步；权限 `detain:add`），入口在 `components/DetainList.vue` 的「入库」按钮
- 救援员现有「入库登记」：`pages/task/park.vue`，仅填停放位置/备注 + PARK 照片，写入 `dispatch_field_record`
- 后端入库：`POST /api/detained-vehicle`（`detain:add`）→ `DetainedVehicleService.checkIn`
- 停车场/区域列表接口分别要求 `parking:query`；TOW_DRIVER 无此权限

## 接口设计

全部挂在 `/api/mobile/rescuer`，权限 `rescuer:park`：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/parking-lots` | 已启用停车场列表（`ParkingLotService.list(null,"ENABLED",pp)`，size 100） |
| GET | `/parking-lots/{lotId}/areas` | 该停车场已启用区域（`parkingAreaService.list(lotId,"ENABLED")`） |
| POST | `/tasks/{id}/detain-in` | 提交入库，body `DetainInRequest` |
| GET | `/tasks/{id}/detain` | 查询该工单已创建的入库记录（可空，用于只读回显） |
| POST | `/detain/{detainId}/media` | 上传入库照片（仅 `PARK`），校验该 detain 由本人在本工单创建 |

### `detainIn(userId, orderId, req)` 逻辑

1. `requireAssignmentAccepted(orderId, userId)` — 工单已签到且属于本人
2. `order = dispatchOrderMapper.findById(orderId)`，取该工单 `accidentVehicles`
3. 车牌归一化后必须命中该工单某一事故车，否则 `车牌与工单事故车不一致`
4. 强制覆盖：`req.dispatchOrderId = orderId`；`req.rescuerName = 当前用户 realName/username`
5. 复用 `detainedVehicleService.checkIn(req, userId)`（保留编码唯一、在库唯一、停车场/区域/枚举校验、DAMAGE→SCENE 复制）
6. 回写 `dispatch_field_record`：
   - `parkAddress` = `停车场名` + （有区域时 `/区域名`）+ （有车位时 ` 车位N`）；名称由 `parkingLotService.findById` / `parkingAreaService.findById` 取
   - `parkRemark` = 客户端 `remark`
   - `parkSubmittedAt = now()`
7. 返回创建的 `DetainedVehicle`

### 已入库回显

`GET /tasks/{id}/detain`：校验 assignment 归属后，按 `detainedVehicleMapper` 查 `dispatch_order_id = orderId` 且状态 `IN_YARD` 的最新一条；没有则返回 `data: null`。移动端据此决定展示只读摘要还是可填表单。

### 媒体上传

`POST /detain/{detainId}/media`：
- 校验 `detain.dispatchOrderId` 对应的 assignment 属于当前用户（`requireAssignmentAccepted`）
- 校验 `bizType` 仅 `PARK`
- 复用 `fileStorageService.storeDetainMedia(detainId, file)` 与 `detainMediaMapper.insert`

## 权限迁移

- `database/migrate_2026-10-27_detain_in_to_rescuer.sql`：
  - `DELETE FROM role_permission WHERE permission_id = 48 AND role_id = 4;`（PARKING_ADMIN 去掉 detain:add）
  - 确保 TOW_DRIVER 有 `rescuer:park`（已有，53–62 范围）
- `database/init.sql` 同步：PARKING_ADMIN 权限范围改为 `19 OR (42..52 除 48)`，即 `19 OR 42,43,44,45,46,47,49,50,51,52`

## 前端（移动端）

### `pages/task/park.vue` 改为三步表单

- 参数：`id`（工单 id）
- onLoad：拉 `getTask(id)` 取 `accidentVehicles`；拉停车场列表；拉当前用户
- 第 1 步 车辆信息：扣押编码*、车牌*（默认事故车首辆；多于一辆时可点「选择事故车」弹层切换）、厂牌型号、车辆类型、车辆颜色、行驶里程、重要装备、有无钥匙
- 第 2 步 施救信息：扣押部门、施救人员（默认当前用户，只读）、施救原因、施救方式、施救时间（默认当前）、施救地点（定位按钮）、现场照片（展示该工单 DAMAGE 照片缩略图只读 + 本地上传）
- 第 3 步 停放信息：停车场*、停放区域、车位编号、停放照片、停放位置说明/备注
- 提交：`POST /tasks/{id}/detain-in` → 上传 PARK 照片 → toast → `navigateBack`
- 已入库判断：`onLoad` 调用 `GET /tasks/{id}/detain`；已有记录则展示只读摘要并禁用提交

### 入口与清理

- `pages/task/detail.vue`：「入库登记」按钮文案不变，已入库后显示「查看入库信息」或保持可进入只读
- `components/DetainList.vue`：删除「入库」按钮与 `canAdd`
- 删除 `pages/detain/checkin.vue`，并从 `pages.json` 移除该路由
- `pages/task/detail.vue` 的 `goPark` 不变

## 前端（PC）

`frontend/src/views/detain/DetainedVehicleList.vue`：
- 删除「入库」按钮（`v-auth="'detain:add'"`）
- 删除新建弹窗与 `openCreate` 相关逻辑；保留编辑、出库、清理、打印
- 编辑表单仍可用（管理员可修正已入库信息）

## 测试

后端单测（`RescuerMobileServiceTest` 新增）：
- `detainIn` 成功：车牌命中事故车、`rescuerName` 被覆盖、`dispatchOrderId` 强制、field record 回写
- 未签到 → 抛错
- 车牌不在事故车 → 抛错
- 非本人工单 → 抛错
- 媒体上传：跨工单 → 抛错；`bizType=PARK` 通过

手动验证：
1. 救援员登录 → 接单 → 签到 → 入库登记 → 三步提交
2. PC 扣留车辆出现记录，状态在库，照片含 SCENE（工单受损照片复制）+ PARK
3. PC 工单详情「现场摘要」有停放地址
4. 停车场管理员登录 → 无「入库」按钮，但可出库/清理
5. 直接 `POST /api/detained-vehicle` 用管理员 token → 403

## 明确不做

- 扣押编码自动生成或扫码
- 一个工单多辆事故车批量入库（一次一辆，可重复进入）
- 停车场管理员保留备用入库入口
- PC 后台入库表单改造（直接删除）
