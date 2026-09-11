# Mobile Parking Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep a single `mobile-rescuer` H5 app; route accounts into a rescuer or parking workspace; land parking check-in / in-yard list / check-out / hangtag scan on existing `/api/detain` and `/api/parking`.

**Architecture:** Persist `workspace` (`rescuer` | `parking`) after login. Native tabBar stays two items; first tab becomes `pages/workbench/index` and renders task list or in-yard list. Backend adds authenticated `GET/PUT /api/auth/me` and returns the created detain row from `POST /api/detain`. No `/api/mobile/parking` slice.

**Tech Stack:** Spring Boot 3.2, JUnit5 + Mockito, uni-app Vue3 H5, existing JWT RBAC

**Spec:** `docs/superpowers/specs/2026-09-10-mobile-parking-workspace-design.md`

## Global Constraints

- One app: `mobile-rescuer/` only; do not create a second mobile project
- `hasRescuer = mobile:rescuer` or any `rescuer:*`; `hasParking = detain:query`
- Dual-permission users must pick a workspace; switch requires logout (no switch control on 我的)
- Single-permission users skip the picker
- Parking workspace: no GPS reporter, no bind-vehicle UI
- Reuse `/api/detain` and `/api/parking`; do not add `mobile:parking` permission codes
- Check-in fields match PC `DetainInRequest`; detain number is server-generated
- Hangtag QR payload is the raw `detainNo` (example `DV202609070001`)
- `pages/mine/scan` is a shared camera page (both workspaces may open it via `scanQrCode`); bind page stays rescuer-only
- Do not implement parking CRUD, detain edit/clear/hangtag print, or write `detained_vehicle` from rescuer 入库登记
- PC Vue app: no required changes (`checkInDetain` already ignores `data`)
- YAGNI: no traffic-police workspace

## File Structure

### Backend
- Create: `backend/src/main/java/com/example/backend/dto/MeResponse.java`
- Modify: `backend/src/main/java/com/example/backend/service/AuthService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/AuthController.java`
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java` (`checkIn` returns entity)
- Modify: `backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java`
- Test: `backend/src/test/java/com/example/backend/service/AuthServiceTest.java`
- Test: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`
- Reuse DTO: `RescuerProfileUpdateRequest` for PUT `/api/auth/me` body (same three fields)

### Mobile
- Create: `mobile-rescuer/src/utils/workspace.js` (CJS + named exports via `module.exports`)
- Create: `mobile-rescuer/src/utils/workspace.test.cjs`
- Create: `mobile-rescuer/src/utils/guard.js`
- Create: `mobile-rescuer/src/api/auth.js` (`me` get/put)
- Create: `mobile-rescuer/src/api/detain.js`, `mobile-rescuer/src/api/parking.js`
- Create: `mobile-rescuer/src/components/TaskList.vue`, `mobile-rescuer/src/components/DetainList.vue`
- Create: `mobile-rescuer/src/pages/workbench/index.vue`
- Create: `mobile-rescuer/src/pages/workspace/select.vue`
- Create: `mobile-rescuer/src/pages/detain/detail.vue`, `checkin.vue`, `scan.vue`
- Modify: `pages.json`, `App.vue`, `stores/user.js`, `utils/locationReporter.js`, `utils/request.js`, `utils/scanCode.js`
- Modify: `pages/login/index.vue`, `pages/mine/index.vue`, `pages/mine/edit.vue`
- Modify: rescuer pages (`task/detail|scene|park`, `mine/bind`) to call `requirePageAccess('rescuer')`
- Modify: `pages/task/list.vue` into a redirect-only page **or** remove it from `pages.json` after workbench exists (prefer remove from tabBar; keep file as unused redirect `switchTab` to workbench if still registered)

---

