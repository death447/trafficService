# 运营报表模块设计文档

## 概述

在 PC 运营管理平台新增独立「报表」页，按自定义日期区间汇总**调度运营、服务质量、停车场扣留**三类已有数据：数字卡片、图表、表格，并支持导出 Excel。首页概览与实时地图不变。移动端不做。

## 决议

| 项 | 决议 |
|----|------|
| 形态 | 独立一页综合报表（方案 1），不分三个子页，不塞进概览 |
| 使用者 | 仅系统管理员、调度员 |
| 时间 | 自定义起止日期；进入页默认「本月 1 号～今天」（Asia/Shanghai） |
| 呈现 | 卡片 + 图表 + 表格 |
| 导出 | 当前筛选下的汇总 + 明细，一个 xlsx、四张工作表 |
| 图表库 | 前端新增 `echarts` |
| Excel | 后端新增 Apache POI `poi-ooxml` |

## 明确不做

- 定时邮件、CSV、PDF
- 按片区/按车辆钻取、自定义报表配置
- 移动端报表
- 改首页概览口径或布局

## 入口与权限

### 权限种子

新增（id 接续现有 69）：

| id | 名称 | code | 类型 | parent |
|----|------|------|------|--------|
| 70 | 报表管理 | `report:manage` | MODULE | 0 |
| 71 | 报表查询 | `report:query` | BUTTON | 70 |

授予角色：`DISPATCHER`（role_id=2）、`ADMIN`（role_id=5）。交警、施救员、停车场管理员不授予。

`database/init.sql` 与独立 migrate 脚本同步写入，已有库只跑 migrate。

### 前端入口

- 侧边栏「工作台」分组、概览下方：「报表」，`v-auth="'report:manage'"`
- 路由 `/reports`，`meta.permissions: ['report:manage']`
- 无权限访问走现有 403
- 页面标题：报表

## 页面结构

单页 `frontend/src/views/report/Report.vue`。

**筛选条（顶部）**

- 开始日期、结束日期：原生 `<input type="date">`（与排班列表一致）
- 「查询」「导出 Excel」
- 默认：当月 1 日 → 今天
- 前端先校验：两日期必填、开始 ≤ 结束、跨度 ≤ 366 天；不通过则提示且不发请求

**三段内容（查询成功后刷新）**

1. 调度运营
   - 卡片：工单总数、已完成、处置中、中止
   - 折线：按日工单量（区间内每一天都有点，无单日为 0）
   - 柱状/饼图：状态分布
   - 表：最多 50 条，列：单号、事故地址、状态、车牌、施救员、创建时间、派单时间、完成时间；点单号进 `/dispatches/:id`
2. 服务质量
   - 卡片：已评价数；到达及时 / 处置规范 / 操作安全 / 服务态度 均分（保留 1 位小数，无评价显示 `—`）
   - 柱状图：四维均分
   - 表：按施救员，列：姓名、评价单数、四维均分、综合均分（四维再平均，1 位小数）
3. 停车场扣留
   - 卡片：入库、在场、出库
   - 表：按停车场，列：名称、入库、在场、出库

无数据：卡片为 0，图表空状态文案「暂无数据」，表格一行「暂无数据」。评价均分在已评价数为 0 时为 `—`，不显示 0.0。

## 时间与统计口径

时区：`Asia/Shanghai`。闭区间：`from` 当日 00:00:00 ～ `to` 当日 23:59:59。

跨度上限 366 天（含起止日）。

### 调度运营

统计全集：`dispatch_order.create_time` 落在区间内的工单。

| 指标 | 口径 |
|------|------|
| 工单总数 | 全集条数 |
| 已完成 | `status = COMPLETED` |
| 处置中 | `status IN (PENDING, DISPATCHED, ACCEPTED)` |
| 中止 | `status = ABORTED` |
| 按日趋势 | 按 `create_time` 的日历日 `COUNT`，缺日补 0 |
| 状态分布 | 按 `status` 分组 `COUNT`；后端固定返回五态，缺则为 0 |
| 明细 | 全集按 `create_time DESC, id DESC`；接口可带齐名字段；**页面只渲染前 50 条** |

状态展示文案与工单模块一致：待派单 / 已派单 / 已接单 / 已完成 / 已中止。

### 服务质量

只统计「调度全集」中**已有** `dispatch_order_evaluation` 的工单。

| 指标 | 口径 |
|------|------|
| 已评价数 | 上述评价条数 |
| 四维均分 | `AVG(score_punctual / score_standard / score_safety / score_attitude)`，输出 1 位小数 |
| 按施救员 | `GROUP BY rescuer_id`；姓名取用户 `real_name`，空则 `username`；`rescuer_id` 为空归为「未指定」 |
| 综合均分 | 该施救员四维均分的算术平均 |

一单至多一条评价（表有唯一约束）。未评价工单不进入分母。

### 停车场扣留

| 指标 | 口径 |
|------|------|
| 入库 | `detained_vehicle.in_time` 落在区间 |
| 出库 | `out_time` 落在区间（含随后已清理的记录） |
| 在场 | `in_time` 落在区间 **且** 当前 `status = IN_YARD` |
| 按停车场 | 按 `parking_lot_id` 汇总上述三项；名称取停车场 `name` |

不按 `create_time` 统计扣留。清理（`CLEARED`）只要 `out_time` 在区间内仍计入出库。

## API

基路径 `/api/report`。均需登录且 `@PreAuthorize("hasAuthority('report:query')")`。`report:manage` 仅用于菜单/路由。

日期参数：`from`、`to`，格式 `YYYY-MM-DD`。

