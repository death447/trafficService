# 工单多事故车与当事人设计文档

## 概述

新建/编辑工单时，由「单组车牌 + 车型 + 当事人 + 电话」改为支持 **多辆事故车**，每车独立：车牌、车型、当事人、联系方式。数据落子表 `dispatch_accident_vehicle`；仅 `PENDING` 可改；创建至少 1 辆且每辆车牌必填。

## 已确认决策

| 项 | 选择 |
|----|------|
| 每车字段 | 车牌 + 车型 + 当事人 + 电话 |
| 校验 | 至少 1 辆；每辆 `plate_no` 必填；车型/当事人/电话选填 |
| 可编辑时机 | 仅 `PENDING` |
| 实现 | 子表方案；主表旧列作首条快照同步，降低报表改动 |

## 背景

`dispatch_order` 现有列：`plate_no`、`vehicle_type_id`/`vehicle_type_name`、`party_name`、`party_phone`。PC 创建/详情编辑、列表、警务、扣押按牌匹配均假定一单一牌。

## 数据模型

### `dispatch_accident_vehicle`

| 列 | 说明 |
|----|------|
| `id` | PK |
| `dispatch_order_id` | 工单 |
| `sort_order` | 展示顺序，从 0 |
| `plate_no` | 必填 |
| `vehicle_type_id` | 可空 |
| `vehicle_type_name` | 快照，可空 |
| `party_name` / `party_phone` | 可空 |
| `create_time` / `update_time` | |

索引：`idx_order_id`；`idx_plate_no`（扣押按牌）。

### 主表兼容

- 迁移：旧工单有车牌或当事人等信息时插入 1 条子表行
- 写路径：全量维护子表后，**将 `sort_order=0`（首条）同步回** `dispatch_order` 的 `plate_no`/车型/party 列
- 读路径：详情/编辑以 `accidentVehicles[]` 为准

### 校验

- `accidentVehicles.size >= 1`
- 每条 `plateNo` trim 后非空；同单内车牌去重
- `vehicleTypeId` 非空时须为启用字典，并写 `vehicleTypeName` 快照

## 建单 / 编辑与 API

### PC

- `DispatchCreate.vue`：多行事故车，默认 1 行，可增删（至少 1）
- `DispatchDetail.vue`：`PENDING` 多行编辑；其它状态只读列表
- 保存：`accidentVehicles` 全量替换（先删后插）

### API

- `POST /api/dispatch`、`PUT /api/dispatch/{id}`：body 含 `accidentVehicles[]`
- `GET /api/dispatch/{id}`：返回 `accidentVehicles[]`
- 列表接口：可继续用主表快照列展示首牌；可选返回拼接字段或前端用详情——列表页用主表快照 + 若后端提供 `accidentVehicleCount`/`accidentPlatesSummary` 更佳；**最低要求**：列表仍显示主表首条快照，详情展示全部

无新权限。

## 列表、扣押与展示

| 触点 | 行为 |
|------|------|
| 工单/移动/交警列表 | 主表快照展示首牌；详情/只读区列出全部；拼接策略：详情内完整列表 |
| 详情当事人展示 | 列出每车的当事人与电话 |
| 报表 | 本期沿用主表首条快照，不改「一车一行」导出 |
| 扣押按牌 | 查子表 `plate_no`；带出匹配行的车型名 |
| 移动现场预填 | 仍用首条车牌/车型；现场 `field_record` 不强制绑事故车子表行 |

## 非目标

- 现场采集与事故车子表一对一绑定
- 报表一车一行导出
- 进行中工单改事故车
- 微信小程序

## 测试要点

- 建单 2 车成功；0 车或空车牌失败；同单重复车牌失败
- PENDING 全量替换子表；非 PENDING 拒绝改子表
- 迁移后旧单详情可见 1 条事故车
- 扣押按第二辆车牌能命中工单
