# 移动端入库按车牌关联运行中工单

## 概述

停车场 H5 入库第一步输入车牌后，自动检索未结束救援工单，按规范化车牌关联 `dispatch_order_id`。命中 1 条自动带上；多条点选；0 条仍可无关联入库。空着的车型、施救地点用工单摘要补全。

## 需求决议

| 项 | 决议 |
|----|------|
| 范围 | 仅移动端 `pages/detain/checkin.vue`；PC 扣车仍手填工单 ID |
| 运行中 | `PENDING`、`DISPATCHED`、`ACCEPTED`；不含 `COMPLETED`、`ABORTED` |
| 匹配字段 | 只比 `dispatch_order.plate_no`，不比现场采集 `dispatch_field_record.plate_no` |
| 车牌规范化 | 去掉空格、`·`、`.` 后转大写，再全等 |
| 命中 1 条 | 自动写入 `dispatchOrderId`，展示单号与状态 |
| 命中多条 | 列出单号、状态、事故地点，点选一条 |
| 命中 0 条 | toast「未找到运行中工单」，允许无关联入库 |
| 空字段带出 | 仅当表单该字段为空：`vehicleType` ← `vehicleTypeName`，`rescueAddress` ← `accidentAddress`；已填不覆盖 |
| 可清除 | 可取消关联；改车牌则清空关联并重新检索 |
| 权限 | 检索用现有 `detain:query`；不给停车场开 `dispatch:query` |
| 不做 | PC 自动检索、模糊包含匹配、用工单覆盖已填字段、草稿、新权限码 |

## 接口

在 `DetainedVehicleController` 中，`GET /{id}` **之前**增加（与 `/list` 同级，避免被当成 id）：

```
GET /api/detain/active-orders?plateNo=
权限：detain:query
```

| 条件 | 行为 |
|------|------|
| `plateNo` 缺省，或规范化后长度 &lt; 2 | `400`，message「请输入车牌」 |
| 无匹配 | `200`，`data.list = []` |
| 有匹配 | `200`，`data.list` 按 `id` 倒序 |

每条摘要只含：`id`、`orderNo`、`status`、`plateNo`、`vehicleTypeName`、`accidentAddress`。

规范化算法（检索与提交共用）：删除所有 Unicode 空白、`·`（U+00B7）、`.` 后 `toUpperCase(Locale.ROOT)`。

`POST /api/detain` 已有 `dispatchOrderId`。若请求带了该字段，在现有「工单必须存在」之上增加：

1. 状态必须是 `PENDING` / `DISPATCHED` / `ACCEPTED`，否则「工单已结束，无法关联」
2. 入库 `plateNo` 与工单 `plateNo` 规范化后必须相等，否则「工单车牌与入库车牌不一致」

不传 `dispatchOrderId` 时行为不变。

## 移动端

第一步车牌：规范化后长度 ≥ 2 时检索。触发：输入停止约 400ms，或失焦。进行中的请求以最后一次为准。

- 1 条：绑定并补空字段；车牌下展示「已关联 {orderNo} / {状态文案}」与清除。
- 多条：弹出列表（单号、状态、地点），点选后同上；可关列表保持不关联。
- 0 条：toast「未找到运行中工单」。
- 检索失败：toast，不打断填表，不改已有关联（除非车牌已变，变则先清空关联再查）。

提交时若有关联，body 带 `dispatchOrderId`。服务端二次校验失败则展示返回 message，不入库。

状态文案：`PENDING` 待派、`DISPATCHED` 已派、`ACCEPTED` 已接单。

## 测试

后端：规范化全等；过滤已完成/中止；1/多/0；提交时车牌不一致或工单已结束拒绝；工单不存在仍拒绝。

移动端：规范化函数；空字段才带出；1 条自动绑、多条不自动绑。

## 明确不做

- 不改 PC 入库表单
- 不把施救「入库登记」写入扣车表
- 不按现场采集车牌回查工单
- 不新增权限码、不改角色种子
