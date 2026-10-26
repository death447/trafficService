# 片区编辑地图：已有启用片区叠加参考

## 概述

在 PC「片区管理」新增/编辑弹窗的高德地图上，叠加展示**其它已启用片区**的围栏，作为画新围栏时的视觉参考，降低与已有片区重叠的概率。本期**不**做几何相交校验，也**不**改列表页。

## 背景与范围

### 已有基础
- `DistrictList.vue` 弹窗使用 `createPolygonEditor` 点击追加顶点
- 片区 `fenceJson` 为 `[{lng,lat},…]`；列表接口支持 `status` 筛选，分页 size ∈ {10,20,50,100}
- 无 `VITE_AMAP_KEY` 时走 JSON textarea，无地图

### 决策（已确认）
| 项 | 选择 |
|----|------|
| 展示场景 | 仅新增/编辑弹窗地图 |
| 叠加对象 | 仅 `ENABLED`；编辑时排除当前 `editingId` |
| 重叠 | 仅视觉参考，保存不校验相交 |

### 本轮目标
1. 打开弹窗且地图可用时，加载启用片区并绘制参考多边形 + 名称
2. 当前编辑围栏样式保持现有蓝色高亮；参考区弱对比、不可编辑、不抢点击
3. 地图销毁时一并清理参考图层

### 明确不做
- 列表页总览图
- 停用片区叠加
- 保存时重叠/相交硬校验或警告
- 后端 GeoJSON / 空间库
- 无 Key 时的 JSON 编辑器里画参考图

## 技术方案

### 总体：扩展 `createPolygonEditor`（方案 1）

打开弹窗后：
1. `listDistricts({ status: 'ENABLED', page: 1, size: 100 })`
2. 解析各条 `fenceJson`（≥3 点才画）；过滤 `id === editingId`
3. 调用编辑器 `setReferenceDistricts(items)` 或构造时传入 `referenceDistricts`

若启用片区超过 100：本期仅展示第一页（产品可接受；片区数量通常远小于此）。不新增专用 API。

### 地图样式与交互

| 图层 | 样式 | 交互 |
|------|------|------|
| 参考多边形 | 灰/弱色描边与低透明度填充（与编辑蓝区分） | `bubble: false` 或不监听 click；不可拖拽顶点 |
| 参考名称 | `AMap.Text` 或 Marker 标在多边形大致中心，文案为 `name` | 不拦截地图点击 |
| 当前编辑多边形 | 现有 `#1f6fa8` / `#3aa0df` | 点击地图仍只追加编辑顶点 |

`setFitView`：有编辑 path 时仍 fit 编辑多边形；新建空 path 时可对参考多边形整体 `setFitView`（有参考时），便于看到已有范围。

### 数据流

```
openCreate / openEdit
  → nextTick + initEditor(path)
  → fetch ENABLED districts
  → filter exclude editingId
  → polygonEditor.setReferenceDistricts([...])
closeForm / destroy
  → remove reference polygons + texts
  → destroy map
```

无 Key 或地图加载失败：行为与现网一致（JSON），不请求/不画参考层亦可（可跳过 fetch）。

### 文件改动（预期）

| 文件 | 变更 |
|------|------|
| `frontend/src/utils/amap.js` | `createPolygonEditor` 支持 reference 层 set/clear |
| `frontend/src/views/district/DistrictList.vue` | 打开弹窗拉启用列表并传入；编辑排除自身 |

后端无强制改动。

## 错误与边界

- `fenceJson` 非法或不足 3 点：跳过该片区，不影响编辑
- 参考层绘制失败：不影响主编辑多边形
- 列表请求失败：提示可选（弱提示或不挡编辑）；主流程仍可画围栏

## 测试要点

- 已有 ≥2 个启用片区时打开「新增」：地图可见其它围栏与名称，点击只改当前围栏
- 编辑片区 A：只见其它启用区，不见 A 的参考副本（编辑 path 仍显示）
- 停用片区不出现在参考层
- 关闭弹窗再开：无残留多边形
- 无 Key：仍可用 JSON 保存

## 非目标回顾

不做列表总览、重叠校验、停用标注、后端空间能力。
