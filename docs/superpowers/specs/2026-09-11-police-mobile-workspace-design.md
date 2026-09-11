# 移动端：交警工作台设计文档

## 概述

将现有 `mobile-rescuer/` **保持为一个 H5 应用**，按登录账号权限进入工作台。本轮落地 **交警工作台**：个人中心、全部任务列表、只读详情（含过程时间与现场/入库照片）、已完成工单四维评价。施救员与停车场工作台行为保持不变。

交警任务走新建竖切 `/api/mobile/police`，**不**给交警 `dispatch:*`，避免打开 PC 调度菜单。评价写入独立表，一单一次、提交后不可改。

需求来源：《道路交通事故救援派单系统报价清单》— 移动端（交警）。

## 背景与范围

### 已有基础
- 同一 JWT + RBAC；工作台会话 `workspace` = `rescuer` | `parking`；双权限先选工作台，换工作台须退出重登
- 演示账号：`towdriver`（施救）、`parkingadmin`（停车场）、`admin`（施救+扣车）；**无**交警演示账号
- 交警角色 `TRAFFIC_POLICE` 仅有 MODULE `accident:manage`，无按钮权限、无移动端入口
- 工单全量在 `dispatch_order`；现场记录 `dispatch_field_record`；照片 `dispatch_media`（DAMAGE/PARK）
- 施救员任务 API 在 `/api/mobile/rescuer`；停车场复用 `/api/detain`、`/api/parking`
- 报价交警三项：个人中心（无绑车）、任务列表（可看详情或去评价）、任务评价（四维 5 星、扣分制默认好评、意见栏；每个大队只看本辖区——**本轮不做辖区过滤**）

### 需求决议

| 项 | 决议 |
|----|------|
| 客户端 | 继续使用 `mobile-rescuer/`，不新开工程 |
| 本轮功能 | 个人中心 + 全部任务列表 + 只读详情 + 已完成评价 |
| 辖区 | 本轮看全部任务，大队/片区过滤留后续 |
| 可评状态 | 仅 `COMPLETED` |
| 评价次数 | 每单一次，提交后不能改 |
| 默认好评 | 评价页四个维度 UI 默认 5 星，可下调；意见可空。服务端不补默认星 |
| 详情深度 | 工单基础信息 + 签到/完成等过程时间 + 现场摘要与 DAMAGE/PARK 照片；事故点静态地图；不跟踪救援车 |
| 接口 | 方案 1：`/api/mobile/police` 竖切，不授 `dispatch:*` |

### 本轮目标
1. `trafficpolice` 可登录并查看全部任务、看详情与照片、对已完成未评工单打分
2. `towdriver` / `parkingadmin` 仍只进各自工作台，选择页无交警入口（除非账号另有 `accident:*`）
3. `admin` 可在三个工作台中选一个；一次只进一套；退出后才能另选
4. 交警工作台不启动 GPS、不展示绑车

### 明确不做
- 大队/片区过滤、交警绑定片区
- 改评、每个交警各评一次、未完成工单评价
- PC 评价分析 / 效能统计
- 交警建单、派单、接单、签到、现场采集、入库登记
- 独立交警 App；给交警 `dispatch:*`
- 工作台内切换（「我的」不提供切换入口）

## 技术方案

### 总体架构（方案 1）

| 层 | 内容 |
|----|------|
| 登录 / 工作台 | 权限判定 + 可选选择页；`workspace` = `rescuer` \| `parking` \| `police` |
| 施救 / 停车场 | 行为不变 |
| 交警工作台 | 全部任务列表、只读详情、评价页 |
| 我的 | 三套工作台共用；资料走 `/api/auth/me`；仅施救展示绑车 |
| 后端 | `PoliceMobileController` + `PoliceMobileService`；评价表；权限 `accident:query` / `accident:rate` |

**数据流（交警）：**
1. 登录 → 判定权限 → 写入 `workspace=police`（或选择后写入）
2. 第一栏列表 `GET /api/mobile/police/tasks`
3. 详情 `GET /api/mobile/police/tasks/{id}`
4. 已完成未评 → 评价页 → `POST /api/mobile/police/tasks/{id}/rate`

`workspace` 只决定 UI 与 GPS，**不**代替接口鉴权。接口仍按 `accident:*` / `rescuer:*` / `detain:*` 返回 401/403。

### 工作台判定

```
hasRescuer = 权限含 mobile:rescuer 或任一 rescuer:*
hasParking = 权限含 detain:query
hasPolice  = 权限含 accident:manage 或任一 accident:*

三者皆无 → 不写会话，提示「无移动端权限」
恰一种   → 直接进入该工作台
两种或三种 → 进入选择页，用户点选后写入 workspace
```

选择页按权限显示「施救任务」「停车场扣车」「交警任务」。单权限跳过选择页。

### Tab 与导航

原生 tabBar **保持两栏**。

