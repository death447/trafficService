# 移动端：工作台分流与停车场扣车设计文档

## 概述

将现有 `mobile-rescuer/` **保持为一个 H5 应用**，按登录账号权限进入不同工作台。本轮先落地 **停车场工作台**：在库列表、入库、出库、扫吊牌二维码查找；清理与吊牌打印仍走 PC。施救员工作台行为保持不变。

扣车业务复用现有 `/api/detain`、`/api/parking`，不新建 `/api/mobile/parking` 竖切。

## 背景与范围

### 已有基础
- 同一 JWT + RBAC；演示账号 `towdriver`（施救）、`parkingadmin`（停车场）、`admin`（两者都有）
- 施救员 H5 登录目前要求 `mobile:rescuer` 或任一 `rescuer:*`，`parkingadmin` 无法进入
- 原生 tabBar 两栏：`pages/task/list`（任务）、`pages/mine/index`（我的）；App 启动即尝试 GPS 上报
- PC 扣车：`IN_YARD` → `OUT` → `CLEARED`；入库 `POST /api/detain`；出库 `POST /api/detain/{id}/out`；吊牌 QR 内容为 **扣押编号原文**（如 `DV202609070001`）
- `PARKING_ADMIN` 已有 `parking:*` 与 `detain:*`；`TOW_DRIVER` 没有扣车权限
- 列表 `detainNo` / `plateNo` 为 `LIKE` 模糊匹配；入库成功时 `data` 目前为 `null`

### 需求决议

| 项 | 决议 |
|----|------|
| 客户端 | 继续使用 `mobile-rescuer/`，不新开工程 |
| 本轮功能 | 停车场：在库列表 + 入库 + 出库 + 扫码查找；清理/吊牌/停车场 CRUD 仍用 PC |
| 双权限账号 | 登录后选工作台；一次只进一套；换工作台必须退出重登 |
| 单权限账号 | 跳过选择页，直接进入唯一工作台 |
| 入库字段 | 对齐 PC：车牌、停车场必填；车型、关联工单 id、扣留部门、备注选填；扣押编号服务端生成 |
| 扫码 | 吊牌 QR = 扣押编号；也可手输车牌/编号 |
| 扣车接口 | 复用 PC `/api/detain`、`/api/parking`（方案 1） |

### 本轮目标
1. `parkingadmin` 可登录并完成在库查看 / 入库 / 出库 / 扫码定位
2. `towdriver` 仍只进施救工作台，任务闭环不变
3. `admin` 登录出现工作台选择；未选不能进业务页；退出后才能另选
4. 停车场工作台不启动 GPS、不展示绑车

### 明确不做
- 停车场 CRUD、扣车编辑、清理、吊牌打印、出库原因
- 交警工作台、独立停车场 App
- 新建 `/api/mobile/parking` 竖切
- 施救员「入库登记」写入 `detained_vehicle`（仍只写 `dispatch_field_record`）
- 用户绑定固定停车场；列表默认看全部在库车
- 工作台内切换（「我的」不提供切换入口）

## 技术方案

### 总体架构（方案 1）

| 层 | 内容 |
|----|------|
| 登录 / 工作台 | 权限判定 + 可选选择页；会话字段 `workspace` = `rescuer` \| `parking` |
| 施救工作台 | 现有任务 / 现场 / 入库登记 / 绑车 / GPS |
| 停车场工作台 | 在库列表、入库表单、扣车详情与出库、扫码 |
| 我的 | 两套工作台共用；资料走「当前用户」接口；停车场隐藏绑车 |
| 后端 | 扣车/停车场 API 不变（入库成功改为返回记录）；新增已登录用户资料读写 |

**数据流（停车场）：**
1. 登录 → 判定权限 → 写入 `workspace=parking`（或选择后写入）
2. 第一栏展示在库列表 `GET /api/detain/list?status=IN_YARD`
3. 入库 `POST /api/detain` → toast 扣押编号 → 回列表
4. 详情 `GET /api/detain/{id}`；出库 `POST /api/detain/{id}/out`
5. 扫码得到扣押编号 → 按编号查找 → `IN_YARD` 则进详情

### 工作台判定

```
hasRescuer = 权限含 mobile:rescuer 或任一 rescuer:*
hasParking = 权限含 detain:query

两者皆无 → 不写会话，提示「无移动端权限」
仅施救     → workspace=rescuer，进任务
仅扣车     → workspace=parking，进扣车列表
两者都有   → 进入选择页，用户点选后写入 workspace
```

`workspace` 只决定 UI 与 GPS，**不**代替接口鉴权。接口仍按 `detain:*` / `rescuer:*` 返回 401/403。

### Tab 与导航

原生 tabBar **保持两栏**，避免第三栏让停车场账号看到「任务」。