### Task 1: `GET/PUT /api/auth/me`

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/MeResponse.java`
- Modify: `backend/src/main/java/com/example/backend/service/AuthService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/AuthController.java`
- Test: `backend/src/test/java/com/example/backend/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `UserMapper.findById` / `update`; `RescuerProfileUpdateRequest` (`realName`, `phone`, `email`)
- Produces: `AuthService.getMe(Long userId) -> MeResponse`; `AuthService.updateMe(Long userId, RescuerProfileUpdateRequest req) -> MeResponse`; `GET/PUT /api/auth/me` authenticated (already covered by `SecurityConfig` — `/api/auth/me` is **not** in `permitAll`)

- [ ] **Step 1: Write failing AuthService tests**

Add to `AuthServiceTest.java` (keep existing login test). Injected `UserMapper` is already `@Mock`.

```java
@Test
void getMeReturnsPublicFieldsWithoutPassword() {
    User user = new User();
    user.setId(4L);
    user.setUsername("parkingadmin");
    user.setRealName("停车场演示");
    user.setPhone("13800000004");
    user.setEmail("parking@example.com");
    user.setPassword("secret-hash");
    when(userMapper.findById(4L)).thenReturn(user);

    MeResponse me = authService.getMe(4L);

    assertEquals(4L, me.getId());
    assertEquals("parkingadmin", me.getUsername());
    assertEquals("停车场演示", me.getRealName());
    assertEquals("13800000004", me.getPhone());
    assertEquals("parking@example.com", me.getEmail());
}

@Test
void getMeThrowsWhenMissing() {
    when(userMapper.findById(9L)).thenReturn(null);
    RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.getMe(9L));
    assertTrue(ex.getMessage().contains("不存在"));
}

@Test
void updateMePatchesProfileFields() {
    User user = new User();
    user.setId(4L);
    user.setUsername("parkingadmin");
    user.setPassword("secret-hash");
    when(userMapper.findById(4L)).thenReturn(user);
    when(userMapper.update(any(User.class))).thenReturn(1);

    RescuerProfileUpdateRequest req = new RescuerProfileUpdateRequest();
    req.setRealName("新名");
    req.setPhone("13900000000");
    req.setEmail("n@example.com");

    MeResponse me = authService.updateMe(4L, req);

    assertEquals("新名", user.getRealName());
    assertEquals("13900000000", user.getPhone());
    assertEquals("n@example.com", user.getEmail());
    verify(userMapper).update(user);
    assertEquals("新名", me.getRealName());
}
```

Add imports: `MeResponse`, `User`, `RescuerProfileUpdateRequest`, `assertThrows`, `assertTrue`, `any`.

- [ ] **Step 2: Run tests and confirm they fail**

Run from repo root:

```bash
mvn -f backend/pom.xml test -Dtest=AuthServiceTest
```

Expected: compile error (`getMe` / `MeResponse` missing) or test failure.

- [ ] **Step 3: Implement MeResponse, AuthService, AuthController**

`MeResponse.java`:

```java
package com.example.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeResponse {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
}
```

Add to `AuthService`:

```java
import com.example.backend.dto.MeResponse;
import com.example.backend.dto.RescuerProfileUpdateRequest;
import com.example.backend.entity.User;

public MeResponse getMe(Long userId) {
    User user = userMapper.findById(userId);
    if (user == null) {
        throw new RuntimeException("用户不存在");
    }
    return toMe(user);
}

public MeResponse updateMe(Long userId, RescuerProfileUpdateRequest req) {
    User user = userMapper.findById(userId);
    if (user == null) {
        throw new RuntimeException("用户不存在");
    }
    if (req != null) {
        if (req.getRealName() != null) {
            user.setRealName(req.getRealName());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
    }
    userMapper.update(user);
    return toMe(user);
}

private static MeResponse toMe(User user) {
    return MeResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .phone(user.getPhone())
            .email(user.getEmail())
            .build();
}
```

Extend `AuthController` (copy `currentUserId` from `DetainedVehicleController`):

