# Task 3 Report: 移动端任务详情地图

**Status:** DONE_WITH_CONCERNS  
**Commit:** `0fa28b9` — `feat(mobile): show accident and assigned vehicle map on task detail`

## Summary

Added AMap to mobile-rescuer H5 task detail: accident marker + assigned yellow truck from `getTask.assignedVehicle`, ~20s reload while `vehicleId` or vehicle coords exist, cleanup on `onHide`/`onUnload`. Ported `hasAmapKey` / `loadAmap` / `createVehicleMapIcon` only (no picker/district factories). Documented Key vars in `.env.example`; did not commit `mobile-rescuer/.env`.

## Changes

| File | Change |
|------|--------|
| `mobile-rescuer/src/utils/amap.js` | Created — KEY load + yellow truck icon (from admin `frontend/src/utils/amap.js`) |
| `mobile-rescuer/.env.example` | Commented `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE` notes |
| `mobile-rescuer/src/pages/task/detail.vue` | Map card after order card; poll + marker sync |

### Template

- Map card 「位置」 after first order card, before 现场摘要
- `canShowMap` → `#task-detail-map`; else placeholders for missing accident coords / missing Key
- `mapHint` / `mapError` lines

### Script

- Consumes `res.data.assignedVehicle` + `order.longitude` / `order.latitude`
- `ensureMap` / `syncVehicleMarker` / `destroyMap`
- `startPoll` 20s → `load()` only from `onShow` (not inside `load`)
- `onHide` → `stopPoll`; `onUnload` → `stopPoll` + `destroyMap`

### Style

- `.map-box` 360rpx, `.map-placeholder`, `.error` per brief

## Verification

| Check | Method | Result |
|-------|--------|--------|
| amap.js exports hasAmapKey/loadAmap/createVehicleMapIcon | Code review | Pass — no picker/district |
| Map after first card; Chinese placeholders | Code review | Pass |
| Poll only when vehicleId or vehicle coords | Code review | Pass |
| load() does not startPoll | Code review | Pass |
| onHide/onUnload cleanup | Code review | Pass |
| `.env` with secrets not committed | `git status` | Pass — `mobile-rescuer/.env` still untracked |
| `npm run build:h5` | uni build | **SUCCESS** (exit 0) |
| Live map / 20s GPS update | Browser + Key | **Not run** |

```bash
cd mobile-rescuer && npm run build:h5
# DONE  Build complete.
```

## Self-Review

- Matches brief Steps 1–5 and Step 7 commit (files + message).
- Uses Task 1 `assignedVehicle` snapshot; no admin frontend edits; no `vehicle:query`.
- Omitted unused `getCurrentInstance` from brief snippet (not referenced).
- Did not wrap with `#ifdef H5` (brief optional); `document.getElementById` is H5-oriented — primary target.

## Concerns

1. **No live UI smoke** — markers, Key placeholder, and 20s poll not exercised in browser.
2. **H5-only DOM** — non-H5 builds may hit `document`/`window` if this page runs there; optional `#ifdef H5` not added.
3. **`.env` not gitignored** — repo ignores `.env.local` / `.env.*.local` only; local `mobile-rescuer/.env` remains untracked but easy to add by mistake.

## Commit

```
0fa28b9 feat(mobile): show accident and assigned vehicle map on task detail
```

---

## Final review Important fixes (whole-branch)

**Status:** FIXED  
**Scope:** Three Important findings only (no Minor drive-bys beyond adjacent map remount guard).

### Fixes

1. **Mobile poll restart after onHide** (`mobile-rescuer/src/pages/task/detail.vue`)
   - Added `pageVisible` flag (`true` in `onShow`, `false` in `onHide`/`onUnload`).
   - `onShow` → `load().then(() => { if (pageVisible) startPoll() })` so an in-flight getTask cannot start a timer on a hidden page.

2. **Failed poll wipes page / map** (`mobile-rescuer/src/pages/task/detail.vue`)
   - `load({ silent })`: poll uses `silent: true`.
   - On error with existing `order` (or silent): keep last good order/map; set soft `pollHint` (“位置刷新失败，显示上次数据”).
   - On true initial failure (`!order`): `destroyMap()` and clear order/fieldRecord/assignedVehicle.
   - `ensureMap`: if AMap container remounted vs stale `mapInstance`, destroy and recreate.

3. **Admin “无事故坐标” unreachable with Key** (`frontend/src/views/dispatch/DispatchDetail.vue`)
   - Track panel `v-if` simplified to `order.status !== 'PENDING'` so placeholder copy shows when Key exists but coords are missing.

### Verification

| Check | Method | Result |
|-------|--------|--------|
| pageVisible gates startPoll | Code review | Pass |
| silent poll keeps last good order | Code review | Pass |
| Track panel shows without coords when Key set | Code review | Pass |
| `mobile-rescuer` `npm run build:h5` | uni build | **SUCCESS** (exit 0, DONE Build complete) |
| `frontend` `npm run build` | vite build | **SUCCESS** (exit 0, built in 1.55s) |
| `.env` secrets not committed | commit staging | Pass — only source + report |

```bash
cd mobile-rescuer && npm run build:h5   # DONE  Build complete.
cd frontend && npm run build            # ✓ built in 1.55s
```

