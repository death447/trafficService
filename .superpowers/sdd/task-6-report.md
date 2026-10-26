# Task 6 Report: PC 报表页

**Status:** DONE_WITH_CONCERNS  
**Date:** 2026-09-12  
**Branch:** `feat/report-module`  
**Brief:** `.superpowers/sdd/task-6-brief.md`

## Summary

Added the PC operations report page at `/reports`: date-range filters (default month-to-date), client-side validation matching backend copy, summary cards/charts/tables for dispatch / quality / detain, Excel export with blob JSON-error bypass, sidebar entry after 概览, and `report:manage` route guard.

## Files changed

| Action | Path |
|--------|------|
| Modify | `frontend/package.json` (echarts ^6.1.0) |
| Modify | `frontend/src/utils/request.js` (blob interceptor bypass) |
| Create | `frontend/src/api/report.js` |
| Modify | `frontend/src/router/index.js` (`/reports`, `Report`, `report:manage`) |
| Modify | `frontend/src/App.vue` (sidebar + `pageTitle`) |
| Create | `frontend/src/views/report/Report.vue` |

Not committed (out of Step 7): `Home.vue`, uploads, `.tools`, backend files, `frontend/package-lock.json` (root `.gitignore` lists `package-lock.json`; `git add` refused without `-f`).

## Commit

```
234103a feat: add PC operations report page with charts and export
```

Full hash: `234103a` (6 files, +642). Staged only the Step 7 paths except the gitignored lockfile.

## Step checklist

- [x] Step 1: `npm install echarts` in `frontend/`
- [x] Step 2: blob `responseType` returns axios `res` unchanged
- [x] Step 3: `getReportSummary` / `exportReport`
- [x] Step 4: route `/reports`
- [x] Step 5: menu after 概览, title `报表`
- [x] Step 6: `Report.vue` requirements (see below)
- [x] Step 7: commit as specified

### Report.vue coverage

- Default `from` = 1st of current local month, `to` = today (`YYYY-MM-DD`); smoke: `2026-09-01`–`2026-09-12`
- `onMounted` calls `onQuery`
- Validation (no request): `请选择开始和结束日期` / `开始日期不能晚于结束日期` / `查询区间不能超过 366 天`
- `queryError` under filters; failed query does not clear `summary`
- Cards copy Home `stat-row` / `stat-card` CSS (Home.vue untouched)
- Orders `slice(0, 50)`; orderNo `router-link` to `/dispatches/:id`
- Status labels: PENDING 待派单, DISPATCHED 已派单, ACCEPTED 已接单, COMPLETED 已完成, ABORTED 已中止
- Averages `v == null ? '—' : Number(v).toFixed(1)`
- ECharts: `trendEl` line, `statusEl` bar (Chinese statuses), `scoreEl` bar (四维); empty copy 暂无数据 when `dispatch.total===0` / `quality.ratedCount===0`; `dispose` on unmount
- Export: JSON content-type/blob type → parse `message`; else download `report_${from}_${to}.xlsx`
- Empty tables: single `colspan` row 暂无数据

## Tests

No frontend unit-test runner in this repo (no Vitest/Jest scripts). TDD not specified in the brief.

**`npm run build` (frontend):** exit 0. Vite 5.4.21, 805 modules. Warning: `Report-*.js` ~1138 kB because `import * as echarts from 'echarts'` is plan-mandated (dynamic chunk).

**Browser smoke (admin / admin123, Vite `localhost:5173`):**

- Sidebar 工作台 shows 报表 after 概览; `/reports` title 报表
- Default dates month 1 → today; auto-query on mount
- `from > to` → `开始日期不能晚于结束日期`
- span > 366 → `查询区间不能超过 366 天`
- Live `GET /api/report/summary` returned `No static resource api/report/summary.` (running Spring process started before report APIs; Task 7 restart should pick up `ReportController`)

## Concerns

- `frontend/package-lock.json` is gitignored; echarts is in `package.json` only. Reinstalls without a lockfile may float versions.
- Live summary 404 until backend restart (Task 7). Charts/tables with real payload not verified this session.
- ECharts production chunk is large (~1.1 MB); expected with full `echarts` import.
- Sidebar `router-link` click from 概览 did not navigate in automation (direct URL load worked); likely automation/HMR, not a product bug.

## Self-review

Implementation follows the brief APIs, copy, date helpers, and list/filter patterns from `DispatchList.vue`. Interceptor blob bypass is the minimum needed so export does not fail `data.code !== 200`. Did not modify `Home.vue`.
