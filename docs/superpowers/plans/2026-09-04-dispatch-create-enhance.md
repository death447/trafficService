# 新建工单增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建工单支持调度员/施救员/车辆预填（仍 PENDING），地图支持定位居中、中国视野回退、地点模糊搜索与拖拽选点；详情默认选中预填空闲车。

**Architecture:** 扩展 `DispatchOrderService.create/update` 校验并写入预填字段且不 `markBusy`；`amap.js` 增强 `createPickerMap`；`DispatchCreate.vue` 补表单；`DispatchDetail.vue` 默认选车并 `assign` 带 `rescuerId`。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、Vue 3、高德 JS API 2.0（Geolocation / AutoComplete / Geocoder）

**Spec:** `docs/superpowers/specs/2026-09-04-dispatch-create-enhance-design.md`

## Global Constraints

- 提交后状态必须 `PENDING`；预填车辆**不** `markBusy`、不校验 IDLE
- `dispatcherId` 缺省=当前用户；若传入须具备角色 `DISPATCHER` 或 `ADMIN`
- `rescuerId` 可选；若传入须具备 `TOW_DRIVER`
- `vehicleId` 可选；若传入须车辆存在
- 定位成功：zoom≈15 居中当前位置，**不**自动写入事故点
- 定位失败：中国视野 zoom 4–5，中心约 `[105, 35]`
- 权限码不变：`dispatch:add` / `dispatch:edit` / `dispatch:dispatch`
- YAGNI：不创建即派单、不强制车/员、不叠围栏轨迹、无新 UI 库

## File Structure

### Backend
- Modify: `service/DispatchOrderService.java`、`controller/DispatchController.java`（若需从 body 读 dispatcherId）
- Modify: `test/.../DispatchOrderServiceTest.java`
- Uses: `UserMapper.findRolesByUserId`、`RescueVehicleService.findById`（或 Mapper）

### Frontend
- Modify: `utils/amap.js`、`views/dispatch/DispatchCreate.vue`、`views/dispatch/DispatchDetail.vue`

---

### Task 1: 后端 create/update 预填字段

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`
- Possibly: inject `UserMapper`（已有 `findRolesByUserId`）

**Interfaces:**
- Produces:
  - `create(DispatchOrder order, Long currentUserId)` — 应用预填校验后 insert
  - `update` — PENDING 时可改 dispatcherId/rescuerId/vehicleId + 地点字段
- Helper: `assertHasRole(userId, String... roleCodes)`、`applyPrefill(order, currentUserId)`

- [ ] **Step 1: 写失败测试**

在 `DispatchOrderServiceTest` 增加 `@Mock UserMapper userMapper`（及若用 vehicleMapper / `rescueVehicleService.findById`）。

```java
@Test
void createKeepsPendingAndPrefillsWithoutMarkBusy() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("测试路");
    order.setRescueReason("追尾");
    order.setDispatcherId(2L);
    order.setRescuerId(3L);
    order.setVehicleId(1L);
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    Role t = new Role(); t.setRoleCode("TOW_DRIVER");
    when(userMapper.findRolesByUserId(2L)).thenReturn(List.of(d));
    when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
    RescueVehicle v = new RescueVehicle(); v.setId(1L); v.setStatus("IDLE");
    when(rescueVehicleService.findById(1L)).thenReturn(v);
    when(dispatchOrderMapper.insert(any())).thenReturn(1);

    assertTrue(service.create(order, 99L));
    assertEquals("PENDING", order.getStatus());
    assertEquals(2L, order.getDispatcherId());
    assertEquals(3L, order.getRescuerId());
    assertEquals(1L, order.getVehicleId());
    verify(rescueVehicleService, never()).markBusy(any());
    verify(dispatchOrderMapper).insert(order);
}

@Test
void createDefaultsDispatcherToCurrentUser() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    // no dispatcherId
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(dispatchOrderMapper.insert(any())).thenReturn(1);
    assertTrue(service.create(order, 7L));
    assertEquals(7L, order.getDispatcherId());
}

@Test
void createRejectsInvalidRescuerRole() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    order.setRescuerId(3L);
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    Role bad = new Role(); bad.setRoleCode("ADMIN");
    when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(bad));
    assertThrows(RuntimeException.class, () -> service.create(order, 7L));
}

@Test
void createRejectsMissingVehicle() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    order.setVehicleId(404L);
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(rescueVehicleService.findById(404L)).thenReturn(null);
    assertThrows(RuntimeException.class, () -> service.create(order, 7L));
}
```

同步改 `update` 测试：PENDING 可写预填；非 PENDING 仍拒绝核心编辑。

- [ ] **Step 2: 跑测失败**

```bash
cd backend && mvn -q -Dtest=DispatchOrderServiceTest test
```

Expected: FAIL（仍清空 vehicle/rescuer 或缺 UserMapper）

- [ ] **Step 3: 实现 `create`/`update`**

替换强制清空逻辑：

```java
public boolean create(DispatchOrder order, Long currentUserId) {
    order.setOrderNo(generateOrderNo());
    order.setStatus("PENDING");
    applyDispatcherAndPrefill(order, currentUserId);
    order.setAbortReason(null);
    order.setDispatchedAt(null);
    order.setCompletedAt(null);
    return dispatchOrderMapper.insert(order) > 0;
}
```

`applyDispatcherAndPrefill`：
1. dispatcherId = order.getDispatcherId() != null ? that : currentUserId；校验角色含 DISPATCHER 或 ADMIN
2. rescuerId null OK；非 null → 角色含 TOW_DRIVER，否则抛「施救员角色无效」
3. vehicleId null OK；非 null → findById 非空，否则「车辆不存在」
4. **禁止**调用 markBusy

`update`：在现有 PENDING 校验后，除地址/原因/坐标外调用同一预填逻辑（dispatcher 以 order 中值为准，currentUserId 仅作缺省）。

- [ ] **Step 4: Controller** — `create` 已把 body 反序列化为 `DispatchOrder`，确保不要在 Controller 覆盖掉 body 的 dispatcherId（当前用 `currentUserId()` 传入第二个参数即可；body 可带 dispatcherId）。

- [ ] **Step 5: 测试全绿并 commit**

```bash
git add backend/src/main/java/com/example/backend/service/DispatchOrderService.java \
  backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java
