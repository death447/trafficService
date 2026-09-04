# Task 7 Review: 前端排班页 + 车辆绑片区

**Range:** `d62bb45` → `5f92ab8`  
**Commit:** `feat(frontend): add schedule management and vehicle district binding`  
**Sources:** Plan Task 7 (`docs/superpowers/plans/2026-09-04-district-schedule.md`); brief encoding-stale → plan used. Diff via git (review-pkg encoding noisy).  
**Mode:** Read-only (no code/git mutation)

---

## Spec compliance

### Checklist

| Requirement | Verdict | Notes |
|-------------|---------|-------|
| `schedule.js` list/get/create/update/delete | **Pass** | All five exported; paths `/schedule` |
| `ScheduleList.vue` columns 日期/时段/人员/角色/片区/车辆/操作 | **Pass** | |
| Filters `from`/`to` default today | **Pass** | Also roleType + district (extra, fine) |
| `roleType` toggles vehicle; clears on change | **Pass** | Vehicle field only for `TOW_DRIVER`; `onRoleTypeChange` clears user+vehicle |
| TOW requires vehicle | **Pass** | Client check + `required` select; DISPATCHER sends `vehicleId: null` |
| Conflict / business errors shown | **Pass** | `formError` / `error` from `e.message` (axios interceptor rejects `Result` `code!==200` with message) |
| Route `/schedules` + `meta.permissions: ['schedule:manage']` | **Pass** | |
| Nav「排班管理」+ Home card | **Pass** | `v-auth="'schedule:manage'"` |
| VehicleList district column + select | **Pass** | ENABLED list; clear → `null`; DISABLED current as disabled option |
| Style match / no new UI libs | **Pass** | `page`/`panel`/`data-table`/`modal`; package.json unchanged |
| Commit message as planned | **Pass** | Exact plan string |

### Spec Issues

#### Critical (Must Fix)

1. **Dispatcher cannot load personnel for schedule form**
   - File: `ScheduleList.vue` (~317–325) uses `getUserList()` → `GET /api/user/list` with `@PreAuthorize("hasAuthority('user:query')")`
   - DISPATCHER role grants only `permission` id `16` + `20–41` (no `user:query` = id 2)
   - `Promise.all([getUserList(), listVehicles(), listDistricts()])` fails entirely on 403 → empty users/vehicles/districts maps
   - Plan Step 4 acceptance: 「调度员建排班不选车 → 成功」cannot work; person `<select>` stays empty
   - Fix (pick one): grant DISPATCHER `user:query`; or add a schedule-scoped lightweight user lookup under `schedule:*`; or harden lookups so vehicle/district still load and surface a clear auth error for users

#### Important (Should Fix)

1. **Manual联调 checklist not evidenced**
   - Implementer report: build only; Step 4 rows (overlap errors, bind district, 403, nearby) not run
   - After Critical fix, re-run dispatcher/towdriver paths before Task 8

2. **Lookup failure is all-or-nothing**
   - Same `Promise.all` means a single 403/network error blanks vehicle & district labels even when those APIs would succeed
   - Prefer independent try/catch per lookup (or `Promise.allSettled`)

#### Minor (Nice to Have)

1. **`getSchedule` unused in UI** — API surface complete per plan; fine to keep
2. **VehicleList double `listDistricts` call** — ENABLED + all; workable, could filter once client-side
3. **Editing inactive / role-mismatched user** — `filteredUsers` may omit current `userId`; select looks blank (edge case)

### Spec verdict

**FAIL** — Core UI/wiring matches plan, but primary role (调度员) cannot populate the user picker due to missing `user:query`. Not ready to proceed until Critical is fixed.

---

## Code quality

### Strengths

- Clean mirror of VehicleList/DistrictList patterns (`page-header`, filters panel, modal form, `v-auth` button grains)
- Correct DISPATCHER vs TOW_DRIVER payload rules; DISABLED district shown readonly and blocked on save (aligns with backend ENABLED-on-write)
- Error surfacing compatible with `request.js` interceptor (`Error(message)` for business `Result.error`)
- Scoped filter CSS uses existing enterprise tokens; no new dependencies
- Single focused commit; file set matches plan Step 5

### Quality Issues

#### Critical (Must Fix)

1. **Same permission/lookup failure as Spec Critical #1** — functional break for intended operator role; treat as merge blocker

#### Important (Should Fix)

1. **`loadLookups` fragility** (`ScheduleList.vue` ~317–325, `onMounted` ~429–435)
   - One rejected promise leaves form unusable and labels as raw IDs while schedule list may still load — confusing UX
   - Split fetches; keep partial success

2. **No client-side end≤start guard**
   - Backend validates; optional early `formError` would match TOW vehicle pre-check pattern

#### Minor (Nice to Have)

1. Client-side name maps for user/vehicle/district (IDs-only API) — acceptable; fragile if lookup list incomplete
2. `datetime-local` → append `:00` — OK for current API; document if timezone quirks appear
3. Filter district dropdown includes DISABLED districts — harmless; could restrict to ENABLED for consistency with form

### Quality verdict

**FAIL** — Implementation quality is generally solid and stylistically consistent, but the dispatcher user-list auth gap (and cascading `Promise.all` failure) is a Critical defect for production/acceptance use.

---

## Recommendations

1. Fix dispatcher access to schedule personnel (permission or dedicated endpoint) **before** Task 8 regression.
2. Decouple lookup requests; show distinct errors.
3. Smoke: dispatcher create without vehicle; towdriver without vehicle → message; overlap → message; vehicle bind ENABLED district; user without `schedule:manage` → no nav / `/schedules` → 403.

## Assessment

| Lens | Ready? |
|------|--------|
| Spec | **No** |
| Quality | **No** (with Critical fix → re-review) |

**Reasoning:** Planned files, commit message, schedule CRUD UI, vehicle district binding, and conflict error plumbing are present and well-aligned with VehicleList. The schedule form’s dependency on `user:query` breaks the dispatcher acceptance path and must be fixed first.

---

## Follow-up fix (2026-09-04): DISPATCHER user:query

**Commit:** `fix: grant dispatcher user:query for schedule assignee picker`

### Changes

1. `database/init.sql` — DISPATCHER `role_permission` SELECT now includes `id = 2` (`user:query`) in addition to dispatch/vehicle/district/schedule perms. Still **no** `user:manage` (id=1) menu/module for DISPATCHER.
2. `database/migrate_2026-09-04_district_schedule.sql` — idempotent INSERT for `role_id=2, permission_id=2` when missing (existing DBs).
3. `ScheduleList.vue` — `loadLookups` uses `Promise.allSettled` so a single 403/network failure no longer blanks vehicles/districts; partial failures surface a message; users still load via `getUserList()` once perm is granted.

### ADMIN verification

ADMIN already receives `user:query` via `permission` ids **1-15** (`SELECT 5, id FROM permission WHERE id BETWEEN 1 AND 15 OR ...`). No change required.

### Intent note

DISPATCHER may call `user:query` **only** to populate the schedule duty-assignee picker. They do not get `user:manage` and therefore do not see the user-management menu.

### Build

`npm run build` (frontend) succeeded after this fix (vite production build, exit 0).