```java
import com.example.backend.dto.MeResponse;
import com.example.backend.dto.RescuerProfileUpdateRequest;
import com.example.backend.security.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;

@GetMapping("/me")
public Result<MeResponse> me() {
    try {
        return Result.success(authService.getMe(currentUserId()));
    } catch (RuntimeException e) {
        return Result.error(e.getMessage());
    }
}

@PutMapping("/me")
public Result<MeResponse> updateMe(@RequestBody RescuerProfileUpdateRequest request) {
    try {
        return Result.success(authService.updateMe(currentUserId(), request));
    } catch (RuntimeException e) {
        return Result.error(e.getMessage());
    }
}

private Long currentUserId() {
    CustomUserDetails principal =
            (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return principal.getId();
}
```

Do **not** add `/api/auth/me` to `permitAll`.

- [ ] **Step 4: Re-run tests**

```bash
mvn -f backend/pom.xml test -Dtest=AuthServiceTest
```

Expected: PASS (existing login test still green).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/MeResponse.java \
  backend/src/main/java/com/example/backend/service/AuthService.java \
  backend/src/main/java/com/example/backend/controller/AuthController.java \
  backend/src/test/java/com/example/backend/service/AuthServiceTest.java
git commit -m "feat(auth): add current-user profile GET/PUT /api/auth/me"
```

---

### Task 2: `POST /api/detain` returns created row

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java`
- Test: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

**Interfaces:**
- Consumes: existing `checkIn` validation; `insert` with `useGeneratedKeys`
- Produces: `DetainedVehicle checkIn(DetainInRequest req, Long operatorUserId)` — never returns null; throws on validation/insert failure. Controller `Result<DetainedVehicle>`.

- [ ] **Step 1: Write failing test `checkInReturnsDetainNoAndInYard`**

```java
@Test
void checkInReturnsDetainNoAndInYard() {
    DetainInRequest req = new DetainInRequest();
    req.setPlateNo(" 粤B停01 ");
    req.setParkingLotId(1L);
    req.setVehicleType("小型车");
    ParkingLot lot = new ParkingLot();
    lot.setId(1L);
    lot.setStatus("ENABLED");
    when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
    when(detainedVehicleMapper.countInYardByPlateNo("粤B停01")).thenReturn(0);
    when(detainedVehicleMapper.countByDetainNoPrefix(org.mockito.ArgumentMatchers.startsWith("DV")))
            .thenReturn(0);
    when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
        DetainedVehicle v = inv.getArgument(0);
        v.setId(88L);
        return 1;
    });

    DetainedVehicle created = service.checkIn(req, 4L);

    assertEquals(88L, created.getId());
    assertEquals("粤B停01", created.getPlateNo());
    assertEquals("IN_YARD", created.getStatus());
    assertEquals(4L, created.getOperatorInId());
    assertNotNull(created.getDetainNo());
    assertTrue(created.getDetainNo().startsWith("DV"));
    assertEquals(14, created.getDetainNo().length());
}
```

- [ ] **Step 2: Run test — expect fail** (boolean vs `DetainedVehicle`, or missing `countByDetainNoPrefix` stub already needed)

```bash
mvn -f backend/pom.xml test -Dtest=DetainedVehicleServiceTest
```

- [ ] **Step 3: Change `checkIn` to return the entity**

Replace the `return detainedVehicleMapper.insert(v) > 0;` tail with:

```java
if (detainedVehicleMapper.insert(v) <= 0) {
    throw new RuntimeException("入库失败");
}
return v;
```

Change method signature to `public DetainedVehicle checkIn(...)`.

Controller:

```java
@PostMapping
@PreAuthorize("hasAuthority('detain:add')")
public Result<DetainedVehicle> checkIn(@RequestBody DetainInRequest request) {
    try {
        return Result.success(detainedVehicleService.checkIn(request, currentUserId()));
    } catch (RuntimeException e) {
        return Result.error(e.getMessage());
    }
}
```

Existing `checkInRejectsWhenPlateAlreadyInYard` still compiles (throw). Do not change PC frontend.

