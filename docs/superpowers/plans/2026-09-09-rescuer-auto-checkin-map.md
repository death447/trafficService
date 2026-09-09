# 施救员自动签到与地图 500m 圈 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 任务详情用派单车辆新鲜坐标相对事故点 ≤500m 自动 AUTO 签到；10s 轮询更新车位与地图；事故点绘制 500m 圆；去掉手机签到按钮，保留手动签到。

**Architecture:** 纯前端：`geo.js` 提供距离/新鲜度/`canAutoCheckin`；`detail.vue` 在 `load` 后与 10s 轮询中调用 `tryAutoCheckin`（现有 checkin API）；地图创建时加 `AMap.Circle(radius=500)`，轮询继续 `syncVehicleMarker`。后端不改。

**Tech Stack:** uni-app H5、Vue 3、高德 JS API 2.0、Node.js `node --test`（纯函数单测）

**Spec:** `docs/superpowers/specs/2026-09-09-rescuer-auto-checkin-map-design.md`

## Global Constraints

- `LOCATION_STALE_SECONDS = 180`；自动签到距离阈值 **500** 米
- 位置源：详情 `assignedVehicle`（工单 `vehicleId`），非手机当次 GPS
- 轮询间隔 **10000** ms（由 20000 改为 10000）
- 不满足自动条件时**静默**（不 toast 失败）；AUTO 成功 toast「签到成功」
- 去掉「签到」+ `uni.getLocation`；保留「手动签到（需填写原因）」
- 后端 checkin / 状态机**不改**
- 无轨迹 / WS / 新自动签到接口

## File Structure

### Mobile
- Create: `mobile-rescuer/src/utils/geo.js`
- Create: `mobile-rescuer/src/utils/geo.test.mjs`
- Modify: `mobile-rescuer/src/pages/task/detail.vue`

---

### Task 1: 地理工具 `geo.js`（距离 / 新鲜度 / 可否自动签到）

**Files:**
- Create: `mobile-rescuer/src/utils/geo.js`
- Create: `mobile-rescuer/src/utils/geo.test.mjs`

**Interfaces:**
- Consumes: 无
- Produces:
  - `export const LOCATION_STALE_SECONDS = 180`
  - `export const AUTO_CHECKIN_RADIUS_METERS = 500`
  - `export function distanceMeters(lng1, lat1, lng2, lat2): number` — 非法坐标返回 `NaN`
  - `export function isLocationFresh(locationUpdatedAt, now = Date.now()): boolean`
  - `export function canAutoCheckin(order, assignedVehicle, now = Date.now()): boolean`

- [ ] **Step 1: Write the failing test file**

Create `mobile-rescuer/src/utils/geo.test.mjs`:

```js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  LOCATION_STALE_SECONDS,
  AUTO_CHECKIN_RADIUS_METERS,
  distanceMeters,
  isLocationFresh,
  canAutoCheckin
} from './geo.js'

describe('geo', () => {
  it('exports stale and radius constants', () => {
    assert.equal(LOCATION_STALE_SECONDS, 180)
    assert.equal(AUTO_CHECKIN_RADIUS_METERS, 500)
  })

  it('distanceMeters same point near zero', () => {
    const d = distanceMeters(114.05, 22.54, 114.05, 22.54)
    assert.ok(d < 1)
  })

  it('distanceMeters known ~500m separation', () => {
    // ~0.0045 deg lat ≈ 500m
    const d = distanceMeters(114.05, 22.54, 114.05, 22.54 + 0.0045)
    assert.ok(d > 450 && d < 550)
  })

  it('distanceMeters invalid returns NaN', () => {
    assert.ok(Number.isNaN(distanceMeters(null, 22, 114, 22)))
  })

  it('isLocationFresh within 180s', () => {
    const now = Date.parse('2026-09-09T12:00:00Z')
    assert.equal(isLocationFresh('2026-09-09T11:57:01Z', now), true)
    assert.equal(isLocationFresh('2026-09-09T11:56:59Z', now), false)
    assert.equal(isLocationFresh(null, now), false)
  })

  it('canAutoCheckin true when accepted fresh within 500m', () => {
    const now = Date.parse('2026-09-09T12:00:00Z')
    const order = {
      status: 'ACCEPTED',
      checkedInAt: null,
      longitude: 114.05,
      latitude: 22.54
    }
    const vehicle = {
      longitude: 114.05,
      latitude: 22.54,
      locationUpdatedAt: '2026-09-09T11:59:00Z'
    }
    assert.equal(canAutoCheckin(order, vehicle, now), true)
  })

  it('canAutoCheckin false when stale or too far or wrong status', () => {
    const now = Date.parse('2026-09-09T12:00:00Z')
    const baseOrder = {
      status: 'ACCEPTED',
      checkedInAt: null,
      longitude: 114.05,
      latitude: 22.54
    }
    assert.equal(
      canAutoCheckin(baseOrder, {
        longitude: 114.05,
        latitude: 22.54,
        locationUpdatedAt: '2026-09-09T11:50:00Z'
      }, now),
      false
    )
    assert.equal(
      canAutoCheckin(baseOrder, {
        longitude: 114.05,
        latitude: 22.55,
        locationUpdatedAt: '2026-09-09T11:59:00Z'
      }, now),
      false
    )
    assert.equal(
      canAutoCheckin({ ...baseOrder, status: 'DISPATCHED' }, {
        longitude: 114.05,
        latitude: 22.54,
        locationUpdatedAt: '2026-09-09T11:59:00Z'
      }, now),
      false
    )
    assert.equal(
      canAutoCheckin({ ...baseOrder, checkedInAt: '2026-09-09T11:58:00Z' }, {
        longitude: 114.05,
        latitude: 22.54,
        locationUpdatedAt: '2026-09-09T11:59:00Z'
      }, now),
      false
    )
  })
})
```