| 栏 | 页面 | 施救 | 停车场 | 交警 |
|----|------|------|--------|------|
| 第一栏 | `pages/workbench/index` | 任务 | 扣车 | 任务 |
| 第二栏 | `pages/mine/index` | 我的 | 我的 | 我的 |

`pages/workbench/index` 按 `workspace` 引用：`TaskList` / `DetainList` / `PoliceTaskList`。交警列表**不**使用施救员的待办/已办/中止分栏。工作台 `onReachBottom` 对 `police` 与 `parking` 一样触发加载下一页。

非 tab 页：

| 路径 | 工作台 | 说明 |
|------|--------|------|
| `pages/workspace/select` | — | 多权限选择 |
| `pages/police/detail` | police | 只读详情 |
| `pages/police/rate` | police | 四维评价 |
| `pages/task/*` | rescuer | 现有详情/现场/入库登记 |
| `pages/detain/*` | parking | 现有入库/详情/扫码 |
| `pages/mine/edit` | 三者 | 编辑资料 |
| `pages/mine/bind` | rescuer | 交警不进入 |

**路由守卫**（扩展现有 `requirePageAccess`）：
- 无 token → 登录
- 有 token、多权限、无 `workspace` → 选择页
- `kind=police` 且当前不是 `police` → `switchTab` 回工作台
- `workspace=police` 打开施救作业页或停车场页 → 回工作台
- `pages/mine/scan` 仍为共用扫码页（绑车/吊牌），交警工作台不提供入口

登录提示增加 `trafficpolice / admin123`。

### 会话与 GPS

`shouldStartGps` 仅 `workspace === 'rescuer'`。交警与停车场均不启动上报。

## 页面与交互

### 任务列表

单列表，分页（`page`/`size`，默认 10）。卡片：单号、状态、事故地点、车牌、当事人。

| 工单状态 | 列表动作 |
|----------|----------|
| 非 `COMPLETED` | 点卡片进详情；无评价按钮 |
| `COMPLETED` 且未评 | 点卡片进详情；「评价」进评价页 |
| `COMPLETED` 且已评 | 点卡片进详情；展示「已评价」，不可再评 |

默认按创建时间倒序。本轮无状态筛选、无片区筛选。

### 任务详情

不复用 `pages/task/detail`（内含接单/自动签到/地图跟踪）。

只读展示：
- 单号、状态、事故地点、救援事由、车牌、当事人、联系方式
- 派单 / 接单 / 签到（含 mode） / 完成时间；退单原因、中止原因（有则显示）
- 有事故坐标且已配置高德 Key：静态地图，只标事故点，不轮询、不标救援车；无坐标或无 Key 时文案降级，与现网任务详情一致
- 有现场记录：车牌、车型、受损、停放 + 受损/停放照片预览
- 已评：只读四星与意见

底栏：已完成未评显示「评价」；已评或未完成不显示提交入口。无接单、退单、签到、现场采集、入库登记、完成。

### 评价页

四个维度各一排 5 星，文案：
1. 到达现场是否及时
2. 处置现场是否规范合理
3. 操作过程是否确保安全
4. 服务态度是否热情耐心

打开时全为 5 星，可点低至 1。意见栏可空。提交前确认。成功后返回该单详情，该单变为已评。非已完成或已评进入时 toast 并退回。

### 我的

与停车场相同：`/api/auth/me` 展示与编辑姓名、手机、邮箱；无绑车卡片、无扫码绑车。

## 数据库设计

### `dispatch_order_evaluation`

```sql
CREATE TABLE `dispatch_order_evaluation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL COMMENT '工单 id',
  `rater_user_id` BIGINT NOT NULL COMMENT '评价人',
  `score_punctual` TINYINT NOT NULL COMMENT '到达及时 1-5',
  `score_standard` TINYINT NOT NULL COMMENT '处置规范 1-5',
  `score_safety` TINYINT NOT NULL COMMENT '操作安全 1-5',
  `score_attitude` TINYINT NOT NULL COMMENT '服务态度 1-5',
  `comment` VARCHAR(500) DEFAULT NULL COMMENT '反馈意见',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交警工单评价';
```

`init.sql` 与 migrate 脚本同步。无外键（与现有表一致）。

### 权限种子

实现时以库中 `max(id)+1` 为准（当前权限 id 至 67）。建议：

| code | type | parent | 说明 |
|------|------|--------|------|
| `accident:query` | BUTTON | `accident:manage` | 交警任务列表/详情 |
| `accident:rate` | BUTTON | `accident:manage` | 提交评价 |

授予：
- `TRAFFIC_POLICE`：`accident:manage`（已有）+ `accident:query` + `accident:rate`
- `ADMIN`：同上两项按钮（及如尚未拥有则补 `accident:manage`），便于三工作台联调
- `DISPATCHER` / `TOW_DRIVER` / `PARKING_ADMIN`：**不**授予

演示用户：`trafficpolice` / `admin123`，角色 `TRAFFIC_POLICE`。

## 后端设计

`PoliceMobileController` → `/api/mobile/police`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/tasks` | `accident:query` | 分页全部工单 + `rated` |
| GET | `/tasks/{id}` | `accident:query` | 详情 |
| POST | `/tasks/{id}/rate` | `accident:rate` | 提交评价 |