- [ ] **Step 4: Re-run `DetainedVehicleServiceTest` — all PASS**

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/DetainedVehicleService.java \
  backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java \
  backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java
git commit -m "feat(detain): return created record from check-in"
```

---

### Task 3: Workspace helpers (pure functions + node tests)

**Files:**
- Create: `mobile-rescuer/src/utils/workspace.js`
- Create: `mobile-rescuer/src/utils/workspace.test.cjs`
- Modify: `mobile-rescuer/package.json` (add `"test:workspace": "node --test src/utils/workspace.test.cjs"`)

**Interfaces:**
- Produces:
  - `hasRescuerAccess(permissions) -> boolean`
  - `hasParkingAccess(permissions) -> boolean` (`detain:query`)
  - `resolveLoginTarget(permissions) -> 'none' | 'select' | 'rescuer' | 'parking'`
  - `shouldStartGps(workspace) -> boolean` (true only for `'rescuer'`)
  - `matchHangtag(list, payload) -> { kind, record? }`  
    kinds: `empty` | `bind-qr` | `not-found` | `in-yard` | `not-in-yard`  
    `bind-qr` when `/^RV:\d+$/` ; exact `detainNo` equality on `list`

Use **CommonJS** (`module.exports`) so `node --test` can `require` without `"type": "module"`. Vite/uni-app can still named-import CJS.

- [ ] **Step 1: Write `workspace.test.cjs` first (functions missing → fail)**

```javascript
const test = require('node:test')
const assert = require('node:assert/strict')
const {
  hasRescuerAccess,
  hasParkingAccess,
  resolveLoginTarget,
  shouldStartGps,
  matchHangtag
} = require('./workspace.js')

test('rescuer permission via prefix or module', () => {
  assert.equal(hasRescuerAccess(['rescuer:task']), true)
  assert.equal(hasRescuerAccess(['mobile:rescuer']), true)
  assert.equal(hasRescuerAccess(['detain:query']), false)
})

test('parking permission is detain:query', () => {
  assert.equal(hasParkingAccess(['detain:query']), true)
  assert.equal(hasParkingAccess(['parking:query']), false)
})

test('resolveLoginTarget', () => {
  assert.equal(resolveLoginTarget([]), 'none')
  assert.equal(resolveLoginTarget(['rescuer:task']), 'rescuer')
  assert.equal(resolveLoginTarget(['detain:query']), 'parking')
  assert.equal(resolveLoginTarget(['rescuer:task', 'detain:query']), 'select')
})

test('gps only in rescuer workspace', () => {
  assert.equal(shouldStartGps('rescuer'), true)
  assert.equal(shouldStartGps('parking'), false)
  assert.equal(shouldStartGps(''), false)
})

test('matchHangtag exact detainNo', () => {
  const rows = [
    { id: 1, detainNo: 'DV202609070001', status: 'IN_YARD' },
    { id: 2, detainNo: 'DV202609070002', status: 'OUT' }
  ]
  assert.equal(matchHangtag(rows, '').kind, 'empty')
  assert.equal(matchHangtag(rows, 'RV:1').kind, 'bind-qr')
  assert.equal(matchHangtag(rows, 'DV202609070001').kind, 'in-yard')
  assert.equal(matchHangtag(rows, 'DV202609070002').kind, 'not-in-yard')
  assert.equal(matchHangtag(rows, 'NOPE').kind, 'not-found')
})
```

- [ ] **Step 2: Run — FAIL (cannot find module or exports undefined)**

```bash
node --test mobile-rescuer/src/utils/workspace.test.cjs
```

- [ ] **Step 3: Implement `workspace.js`**

```javascript
'use strict'

function hasRescuerAccess(permissions) {
  const list = permissions || []
  return list.some(
    (p) => p === 'mobile:rescuer' || (typeof p === 'string' && p.startsWith('rescuer:'))
  )
}

function hasParkingAccess(permissions) {
  return (permissions || []).includes('detain:query')
}