### `GET /api/report/summary`

成功：`Result.success`，`data` 形如：

```json
{
  "from": "2026-09-01",
  "to": "2026-09-12",
  "dispatch": {
    "total": 0,
    "completed": 0,
    "inProgress": 0,
    "aborted": 0,
    "trend": [{ "date": "2026-09-01", "count": 0 }],
    "statusDist": [
      { "status": "PENDING", "count": 0 },
      { "status": "DISPATCHED", "count": 0 },
      { "status": "ACCEPTED", "count": 0 },
      { "status": "COMPLETED", "count": 0 },
      { "status": "ABORTED", "count": 0 }
    ],
    "orders": [
      {
        "id": 1,
        "orderNo": "RO...",
        "accidentAddress": "",
        "status": "COMPLETED",
        "plateNo": "",
        "rescuerName": "",
        "createTime": "2026-09-10T12:00:00",
        "dispatchedAt": null,
        "completedAt": null
      }
    ]
  },
  "quality": {
    "ratedCount": 0,
    "avgPunctual": null,
    "avgStandard": null,
    "avgSafety": null,
    "avgAttitude": null,
    "byRescuer": [
      {
        "rescuerId": 3,
        "rescuerName": "张三",
        "ratedCount": 1,
        "avgPunctual": 5.0,
        "avgStandard": 5.0,
        "avgSafety": 5.0,
        "avgAttitude": 5.0,
        "avgOverall": 5.0
      }
    ]
  },
  "detain": {
    "inbound": 0,
    "inYard": 0,
    "outbound": 0,
    "byLot": [
      {
        "parkingLotId": 1,
        "parkingLotName": "示例停车场",
        "inbound": 0,
        "inYard": 0,
        "outbound": 0
      }
    ]
  }
}
```

约定：

- `orders` 为全集按时间倒序的**全量列表**（当前数据量可接受）；前端表格截取 50 条。导出工单明细用同一列表。
- 均分无评价时 JSON 为 `null`，前端显示 `—`。
- `trend` 含 from～to 每一天。
- `statusDist` 固定五态，缺则为 0。
- 无匹配数据时仍 200，数字为 0、数组为空（`trend` 仍补日）。

### `GET /api/report/export`

查询参数与 summary 相同，统计口径相同，**先走同一套 `summary` 组装逻辑再写文件**，避免两套 SQL 不一致。

成功：

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="report_{from}_{to}.xlsx"`（仅 ASCII，避免中文头编码问题）

工作表：

1. **汇总**：两列「指标 / 数值」。含工单四项、已评价数、四维均分、扣留三项。均分 `null` 写空单元格。
2. **工单明细**：与页面列一致，状态写中文，时间为 `yyyy-MM-dd HH:mm`。
3. **评价按施救员**：与页面列一致。
4. **扣留按停车场**：与页面列一致。

无行数据时仍有表头。校验失败不返回 xlsx，返回与 summary 相同的 `Result`（`code=400`）。

## 校验与错误

Service 统一校验 `from`/`to`：

- 缺参或无法解析 → `请选择开始和结束日期`
- `from > to` → `开始日期不能晚于结束日期`
- 跨度 > 366 天 → `查询区间不能超过 366 天`

Controller 捕获后 `return Result.error(400, message)`（HTTP 仍可为 200，body `code=400`，与扣留模块一致）。前端拦截 `code !== 200` 展示 `message`。

行为：

- 查询失败：筛选条下错误文案；**保留上一次成功的卡片/图/表**，不整页清空
- 导出失败：仅提示「导出失败」或接口 message，不改页面数据
- 导出成功用 blob 触发下载；若 blob 实为 JSON 错误体，解析 message 提示，不保存假 xlsx

无权限：现有 403 / `AccessDeniedException` 处理。

## 后端结构

新增，不把聚合 SQL 塞进 `DispatchOrderService`：

- `dto/ReportSummary.java` 及内部静态/独立 VO（dispatch / quality / detain）
- `mapper/ReportMapper.java` + XML：按时间窗聚合与明细
- `service/ReportService.java`：校验、补日趋势、补五态、均分四舍五入到 1 位、导出 POI
- `controller/ReportController.java`

`SecurityConfig` 将 `/api/report/**` 与其他业务接口一样纳入 JWT 鉴权（无需匿名）。

## 前端结构

- `views/report/Report.vue`
- `api/report.js`：`getReportSummary`、`exportReport`（`responseType: 'blob'`）
- `App.vue` 菜单与标题映射
- `router/index.js`
- 依赖：`echarts`（页面 `onMounted`/`watch` 初始化折线与柱状，卸载 `dispose`）

不引入 Element Plus / vue-echarts。

## 测试

`ReportServiceTest`（Mockito，不连库）：

1. 合法区间：汇总数字、趋势天数 = 区间天数、五态齐全
2. 评价均分四舍五入到 1 位；无评价时均分为 null
3. 扣留入库/在场/出库分别对应 in_time / IN_YARD / out_time
4. 缺日期、from>to、超 366 天抛出带上述文案的异常
5. `export` 写出 4 个 sheet，名称分别为汇总、工单明细、评价按施救员、扣留按停车场；汇总表含表头

前端实现后浏览器验收（不写 Cypress）：

1. 管理员、调度员可见菜单并可打开；其他角色不可见
2. 默认本月至今，查询后卡片/图/表有数据或空状态
3. 改日期再查询，数字随区间变化
4. 非法日期前端提示且不请求
5. 导出能下载 xlsx，四张表能打开
6. 点工单号进入详情
7. 概览页布局与地图不受影响
