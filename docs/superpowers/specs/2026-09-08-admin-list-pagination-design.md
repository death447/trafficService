# 管理端列表真分页设计文档

## 概述

将管理端全部业务列表从「假分页」（全量查询 + 忽略 page/size）改为**数据库真分页**。默认每页 10 条，可选 10 / 20 / 50 / 100。本轮不含移动端任务列表。

## 决议

| 项 | 决议 |
|----|------|
| 范围 | 仅管理端 Web |
| 分页实现 | SQL `COUNT` + `LIMIT/OFFSET` 真分页 |
| 每页条数 | 默认 10；可选 10 / 20 / 50 / 100 |
| 方案 | 统一契约 + Mapper 条件 SQL + 共享前端分页组件 |

## 覆盖列表页

用户、角色、权限、工单、施救车辆、片区、排班、停车场、扣留车辆、车型。

## API 契约

**查询参数**
- `page`：从 1 开始，默认 1；`<1` 视为 1
- `size`：默认 10；仅允许 `{10,20,50,100}`，否则回退 10
- 各列表原有筛选参数保持不变

**响应 `Result.data`**
```json
{ "list": [], "total": 0, "page": 1, "size": 10 }
```
- `total`：筛选后总条数
- `list`：仅当前页

**例外**
- `/vehicle-type/enabled`、附近车辆等非列表接口不变
- 表单下拉需要较多选项时传 `size=100`（上限），不再使用 `size=500` 假全量

## 后端

- `PageParams`：规范化 `page/size/offset`
- 各列表 Service：`count(filters)` + `selectPage(filters, offset, size)`
- Mapper：动态 WHERE + 稳定 `ORDER BY`（如 `id DESC` 或业务时间 DESC）+ `LIMIT`
- Controller：写入规范化后的 `page/size` 与真实 `total`

## 前端

- `PaginationBar.vue`：总数、页码、上一页/下一页、每页条数；改 size 或重新查询时重置到第 1 页
- 各 `*List.vue` 接入分页状态并传参
- 不引入新 UI 组件库

## 验收

1. 列表默认每页 10，可切换 10/20/50/100  
2. 翻页仅返回当前页；`total` 随筛选变化  
3. 筛选/重置回到第 1 页  
4. `total=0` 时分页条正常  
5. 移动端本轮不变  