function resolveLoginTarget(permissions) {
  const rescuer = hasRescuerAccess(permissions)
  const parking = hasParkingAccess(permissions)
  if (!rescuer && !parking) return 'none'
  if (rescuer && parking) return 'select'
  return rescuer ? 'rescuer' : 'parking'
}

function shouldStartGps(workspace) {
  return workspace === 'rescuer'
}

function matchHangtag(list, payload) {
  const text = String(payload || '').trim()
  if (!text) return { kind: 'empty' }
  if (/^RV:\d+$/.test(text)) return { kind: 'bind-qr' }
  const record = (list || []).find((row) => row && row.detainNo === text)
  if (!record) return { kind: 'not-found' }
  if (record.status === 'IN_YARD') return { kind: 'in-yard', record }
  return { kind: 'not-in-yard', record }
}

module.exports = {
  hasRescuerAccess,
  hasParkingAccess,
  resolveLoginTarget,
  shouldStartGps,
  matchHangtag
}
```

- [ ] **Step 4: Re-run node tests — PASS.** Add npm script `test:workspace`.

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/utils/workspace.js mobile-rescuer/src/utils/workspace.test.cjs mobile-rescuer/package.json
git commit -m "feat(mobile): add workspace permission and hangtag match helpers"
```

---

### Task 4: Session, GPS gate, login, select page, workbench shell

**Files:**
- Modify: `mobile-rescuer/src/stores/user.js`
- Modify: `mobile-rescuer/src/utils/locationReporter.js`
- Modify: `mobile-rescuer/src/utils/request.js` (`clearAuthAndGoLogin` also removes `workspace`)
- Modify: `mobile-rescuer/src/App.vue`
- Modify: `mobile-rescuer/src/pages/login/index.vue`
- Create: `mobile-rescuer/src/pages/workspace/select.vue`
- Create: `mobile-rescuer/src/pages/workbench/index.vue`
- Create: `mobile-rescuer/src/components/TaskList.vue` (move current `pages/task/list.vue` template/script **without** `startLocationReporter` / token redirect)
- Modify: `mobile-rescuer/src/pages.json` (tabBar first item → workbench; register select; global title `救援移动端`)
- Create: `mobile-rescuer/src/utils/guard.js`

**Interfaces:**
- Consumes: `resolveLoginTarget`, `shouldStartGps`, `hasRescuerAccess` from workspace.js (Vue: `import { resolveLoginTarget, shouldStartGps, hasRescuerAccess, hasParkingAccess } from '../../utils/workspace.js'`)
- Produces: `setSession` writes `workspace`; `getUserState().workspace`; `enterWorkspace(name)` ; `requirePageAccess(kind)` with `kind` in `public|select|shared|rescuer|parking`

- [ ] **Step 1: Session + GPS**

In `user.js`:
- Add `workspace` to `KEYS`
- `getUserState().workspace = uni.getStorageSync('workspace') || ''`
- `setSession(data)` writes `workspace` from `data.workspace || ''` and **does not** call `startLocationReporter` (caller decides)
- `clearSession` already loops KEYS
- Add `enterWorkspace(name)` → `uni.setStorageSync('workspace', name)` then if `shouldStartGps(name)` start reporter else `stopLocationReporter()`
- Keep exporting `hasRescuerAccess` as a wrapper around workspace helper (same behavior) so existing imports compile; prefer new helper going forward

`locationReporter.startLocationReporter`: after `hasToken()`, if `!shouldStartGps(uni.getStorageSync('workspace') || '')` return immediately (do not start interval).

`App.vue` `onShow`: call `startLocationReporter()` only as today — reporter itself no-ops unless workspace is rescuer.

`request.js` `clearAuthAndGoLogin`: also `uni.removeStorageSync('workspace')`.

- [ ] **Step 2: `guard.js`**