列表查询参数：`page`、`size`（`PageParams.normalize`，与 PC 相同）。本轮无 `status` / 片区参数。

列表项至少含：`id`、`orderNo`、`status`、`accidentAddress`、`plateNo`、`partyName`、`partyPhone`、`createTime`、`rated`。

详情 payload：

| 字段 | 说明 |
|------|------|
| `order` | 工单实体（含过程时间与原因字段；可含调度员/施救员姓名展示字段） |
| `fieldRecord` | 现场记录，无则 `null` |
| `medias` | DAMAGE / PARK，无则空列表 |
| `evaluation` | 已评记录，未评 `null` |

不返回 `assignedVehicle`，不提供写现场/签到接口。照片仍通过 `/uploads/{filePath}` 访问。

### `POST /rate` 请求体

```json
{
  "scorePunctual": 5,
  "scoreStandard": 5,
  "scoreSafety": 5,
  "scoreAttitude": 5,
  "comment": "可选"
}
```

服务端规则：
1. 工单不存在 → 「工单不存在」
2. `status` 不是 `COMPLETED` → 「仅已完成工单可评价」
3. 已有评价 → 「该工单已评价」
4. 任一分数不是 1–5 整数 → 「每个维度须为 1 至 5 星」；**不**把缺省补成 5
5. `comment` trim 后空串存 `null`，超过 500 字拒绝
6. 写入 `rater_user_id` = 当前用户；`uk_dispatch_order_id` 冲突视为「该工单已评价」
7. 成功返回该评价记录

不改 `DispatchController`、不改工单状态机。

## 错误处理

| 场景 | 行为 |
|------|------|
| 无施救且无扣车且无交警权限 | 不写会话，提示无移动端权限 |
| 多权限未选工作台 | 只留在选择页 |
| 进错工作台页面 | 回到当前工作台首页 |
| 401 | 清会话回登录（现有 request 拦截） |
| 403 | toast 后端/默认「无权限」 |
| 未完成就评 / 已评再评 / 分数非法 / 工单不存在 | toast 后端中文原文 |
| 评价页打开时已不可评 | toast 并返回 |
| 列表/详情加载失败 | 空态或 toast，不进入评价 |

## 测试与验收

| # | 标准 |
|---|------|
| 1 | `trafficpolice` 登录直接进全部任务列表；无绑车；不上报 GPS |
| 2 | `towdriver` / `parkingadmin` 行为与现在一致；选择页无交警入口 |
| 3 | `admin` 登录出现选择页（施救 / 停车场 / 交警）；选一个进对应工作台；「我的」无切换；须退出重登才能选另一个 |
| 4 | 打开未完成单：能看详情和照片（若有），没有评价入口 |
| 5 | 已完成未评：列表/详情可进评价页，默认 5 星，提交成功后不能再评 |
| 6 | 无 `accident:*` 的账号仍提示无移动端权限（除非有施救或扣车权限） |
| 7 | 交警打开 `/pages/task/scene` 或 `/pages/detain/checkin` 会被打回工作台 |

### 后端单测（最低集）

`PoliceMobileServiceTest`：
- 列表分页：总数、`rated` 对未评/已评正确
- 详情：带出签到/完成时间、现场记录、DAMAGE/PARK；未评 `evaluation` 为 `null`
- 评分成功：`COMPLETED` + 未评 + 1–5 星 → 插入一行
- 拒绝：非 `COMPLETED`、已评、分数越界、工单不存在
- 唯一约束第二次提交 → 「该工单已评价」

现有施救员 / 扣车 / PC 派单用例继续通过。

### 移动端纯函数

- `resolveLoginTarget`：仅交警 → `police`；仅施救/仅停车场不变；两种或三种 → `select`；三种都无 → `none`
- `shouldStartGps('police')` 为 false
- 守卫：`kind=police` 在其它工作台会被打回

### 手工

`trafficpolice` / `admin123`：列表 → 打开进行中单确认无评价 → 打开已完成单 → 评价（可下调一星）→ 再进详情只读已评。PC 工单状态不变。

## 交付物清单

- 本设计文档
- 移动端：登录分流扩展、选择页第三项、工作台交警列表、详情/评价页、守卫与 GPS 门闩、演示文案
- 后端：评价表 + migrate/`init.sql`、权限与 `trafficpolice` 种子、`/api/mobile/police` 三接口与单测

## 后续衔接（非本轮）

1. 按大队/片区过滤交警可见与可评任务
2. PC 评价分析（报价「评价分析」）
3. 允许改评或按大队汇总
4. 交警侧建单 / 下达指令