| 栏 | 页面 | 施救文案 | 停车场文案 |
|----|------|----------|------------|
| 第一栏 | `pages/workbench/index`（替换现 tab 第一项 `pages/task/list`） | 任务 | 扣车 |
| 第二栏 | `pages/mine/index` | 我的 | 我的 |

进入工作台后 `uni.setTabBarItem` 更新第一栏文案（及可选 icon）。  
`pages/workbench/index` 按 `workspace` 引用两个组件：施救用从 `pages/task/list` 抽出的任务列表组件；停车场用在库列表组件。tabBar 第一项只指向工作台页，避免停车场用户落到旧 `pages/task/list` 路由。

非 tab 页用 `navigateTo`：

| 路径 | 工作台 | 说明 |
|------|--------|------|
| `pages/workspace/select` | — | 双权限选择；无 `workspace` 时只能停在此页 |
| `pages/detain/detail` | parking | 扣车详情 + 出库 |
| `pages/detain/checkin` | parking | 入库表单 |
| `pages/task/*` | rescuer | 现有详情/现场/入库登记 |
| `pages/mine/edit` | 两者 | 编辑资料 |
| `pages/mine/bind`、`pages/mine/scan` | rescuer | 停车场不进入 |
| `pages/detain/scan` | parking | 扫吊牌；复用 `utils/scanCode.js`，不走 `RV:` 解析 |

登录页、全局标题改为中性（如「救援移动端」），不再写死「施救员」。

**路由守卫（各业务页 `onShow` 或统一封装）：**
- 无 token → 登录
- 有 token、双权限、无 `workspace` → 选择页
- `workspace=parking` 打开施救页（任务详情/现场/绑车等）→ `switchTab` 回工作台
- `workspace=rescuer` 打开扣车页 → `switchTab` 回工作台

### 会话与 GPS

`setSession` 增加持久化 `workspace`。`clearSession` / `logout` 一并清除。

仅 `workspace === 'rescuer'` 时调用 `startLocationReporter`。停车场工作台、选择页、登录页不启动；App `onShow` 不得在无施救工作台时启动上报。

## 后端设计

### 复用 API（无新权限码）

| 方法 | 路径 | 权限 | 移动端用途 |
|------|------|------|------------|
| GET | `/api/detain/list` | `detain:query` | 在库列表；扫码/手输按 `detainNo` 或 `plateNo` 查 |
| GET | `/api/detain/{id}` | `detain:query` | 详情（含 `parkingLotName`、`orderNo`） |
| POST | `/api/detain` | `detain:add` | 入库 |
| POST | `/api/detain/{id}/out` | `detain:out` | 出库 |
| GET | `/api/parking/list` | `parking:query` | 入库下拉，`status=ENABLED`；`size` 用允许的最大值 100 |

不调用：扣车 PUT、`/clear`、停车场写接口。

### 入库响应（兼容增强）

`POST /api/detain` 成功时 `data` 改为返回新建的 `DetainedVehicle`（至少 `id`、`detainNo`、`plateNo`、`status`）。PC 现有调用忽略 `data` 即可。移动端用 `detainNo` 做成功 toast。

### 当前用户资料（停车场「我的」不可走施救接口）

`GET/PUT /api/mobile/rescuer/profile` 需要 `rescuer:profile`，`parkingadmin` 没有该权限。