```javascript
import { getUserState, logout } from '../stores/user'
import { resolveLoginTarget } from './workspace.js'

const WORKBENCH = '/pages/workbench/index'
const SELECT = '/pages/workspace/select'
const LOGIN = '/pages/login/index'

export function requirePageAccess(kind) {
  const state = getUserState()
  if (kind === 'public') {
    if (state.token && state.workspace) {
      uni.switchTab({ url: WORKBENCH })
      return false
    }
    return true
  }
  if (!state.token) {
    uni.reLaunch({ url: LOGIN })
    return false
  }
  const target = resolveLoginTarget(state.permissions)
  if (target === 'none') {
    logout()
    return false
  }
  if (!state.workspace) {
    if (kind !== 'select') {
      uni.reLaunch({ url: SELECT })
      return false
    }
    return true
  }
  if (kind === 'select') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
  if (kind === 'rescuer' && state.workspace !== 'rescuer') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
  if (kind === 'parking' && state.workspace !== 'parking') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
  return true
}

export function applyWorkbenchTabText(workspace) {
  uni.setTabBarItem({
    index: 0,
    text: workspace === 'parking' ? '扣车' : '任务'
  })
}
```

- [ ] **Step 3: Login**

Brand text: `救援移动端`. Hint: `towdriver 或 parkingadmin / admin123`.

On success:

```javascript
const permissions = data.permissions || []
const target = resolveLoginTarget(permissions)
if (target === 'none') {
  uni.showToast({ title: '无移动端权限', icon: 'none' })
  return
}
setSession({ ...data, workspace: target === 'select' ? '' : target })
if (target === 'select') {
  uni.reLaunch({ url: '/pages/workspace/select' })
  return
}
if (shouldStartGps(target)) startLocationReporter()
uni.switchTab({ url: '/pages/workbench/index' })
```

`onShow`: `requirePageAccess('public')`.

- [ ] **Step 4: Select page**

Two cards. Show「施救任务」iff `hasRescuerAccess`; 「停车场扣车」iff `hasParkingAccess`. On click: `enterWorkspace('rescuer'|'parking')` then `uni.switchTab` workbench. `onShow`: `requirePageAccess('select')`. No back-to-business path.

- [ ] **Step 5: TaskList component + workbench**

Copy `pages/task/list.vue` into `components/TaskList.vue`:
- Remove token redirect and `startLocationReporter`
- Expose `reload` via `defineExpose({ reload: load })`
- Parent will call `reload` from page `onShow`

`pages/workbench/index.vue`:
- `onShow`: if `!requirePageAccess('shared')` return; `applyWorkbenchTabText(getUserState().workspace)`; if rescuer `startLocationReporter()`; if parking `stopLocationReporter()`; `nextTick` then `taskRef.reload()` or `detainRef.reload()` (detain stub empty until Task 6 — for this task parking branch can show a card `扣车列表加载中…` **or** mount `DetainList` placeholder that shows `暂无数据`. Prefer creating `DetainList.vue` skeleton: empty list, `reload` no-op returning, so workbench compiles.)

Minimal parking stub in workbench (replace in Task 6):

```html
<view v-if="workspace === 'parking'" class="muted center">扣车模块安装中</view>
```

Do **not** ship that stub in the same commit as Task 6; Task 4 may show the stub for one commit, Task 6 replaces it. Acceptable.

- [ ] **Step 6: pages.json**

- Insert `pages/workbench/index` (title `工作台`) after login
- Insert `pages/workspace/select` (title `选择工作台`)
- tabBar first `pagePath`: `pages/workbench/index`, default text `任务`
- `globalStyle.navigationBarTitleText`: `救援移动端`
- Keep `pages/task/list` in pages array; its `onShow` only `uni.switchTab({ url: '/pages/workbench/index' })` so old links bounce (spec: parking must not stay on task list)

- [ ] **Step 7: Manual smoke** (no automated UI): `towdriver` still reaches a task list on first tab.

- [ ] **Step 8: Commit**

```bash
git add mobile-rescuer/src
git commit -m "feat(mobile): add login workspace routing and workbench tab"
```

Stage only intended files (not `.env`).

---

