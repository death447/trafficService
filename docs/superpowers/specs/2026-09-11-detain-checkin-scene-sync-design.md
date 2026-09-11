# 入库同步签到时间与工单受损照

## 概述

停车场 H5 入库在已关联运行中工单时：施救时间用工单施救员签到时间覆盖（未签到则保持打开页时的当前时刻）；第二步预览工单现场采集的 `DAMAGE` 照片；提交时服务端把这些文件复制为扣车 `SCENE`。停车场仍可再拍。

本文件建立在 [2026-09-11-detain-checkin-active-order-design.md](2026-09-11-detain-checkin-active-order-design.md) 之上，不改动该文的匹配/1 条自动绑/多条点选规则。

## 需求决议

| 项 | 决议 |
|----|------|
| 范围 | 仅移动端入库 + 现有 `GET /api/detain/active-orders` 与 `POST /api/detain` |
| 施救时间 | 绑工单且 `checked_in_at` 非空 → 覆盖表单施救时间；未签到 → 保持打开页时的 `formatNow()`；仍可手改 |
| 照片预览 | 绑工单后第二步展示该单 `DAMAGE` 图（`/uploads/{file_path}`） |
| 照片落库 | 提交入库成功后同一事务内，服务端复制 `DAMAGE` → 扣车 `SCENE`；再上传停车场本地新拍 |
| 源文件缺失 | 跳过该张，不使整单入库失败 |
| 取消关联 | 去掉工单预览图；施救时间回到当前时刻；本地新拍保留 |
| 权限 | 仍用 `detain:query` / `detain:add`；不给停车场开 `rescuer:scene` |
| 不做 | PC 入库同步、复制工单 `PARK` 图、新权限码、引用而不复制 |

## 接口

`GET /api/detain/active-orders` 每条 `ActiveDispatchSummary` 增加：

| 字段 | 含义 |
|------|------|
| `checkedInAt` | 工单 `checked_in_at`，未签到 `null` |
| `damagePhotoPaths` | 该单 `dispatch_media` 中 `biz_type=DAMAGE` 的 `file_path`，顺序 `sort_order, id`；无图为 `[]` |

不新增路径。列表仍按规范化车牌过滤未结束工单。

`POST /api/detain`：在扣车行插入成功后、返回前，若 `dispatchOrderId` 非空：

1. 查出该工单全部 `DAMAGE`
2. 将磁盘文件从 `uploads/{file_path}` 复制到 `uploads/detain/{detainId}/{新文件名}`（扩展名保留，仅 jpg/jpeg/png/webp）
3. 插入 `detain_media`：`biz_type=SCENE`，`uploaded_by` 为当前入库操作人
4. 源路径非法或不存在：跳过该张
5. 复制 IO 失败：抛错，事务回滚整单入库

无 `dispatchOrderId` 时不复制。停车场随后 `POST /api/detain/{id}/media` `SCENE` 行为不变。

## 移动端

`bindOrder`：

- 调用现有空字段补全（车型、施救地点）
- 若 `checkedInAt` 有值，将施救时间格式化为 `yyyy-MM-dd HH:mm:ss` 写入 `form.rescueTime`
- 工单预览图列表 = `damagePhotoPaths` 转成可展示 URL（现有 `mediaUrl`）

第二步现场照片区：先渲染工单预览图（只读预览，不可当本地文件再传），再渲染本地 `chooseImage` 缩略图与「点击上传」。

`clearLink` / 改车牌清关联：清空工单预览图；`form.rescueTime = formatNow()`；不删 `scenePhotos` 本地数组。

提交：body 仍带 `rescueTime`、`dispatchOrderId`；本地图仍在入库成功后逐张上传。工单图不走前端上传。

## 测试

后端：有签到时间的摘要带 `checkedInAt`；未签到为 `null`；`DAMAGE` 路径进 `damagePhotoPaths`，不含 `PARK`；`checkIn` 带 `dispatchOrderId` 时插入对应条数 `SCENE`；无关联不插入。

移动端：有 `checkedInAt` 才覆盖施救时间；无则保持原值；取消关联恢复当前时刻；预览 URL 由 path 拼出。

## 明确不做

- 不改 PC 扣车表单
- 不复制工单停放照 `PARK`
- 不把扣车图写回工单
- 不新增权限码