新增（任意 **已登录** 用户，无额外权限码）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/me` | 返回当前用户 `id, username, realName, phone, email`（不含密码） |
| PUT | `/api/auth/me` | 更新 `realName, phone, email`；校验与现施救员资料接口相同 |

移动端「我的 / 编辑资料」两套工作台都改走 `/api/auth/me`。施救员原 profile 接口可保留给旧客户端，本轮 H5 不再依赖它。

不新增 `mobile:parking` 模块权限；进入停车场工作台的门闩就是 `detain:query`。

## 前端设计

### 登录

- 演示提示同时给出 `towdriver` / `parkingadmin`（密码均为现网演示密码）
- 登录成功后按「工作台判定」跳转，禁止无权限写入 token
- 已有 token 且已有 `workspace` 时，打开登录页则 `switchTab` 到工作台

### 选择页

两张卡片：「施救任务」「停车场扣车」。只展示当前账号具备的工作台（双权限时两张都可点）。选定后写入 `workspace` 并进入对应 tab。无返回业务页的路径，除非退出。

### 在库列表

- 默认 `status=IN_YARD`，分页与 PC 相同（10/20/50/100，默认 10）
- 筛选：车牌、扣押编号（对应 list 的 `plateNo`、`detainNo`）
- 行：扣押编号、车牌、停车场名、入库时间
- 右上角：有 `detain:add` 才显示「入库」；「扫码」始终对有 `detain:query` 的停车场工作台显示
- 点行 → 详情

### 入库页

字段与 `DetainInRequest` 对齐：

| 字段 | 必填 | UI |
|------|------|-----|
| plateNo | 是 | 输入 |
| parkingLotId | 是 | 下拉，仅 ENABLED |
| vehicleType | 否 | 输入（与 PC 文本车型一致，不强制事故车型字典） |
| dispatchOrderId | 否 | 数字工单 id |
| detainDept | 否 | 输入 |
| remark | 否 | 输入 |

前端空车牌/空停车场直接 toast，不发请求。停车场列表失败则下拉为空并提示，禁止提交。成功 toast 扣押编号后 `navigateBack`。

### 详情与出库

展示：扣押编号、车牌、车型、停车场、扣留部门、关联工单号、入库时间、备注、状态。  
仅 `status === 'IN_YARD'` 且有 `detain:out` 显示「出库」。确认后出库，成功回列表。不做出库原因。

### 扫码

独立页 `pages/detain/scan`，复用 `utils/scanCode.js`（H5 `html5-qrcode`，失败可手输）。  
载荷：`trim` 后的字符串。若匹配 `^RV:\d+$`（绑车码），提示「请扫描吊牌二维码」并停止查找。  
查找：`GET /api/detain/list?detainNo={payload}&page=1&size=10`，在结果中取 **扣押编号全等** 的记录（避免 LIKE 误伤）。

| 结果 | 行为 |
|------|------|
| 全等且 `IN_YARD` | 进详情 |
| 全等但非在库 | toast「该车辆已出库或已清理」 |
| 无全等 | toast「未找到扣留记录」 |
| 空码 / 扫码失败 | toast；可改手输编号再查（手输走同一查找） |

列表筛选手输车牌仍用 `plateNo` LIKE，与 PC 一致，不要求全等。

### 我的

- 展示 `/api/auth/me` 的姓名与账号；编辑资料仍可用
- `workspace=parking`：隐藏「扫码/手输绑车」及绑定车辆卡片
- `workspace=rescuer`：绑车与车辆卡片保持现状
- 退出登录：停 GPS、清 `workspace`、回登录页

## 错误处理

| 场景 | 行为 |
|------|------|
| 无施救且无 `detain:query` | 不写会话，提示无移动端权限 |
| 双权限未选工作台 | 只留在选择页 |
| 进错工作台页面 | 回到当前工作台首页 |
| 401 | 清会话回登录（现有 request 拦截） |
| 403 | toast 后端/默认「无权限」 |
| 同牌已在库、场禁用、工单不存在 | 展示后端中文原文 |
| 出库时记录已非在库 | 展示失败文案，刷新详情或回列表 |
| 扫码无记录 / 非在库 | toast，不跳详情 |
| 停车场下拉失败 | 提示且不可提交入库 |

## 测试与验收

| # | 标准 |
|---|------|
| 1 | `towdriver` 登录直接进任务；无扣车入口；GPS 仍可上报（绑车后） |
| 2 | `parkingadmin` 登录直接进在库列表；无任务/绑车；不启动 GPS |
| 3 | `admin` 登录出现选择页；选一个进对应工作台；「我的」无切换；须退出重登才能选另一个 |
| 4 | 无相关权限账号无法登录移动端 |
| 5 | 入库必填校验 + 成功返回并提示 `detainNo`；同牌在库失败有提示 |
| 6 | 在库详情可出库 → `OUT`；列表默认不再显示该车 |
| 7 | 扫描/粘贴已有在库 `detainNo` 进入详情；已出库编号提示已出库；乱码提示未找到 |
| 8 | 无 `detain:add` / `detain:out` 时隐藏对应按钮（用权限数组测 UI；接口 403 仍 toast） |
| 9 | 停车场工作台打开 `/pages/task/detail` 会被打回工作台 |

### 后端单测（最低集）
- `POST /api/detain` 成功 `data` 含 `detainNo`（或 Service 返回实体后 Controller 包装）
- `GET/PUT /api/auth/me`：未登录 401；登录后读写自己的 `realName/phone/email`，不能改别人
- 现有 `DetainedVehicleService` 入库/出库/同牌冲突用例继续通过

### 手工
`parkingadmin` / `admin123`：列表 → 入库一辆 → 扫编号进详情 → 出库。PC 扣车列表应看到同一条记录。

## 交付物清单
- 本设计文档
- 移动端：登录分流、选择页、工作台 tab、扣车三页、扫码查找、我的按工作台裁剪、GPS 门闩
- 后端：入库返回实体；`/api/auth/me`
- 单测覆盖上述后端最低集

## 后续衔接（非本轮）
1. 交警工作台
2. 清理 / 吊牌打印上移动端
3. 施救员入库登记与 `detained_vehicle` 打通
4. 账号绑定默认停车场