### Task 5: 我的 + `/api/auth/me`; hide bind in parking

**Files:**
- Create: `mobile-rescuer/src/api/auth.js`
- Modify: `mobile-rescuer/src/pages/mine/index.vue`
- Modify: `mobile-rescuer/src/pages/mine/edit.vue`
- Modify: `mobile-rescuer/src/pages/mine/bind.vue` — `requirePageAccess('rescuer')` in `onShow`

**Interfaces:**
- `getMe()` → `GET /auth/me`
- `updateMe(data)` → `PUT /auth/me`

`auth.js`:

```javascript
import { request } from '../utils/request'

export function getMe() {
  return request({ url: '/auth/me' })
}

export function updateMe(data) {
  return request({ url: '/auth/me', method: 'PUT', data })
}
```

Mine `onShow`: `requirePageAccess('shared')`. Load `getMe()` for name/phone/email. Bound vehicle block and bind button: `v-if="workspace === 'rescuer'"`. Rescuer still calls `getBoundVehicle`. Parking: do not call bind-vehicle API. Edit uses `getMe`/`updateMe`. No「切换工作台」control.

- [ ] Commit: `feat(mobile): load profile from /api/auth/me and hide bind in parking`

---

### Task 6: In-yard list, check-in, detail, check-out

**Files:**
- Create: `mobile-rescuer/src/api/detain.js`, `parking.js`
- Create: `mobile-rescuer/src/components/DetainList.vue`
- Create: `mobile-rescuer/src/pages/detain/checkin.vue`
- Create: `mobile-rescuer/src/pages/detain/detail.vue`
- Modify: `pages.json` (register checkin/detail)
- Modify: `workbench/index.vue` to use `DetainList`

**Interfaces:**
- `listDetains({ page, size, plateNo, detainNo, status })` → `GET /detain/list`
- `getDetain(id)` → `GET /detain/{id}`
- `checkInDetain(body)` → `POST /detain`
- `checkOutDetain(id)` → `POST /detain/{id}/out`
- `listParkings({ status: 'ENABLED', page: 1, size: 100 })` → `GET /parking/list`

API helpers use `request({ url, method, data })` like `rescuer.js` (query params via `data` on GET).

**DetainList.vue**
- Default `status=IN_YARD`, `page=1`, `size=10`
- Filters: plateNo, detainNo; query resets page to 1
- Pagination: 10/20/50/100 (buttons or picker) matching PC sizes
- Row: `detainNo`, `plateNo`, `parkingLotName`, `inTime`; tap → `/pages/detain/detail?id=`
- Header: `扫码` always (parking workbench); `入库` if `permissions.includes('detain:add')`
- `defineExpose({ reload })`

**checkin.vue** — `requirePageAccess('parking')`
- Fields: plateNo*, parkingLotId* (select from ENABLED lots), vehicleType, dispatchOrderId (number), detainDept, remark
- If parking fetch fails: toast, empty select, block submit
- Empty plate or lot: toast, no request
- Success: `uni.showToast` `已入库 ${data.detainNo}` then `navigateBack`

**detail.vue** — `requirePageAccess('parking')`
- Show: detainNo, plateNo, vehicleType, parkingLotName, detainDept, orderNo, inTime, remark, status
- Out button iff `status === 'IN_YARD'` && `detain:out`
- Confirm modal then `checkOutDetain`; success `navigateBack`

Workbench parking branch: `<DetainList ref="detainRef" />` and reload onShow.

- [ ] Commit: `feat(mobile): add parking in-yard list, check-in, and check-out`

---

### Task 7: Hangtag scan lookup

**Files:**
- Create: `mobile-rescuer/src/pages/detain/scan.vue`
- Modify: `pages.json` (title `扫码查找`)
- Modify: `DetainList.vue` 扫码 → `navigateTo` scan page
- Modify: `mobile-rescuer/src/pages/mine/scan.vue` navigation title to `扫码` (shared camera)
- `scanCode.js` stays pointed at `/pages/mine/scan` (generic camera + eventChannel)