- [ ] **Step 2: Run tests — expect FAIL (module missing)**

Run: `cd mobile-rescuer && node --test src/utils/geo.test.mjs`

Expected: FAIL — cannot find module `./geo.js` (or exports missing)

- [ ] **Step 3: Implement `geo.js`**

Create `mobile-rescuer/src/utils/geo.js`:

```js
export const LOCATION_STALE_SECONDS = 180
export const AUTO_CHECKIN_RADIUS_METERS = 500

function toNum(v) {
  if (v == null || v === '') return NaN
  const n = Number(v)
  return Number.isFinite(n) ? n : NaN
}

/** Haversine distance in meters; NaN if any coordinate invalid */
export function distanceMeters(lng1, lat1, lng2, lat2) {
  const a = toNum(lng1)
  const b = toNum(lat1)
  const c = toNum(lng2)
  const d = toNum(lat2)
  if (![a, b, c, d].every(Number.isFinite)) return NaN
  const toRad = (x) => (x * Math.PI) / 180
  const dLat = toRad(d - b)
  const dLng = toRad(c - a)
  const lat1r = toRad(b)
  const lat2r = toRad(d)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1r) * Math.cos(lat2r) * Math.sin(dLng / 2) ** 2
  return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

export function isLocationFresh(locationUpdatedAt, now = Date.now()) {
  if (locationUpdatedAt == null || locationUpdatedAt === '') return false
  const t = Date.parse(String(locationUpdatedAt).replace(' ', 'T'))
  if (!Number.isFinite(t)) return false
  return now - t <= LOCATION_STALE_SECONDS * 1000
}

export function canAutoCheckin(order, assignedVehicle, now = Date.now()) {
  if (!order || !assignedVehicle) return false
  if (order.status !== 'ACCEPTED') return false
  if (order.checkedInAt) return false
  if (!isLocationFresh(assignedVehicle.locationUpdatedAt, now)) return false
  const meters = distanceMeters(
    order.longitude,
    order.latitude,
    assignedVehicle.longitude,
    assignedVehicle.latitude
  )
  if (!Number.isFinite(meters)) return false
  return meters <= AUTO_CHECKIN_RADIUS_METERS
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `cd mobile-rescuer && node --test src/utils/geo.test.mjs`

Expected: all tests pass

- [ ] **Step 5: Commit** (only if user asked to commit; otherwise skip and note in handoff)

```bash
git add mobile-rescuer/src/utils/geo.js mobile-rescuer/src/utils/geo.test.mjs
git commit -m "feat(mobile): add geo helpers for auto check-in"
```

---

### Task 2: 详情页自动签到 + 10s 轮询 + UI

**Files:**
- Modify: `mobile-rescuer/src/pages/task/detail.vue`

**Interfaces:**
- Consumes: `canAutoCheckin` from `../../utils/geo`；`checkinTask` from api
- Produces: `tryAutoCheckin()`；poll interval `10000`；无手机签到按钮

- [ ] **Step 1: Update ACCEPTED actions template**

Replace the ACCEPTED actions block so that:

- Remove: `<view v-if="!order.checkedInAt" class="btn-primary" @click="onCheckin">签到</view>`
- Change manual button to:  
  `<view v-if="!order.checkedInAt" class="btn-ghost" @click="promptManual">手动签到（需填写原因）</view>`
- Keep: 现场采集 / 入库登记 / 完成工单 / 未签到可退单

- [ ] **Step 2: Import geo + add auto-checkin state/helpers**

In `<script setup>`:

```js
import { canAutoCheckin } from '../../utils/geo'
```

Add module-level flags near other lets:

```js
let autoCheckinInFlight = false
let autoCheckinDone = false
```

Add function (place near other action handlers):

```js
async function tryAutoCheckin() {
  if (autoCheckinDone || autoCheckinInFlight) return
  const o = order.value
  const v = assignedVehicle.value
  if (!canAutoCheckin(o, v)) return
  autoCheckinInFlight = true
  try {
    await checkinTask(id.value, {
      lng: Number(v.longitude),
      lat: Number(v.latitude),
      mode: 'AUTO'
    })
    autoCheckinDone = true
    uni.showToast({ title: '签到成功', icon: 'success' })
    await load({ silent: true })
  } catch (_) {
    // silent per spec — distance/API errors already toasted by request.js if any;
    // avoid duplicate noise: prefer showError:false if checkinTask supports it
  } finally {
    autoCheckinInFlight = false
  }
}
```

If `checkinTask` / `request` supports `showError: false`, pass it so failed AUTO during poll does not toast every 10s. Inspect `mobile-rescuer/src/utils/request.js` and `api/rescuer.js` — if supported:

```js
await checkinTask(id.value, { lng: Number(v.longitude), lat: Number(v.latitude), mode: 'AUTO' }, { showError: false })
```

or extend `checkinTask` to accept options like other APIs (`getVehicle` already has `showError`). Match existing pattern in `rescuer.js`.

- [ ] **Step 3: Call tryAutoCheckin after successful load; reset done on new order**

In `load`, after assigning `order` / `assignedVehicle` and map sync, call:

```js
await tryAutoCheckin()
```

In `onLoad`, when `id` is set, reset:

```js
autoCheckinDone = false
```

If `id` changes onLoad, always reset `autoCheckinDone = false`.

- [ ] **Step 4: Change poll interval to 10s**

In `startPoll`, change `20000` → `10000`.

- [ ] **Step 5: Remove `onCheckin` function**

Delete the entire `onCheckin` that calls `uni.getLocation` + AUTO checkin. Keep `promptManual` / `submitReason` MANUAL path.

- [ ] **Step 6: Smoke-check build**

Run: `cd mobile-rescuer && npm run build:h5`

Expected: `DONE  Build complete.`

Manual (if H5 running): ACCEPTED 未签到详情 — 无「签到」按钮；有「手动签到（需填写原因）」；车辆新鲜且近时自动 toast 签到成功。

- [ ] **Step 7: Commit** (if user requested commits)

```bash
git add mobile-rescuer/src/pages/task/detail.vue mobile-rescuer/src/api/rescuer.js
git commit -m "feat(mobile): auto check-in from vehicle location on task detail"
```

---

### Task 3: 事故点 500m 圆 + fitView

**Files:**
- Modify: `mobile-rescuer/src/pages/task/detail.vue`

**Interfaces:**
- Consumes: `window.AMap`；`AUTO_CHECKIN_RADIUS_METERS` from geo (or literal `500`)
- Produces: `accidentCircle` overlay on map; included in `setFitView` when possible

- [ ] **Step 1: Track circle + create on map init**

Add `let accidentCircle = null` next to `vehicleMarker`.

Import: `import { canAutoCheckin, AUTO_CHECKIN_RADIUS_METERS } from '../../utils/geo'`  
(if Task 2 only imported `canAutoCheckin`, extend import).

In `ensureMap`, after creating the accident `Marker`, add:

```js
accidentCircle = new AMap.Circle({
  center: [lng, lat],
  radius: AUTO_CHECKIN_RADIUS_METERS,
  strokeColor: '#2979ff',
  strokeWeight: 2,
  strokeOpacity: 0.8,
  fillColor: '#2979ff',
  fillOpacity: 0.12,
  map: mapInstance,
  bubble: true
})
```

- [ ] **Step 2: Clear circle in destroyMap**

```js
if (accidentCircle) {
  accidentCircle.setMap(null)
  accidentCircle = null
}
```

(before destroying `mapInstance`)

- [ ] **Step 3: Update syncVehicleMarker fitView to include circle**

When calling `setFitView`, pass overlays that exist, e.g.:

```js
const overlays = []
const accidentMarker = new AMap.Marker({ position: accident }) // or keep a persistent accidentMarker ref
if (accidentCircle) overlays.push(accidentCircle)
if (vehicleMarker) overlays.push(vehicleMarker)
// Prefer storing accidentMarker on create like vehicleMarker instead of ephemeral Marker-only-for-fit
if (overlays.length) {
  mapInstance.setFitView(overlays, false, [40, 40, 40, 40])
}
```

Prefer: keep `let accidentMarker = null` created once in `ensureMap` (same as today inline Marker), assign to `accidentMarker`, clear in `destroyMap`, and `setFitView([accidentMarker, accidentCircle, vehicleMarker].filter(Boolean), ...)`.

- [ ] **Step 4: Build verify**

Run: `cd mobile-rescuer && npm run build:h5`

Expected: success. Manual: 有事故坐标的详情地图可见蓝色半透明 500m 圆；10s 后车标位置更新。

- [ ] **Step 5: Commit** (if user requested commits)

```bash
git add mobile-rescuer/src/pages/task/detail.vue
git commit -m "feat(mobile): draw 500m check-in radius on task detail map"
```

---

## Spec coverage (self-review)

| Spec 项 | Task |
|---------|------|
| 车辆新鲜坐标 AUTO 签到 | Task 1 + 2 |
| 180s / 500m | Task 1 |
| 10s 轮询并可再试签到 | Task 2 |
| 签成后不再试 | Task 2 `autoCheckinDone` |
| 地图车标同步 | 已有 + Task 2 轮询 |
| 500m 圆 | Task 3 |
| 去掉手机签到 / 保留手动 | Task 2 |
| 后端不改 | 全计划无 backend 文件 |

无 TBD/占位符；`canAutoCheckin` / `AUTO_CHECKIN_RADIUS_METERS` 命名在 Task 1–3 一致。