git commit -m "feat(backend): allow pending dispatch prefill of people and vehicle"
```

---

### Task 2: amap.js 定位 / 搜索 / 拖拽

**Files:**
- Modify: `frontend/src/utils/amap.js`

**Interfaces:**
- `createPickerMap(container, { lng, lat, onPicked, searchInput })`  
  - `searchInput`: 可选 HTMLInputElement 或选择器，绑定 AutoComplete  
  - 返回 `{ map, setMarker(lng,lat,address?), destroy() }`（若现返回 map，可改为对象并让调用方兼容：若只有 map.destroy 则 DispatchCreate 用返回值.destroy）

- [ ] **Step 1: 重写/扩展 `createPickerMap`**

行为顺序：
1. `loadAmap()`；创建 Map（先不要设深圳默认；可用中国中心 zoom 5 作为初始，避免闪一下深圳）
2. `AMap.plugin(['AMap.Geolocation','AMap.Geocoder','AMap.AutoComplete','AMap.PlaceSearch'], ...)`
3. Geolocation `getCurrentPosition`：成功 → `setZoomAndCenter(15, [lng,lat])`，**不**调 onPicked
4. 失败/超时 → `setZoomAndCenter(5, [105, 35])`
5. 若传入初始 lng/lat（编辑场景）→ 直接 setZoomAndCenter(13,…) 并 setMarker
6. click → setMarker + Geocoder → onPicked
7. Marker `draggable: true`；`dragend` → Geocoder → onPicked
8. 若有 `searchInput`：AutoComplete `city: '全国'` 或留空；`select` 事件取 `poi.location` / name → setMarker + onPicked

无 Key 时函数仍抛错，由页面捕获。

- [ ] **Step 2: 手工或 node 冒烟（可选）** — 至少确保无语法错误：`npm run build` 在 Task 3 一并跑

- [ ] **Step 3: Commit**

```bash
git add frontend/src/utils/amap.js
git commit -m "feat(frontend): enhance map picker with geolocation and place search"
```

---

### Task 3: DispatchCreate 表单页

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`
- Uses: `getUserList` from `api/user.js`、`listVehicles` from `api/vehicle.js`、`useUserStore`

- [ ] **Step 1: 表单模型**

```js
form: {
  rescueReason, accidentAddress, longitude, latitude,
  dispatcherId, rescuerId, vehicleId
}
```

`dispatcherId` 默认 `userStore.userInfo.id`（或 store 中用户 id 字段，按现有 Login 数据结构）。

- [ ] **Step 2: 下拉数据**

- `getUserList({ size: 500 })` → 按 `roles` 过滤：调度员选项含 DISPATCHER/ADMIN；施救员含 TOW_DRIVER  
  （若 list 不带 roles，则对候选调 `getUserRoles` 或后端已返回 roles——查看现有 UserList 响应；必要时用已加载用户的 role 字段。）
- `listVehicles({})` → 选项显示 `plateNo` + status 文案；不强制仅 IDLE

- [ ] **Step 3: 模板**

在原因/地点旁增加三个 `<select>`；地点 input 增加 `ref="searchInput"` 供 AutoComplete；地图区提示：「可搜索地点、点击或拖动标记选点」。

- [ ] **Step 4: initMap**

```js
mapInstance = await createPickerMap(mapEl.value, {
  searchInput: searchInput.value,
  onPicked({ lng, lat, address }) { ... }
})
```

提交 payload 含三个 id（空字符串转 null）。

- [ ] **Step 5: `npm run build` SUCCESS**

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/dispatch/DispatchCreate.vue
git commit -m "feat(frontend): complete create-dispatch form with people and vehicle"
```

---

### Task 4: DispatchDetail 预填默认选车 + assign rescuer

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

- [ ] **Step 1: loadNearby 成功后**

```js
const pref = order.value?.vehicleId
if (pref != null) {
  const hit = nearby.value.find((i) => i.vehicle.id === pref)
  if (hit) selectedVehicleId.value = pref
  else nearbyHint.value = (nearbyHint.value ? nearbyHint.value + ' ' : '') + '预填车辆当前不可派，请另选'
}
```

信息区展示 `order.rescuerName` / 预填车辆牌照（若有）。

- [ ] **Step 2: assign 请求**

```js
await assignDispatch(id.value, {
  vehicleId: selectedVehicleId.value,
  rescuerId: order.value?.rescuerId ?? null
})
```

- [ ] **Step 3: build + commit**

```bash
git commit -m "feat(frontend): default-select prefilled vehicle on dispatch detail"
```

---

### Task 5: 回归

- [ ] `cd backend && mvn -q test` — BUILD SUCCESS  
- [ ] 对照验收 1–6：能代码验证的标 code-verified，地图定位标 deferred-manual 若无真机  
- [ ] 报告写入 `.superpowers/sdd/`（若走 SDD）或仅在对话确认

---

## Spec coverage

| Spec | Task |
|------|------|
| create/update 预填、PENDING、不 markBusy | 1 |
| 角色/车辆校验 | 1 |
| Geolocation / 中国视野 / 搜索 / 拖拽 | 2 |
| 新建表单字段 | 3 |
| 详情默认选车 + assign rescuer | 4 |
| 验收与回归 | 5 |
