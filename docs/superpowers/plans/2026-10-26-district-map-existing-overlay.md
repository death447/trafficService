# 片区编辑地图已有围栏叠加 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 片区新增/编辑弹窗地图上叠加其它启用片区围栏与名称，作视觉参考，避免新建时无意重叠。

**Architecture:** 扩展 `createPolygonEditor` 增加参考图层 API；`DistrictList` 打开弹窗时拉取 `ENABLED` 列表（排除当前编辑 id）并 `setReferenceDistricts`。保存不校验相交。

**Tech Stack:** Vue 3、高德 Maps JS API 2.0、现有 `listDistricts` API

**Spec:** `docs/superpowers/specs/2026-10-26-district-map-existing-overlay-design.md`

## Global Constraints

- 仅弹窗地图；列表页不加总览
- 仅 `ENABLED`；编辑排除 `editingId`
- 参考层不可编辑、不抢 click；编辑层样式不变
- 无 Key / 地图失败：仍用 JSON，不画参考层
- 不校验几何重叠；后端不改
- 启用片区请求：`page=1, size=100`；超出 100 本期只显示第一页
- 每任务结束提交

## File Structure

### Frontend
- Modify: `frontend/src/utils/amap.js` — `createPolygonEditor` 参考层
- Modify: `frontend/src/views/district/DistrictList.vue` — 拉取并传入参考数据

---

### Task 1: `createPolygonEditor` 参考图层

**Files:**
- Modify: `frontend/src/utils/amap.js`（`createPolygonEditor`）

**Interfaces:**
- Produces:
  - `setReferenceDistricts(items: Array<{ id?, name, path: {lng,lat}[] }>): void`
  - `clearReferenceDistricts(): void`（或 destroy 内自动清理）
- Consumes: 现有 `AMap.Map` / `AMap.Polygon`；可选 `AMap.Text`

- [ ] **Step 1: 扩展 createPolygonEditor**

内部维护 `refPolygons = []`、`refTexts = []`。

`setReferenceDistricts(items)`：
1. 先 `clearReferenceDistricts`
2. 对每项：`path.length >= 3` 才创建
3. `AMap.Polygon`：`strokeColor`/`fillColor` 灰色弱对比（如 stroke `#888`，fill `#999`，fillOpacity `0.12`，strokeWeight `1`）；`zIndex` 低于编辑多边形；`bubble: false`
4. 名称：多边形中心（顶点均值即可）`new AMap.Text({ text: name, position, style: { ... 小号深灰 } })`
5. 新建且当前编辑 path 为空、且有参考时：`map.setFitView(refPolygons)`（可选；有编辑 path 时仍 fit 编辑多边形，与现逻辑一致）

`clearReferenceDistricts`：`map.remove` 全部 ref 对象并清空数组。

`destroy`：先 clear 参考层，再销毁地图。

编辑多边形创建时确保 `zIndex` 高于参考（如 `20` vs `10`）。

- [ ] **Step 2: 浏览器/自测约定**

无自动化单测（AMap 依赖 DOM）；实现后由 Task 2 人工冒烟。

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: polygon editor reference district overlays"
```

---

### Task 2: DistrictList 打开弹窗加载启用片区

**Files:**
- Modify: `frontend/src/views/district/DistrictList.vue`

**Interfaces:**
- Consumes: `listDistricts`、`createPolygonEditor` 的 `setReferenceDistricts`、现有 `parseFenceJson`

- [ ] **Step 1: 加载参考数据**

新增 `async function loadReferenceDistricts()`：
```js
const res = await listDistricts({ status: 'ENABLED', page: 1, size: 100 })
const list = res.data?.list || []
return list
  .filter((d) => d.id !== editingId.value)
  .map((d) => ({
    id: d.id,
    name: d.name || d.code || String(d.id),
    path: parseFenceJson(d.fenceJson)
  }))
  .filter((d) => d.path.length >= 3)
```

请求失败：静默或弱提示，不挡编辑（`console`/`formError` 非阻塞均可；推荐不写死 formError，避免挡住保存）。

- [ ] **Step 2: 接入 openCreate / openEdit / initEditor**

在 `initEditor` 成功创建 editor 后：
```js
const refs = await loadReferenceDistricts()
polygonEditor.setReferenceDistricts?.(refs)
```

`destroyEditor` 已 destroy 地图即可；无需额外 clear。

无 Key / `amapReady === false`：不调用 loadReference。

- [ ] **Step 3: 冒烟**

1. ≥2 个启用片区 → 新增：地图见其它围栏+名称；点击只改当前
2. 编辑片区 A：不见 A 的灰色副本（蓝编辑区仍在）
3. 停用片区不出现
4. 关弹窗再开无残留
5. 无 Key：JSON 仍可保存

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: show existing enabled districts on fence editor map"
```

---

## Self-Review (plan vs spec)

| Spec | Task |
|------|------|
| 弹窗叠加启用片区 | Task 2 |
| 编辑排除自身 | Task 2 filter |
| 参考弱样式 / 不抢点击 | Task 1 |
| 不校验重叠 | Global |
| 无 Key 不画参考 | Task 2 |
| size=100 | Task 2 |