**Interfaces:**
- Consumes: `listDetains({ detainNo: payload, page: 1, size: 10 })`, `matchHangtag`
- Produces: navigate to detail on `in-yard`; toasts per spec

`scan.vue` (`requirePageAccess('parking')`):
- Input +「查找」+「扫码」(`scanQrCode`)
- Shared `lookup(text)`:

```javascript
import { matchHangtag } from '../../utils/workspace.js'
import { listDetains } from '../../api/detain'

async function lookup(raw) {
  const text = String(raw || '').trim()
  const pre = matchHangtag([], text)
  if (pre.kind === 'empty') {
    uni.showToast({ title: '请输入或扫描扣押编号', icon: 'none' })
    return
  }
  if (pre.kind === 'bind-qr') {
    uni.showToast({ title: '请扫描吊牌二维码', icon: 'none' })
    return
  }
  try {
    const res = await listDetains({ detainNo: text, page: 1, size: 10 })
    const list = res.data?.list || []
    const hit = matchHangtag(list, text)
    if (hit.kind === 'in-yard') {
      uni.navigateTo({ url: `/pages/detain/detail?id=${hit.record.id}` })
      return
    }
    if (hit.kind === 'not-in-yard') {
      uni.showToast({ title: '该车辆已出库或已清理', icon: 'none' })
      return
    }
    uni.showToast({ title: '未找到扣留记录', icon: 'none' })
  } catch (_) {}
}
```

- [ ] Re-run `node --test mobile-rescuer/src/utils/workspace.test.cjs` (still PASS)

- [ ] Commit: `feat(mobile): lookup detained vehicles by hangtag QR`

---

### Task 8: Route guards on remaining pages + final verification

**Files:**
- Modify: `pages/task/detail.vue`, `scene.vue`, `park.vue` — first line of `onShow`/`onLoad`: `if (!requirePageAccess('rescuer')) return`
- Modify: detain pages already gated in Tasks 6–7
- Modify: `pages/mine/scan.vue` — **do not** use `rescuer` guard (shared camera)
- Modify: `README.md` of `mobile-rescuer` — document `parkingadmin` / workspace picker / demo accounts

**Verification (must run, paste outcomes in the task report):**

1. `node --test mobile-rescuer/src/utils/workspace.test.cjs` — PASS
2. `mvn -f backend/pom.xml test -Dtest=AuthServiceTest,DetainedVehicleServiceTest` — PASS
3. Manual H5 (`npm run dev:h5`) with backend up:
   - `towdriver` / `admin123` → task workbench, 我的 shows bind, no 扣车 list
   - `parkingadmin` / `admin123` → in-yard list, no bind, GPS interval should not call `/mobile/rescuer/location`
   - `admin` / `admin123` → select page; pick parking; 我的 has no switch; logout required to pick rescuer
   - Check-in a plate → toast `DVyyyyMMdd####` → appears on list → detail → 出库 → gone from default in-yard list
   - Scan/paste that `detainNo` after out → toast 已出库或已清理
   - From parking session open `/pages/task/detail` (dev tools) → bounce to workbench

- [ ] Commit: `docs(mobile): document dual-workspace login and guard remaining pages`

---

## Self-review

| Spec item | Task |
|-----------|------|
| Single app, workspace picker, skip if one role | 3, 4 |
| GPS / bind only rescuer | 4, 5 |
| `/api/auth/me` | 1, 5 |
| Detain list / in / out, reuse APIs | 2, 6 |
| Hangtag exact match + RV: reject | 3, 7 |
| Guards wrong workspace | 4, 8 |
| No parking CRUD / clear / print / mobile parking API | (omitted) |
| PC ignore check-in `data` | (no PC edit) |

No TBD. `checkIn` return type is `DetainedVehicle` in Task 2 and consumed as `res.data.detainNo` in Task 6. `matchHangtag` kinds are identical in Task 3 tests and Task 7 lookup.
