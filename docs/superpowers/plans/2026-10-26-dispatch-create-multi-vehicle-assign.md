# 新建工单多选施救车并即时派单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建工单支持多选施救车；有选车时同一事务内立刻派单，无选车则仅创建 PENDING。

**Architecture:** `DispatchOrder` 增加非持久化 `vehicleIds`；`create` 合并 `vehicleId`+`vehicleIds` 后插入工单，非空则复用现有 `assign(orderId, List)`。前端 `DispatchCreate` 改为复选+地图多选，提交 `vehicleIds`。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、Vue 3

**Spec:** `docs/superpowers/specs/2026-10-26-dispatch-create-multi-vehicle-assign-design.md`

## Global Constraints

- 无车 → `PENDING`，无 assignment，不 `markBusy`
- 有车 → 同事务 `assign`；失败整单回滚
- `vehicleId` 与 `vehicleIds` 去重合并
- 不改详情页加派 UI、不改 `/assign` 契约
- 权限码不变：`dispatch:add`
- Windows PowerShell：Maven `-Dtest=...` 需加引号；源文件 UTF-8 无 BOM

## File Structure

- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java` — 非表字段 `vehicleIds`
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java` — `create` + 合并辅助 + 调整 `applyDispatcherAndPrefill`
- Modify: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue` — 多选 UI 与提交

---

### Task 1: 后端 create 合并 vehicleIds 并即时 assign

**Files:**
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`

**Interfaces:**
- Consumes: existing `assign(Long orderId, List<Long> vehicleIds)`, `requireAssignable`, `assignmentMapper`
- Produces:
  - `DispatchOrder.vehicleIds: List<Long>`（JSON 入参，非 DB）
  - `List<Long> resolveVehicleIdsToAssign(DispatchOrder order)` — 合并去重后清空 order 上预填车/员
  - `create`：无车 PENDING；有车则 insert 后 `assign`

- [ ] **Step 1: 改写/新增失败单测（先红）**

将过时的 `createKeepsPendingAndPrefillsWithoutMarkBusy` **替换**为下列行为（预填改即时派）：

```java
@Test
void createWithNoVehiclesStaysPending() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("测试路");
    order.setRescueReason("追尾");
    order.setPlateNo("粤B00001");
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
        DispatchOrder o = inv.getArgument(0);
        o.setId(1L);
        return 1;
    });

    assertTrue(service.create(order, 7L));
    assertEquals("PENDING", order.getStatus());
    assertNull(order.getVehicleId());
    assertNull(order.getRescuerId());
    verify(assignmentMapper, never()).insert(any());
    verify(rescueVehicleService, never()).markBusy(any());
}

@Test
void createWithVehicleIdsAssignsAll() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("测试路");
    order.setRescueReason("追尾");
    order.setPlateNo("粤B00001");
    order.setVehicleIds(List.of(1L, 2L));
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    Role t = new Role(); t.setRoleCode("TOW_DRIVER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
    when(userMapper.findRolesByUserId(4L)).thenReturn(List.of(t));
    RescueVehicle v1 = new RescueVehicle();
    v1.setId(1L); v1.setStatus("IDLE"); v1.setDriverUserId(3L);
    RescueVehicle v2 = new RescueVehicle();
    v2.setId(2L); v2.setStatus("BUSY"); v2.setDriverUserId(4L);
    when(rescueVehicleService.requireAssignable(1L)).thenReturn(v1);
    when(rescueVehicleService.requireAssignable(2L)).thenReturn(v2);
    when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
        DispatchOrder o = inv.getArgument(0);
        o.setId(10L);
        assertNull(o.getVehicleId()); // insert 前不写镜像列
        return 1;
    });
    when(dispatchOrderMapper.findById(10L)).thenAnswer(inv -> {
        DispatchOrder o = new DispatchOrder();
        o.setId(10L);
        o.setStatus(order.getStatus() != null ? order.getStatus() : "PENDING");
        o.setVehicleId(order.getVehicleId());
        o.setRescuerId(order.getRescuerId());
        o.setDispatchedAt(order.getDispatchedAt());
        return o;
    });
    when(assignmentMapper.countByOrderIdAndVehicleId(eq(10L), anyLong())).thenReturn(0);
    when(assignmentMapper.insert(any())).thenAnswer(inv -> {
        DispatchVehicleAssignment a = inv.getArgument(0);
        a.setId(a.getVehicleId());
        return 1;
    });
    when(assignmentMapper.findByOrderId(10L)).thenAnswer(inv -> {
        List<DispatchVehicleAssignment> list = new ArrayList<>();
        for (Long vid : List.of(1L, 2L)) {
            DispatchVehicleAssignment a = new DispatchVehicleAssignment();
            a.setId(vid);
            a.setDispatchOrderId(10L);
            a.setVehicleId(vid);
            a.setStatus("DISPATCHED");
            list.add(a);
        }
        return list;
    });
    when(dispatchOrderMapper.update(any())).thenReturn(1);

    assertTrue(service.create(order, 7L));
    verify(assignmentMapper, times(2)).insert(any());
    verify(rescueVehicleService).markBusy(1L);
    verify(rescueVehicleService).markBusy(2L);
    assertEquals("DISPATCHED", order.getStatus());
    assertEquals(2L, order.getVehicleId()); // 最后一辆
    assertEquals(4L, order.getRescuerId());
}

@Test
void createMergesVehicleIdIntoVehicleIds() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    order.setPlateNo("粤B00002");
    order.setVehicleId(1L);
    order.setVehicleIds(List.of(1L, 2L)); // 1 去重
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    Role t = new Role(); t.setRoleCode("TOW_DRIVER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
    when(userMapper.findRolesByUserId(4L)).thenReturn(List.of(t));
    RescueVehicle v1 = new RescueVehicle();
    v1.setId(1L); v1.setStatus("IDLE"); v1.setDriverUserId(3L);
    RescueVehicle v2 = new RescueVehicle();
    v2.setId(2L); v2.setStatus("IDLE"); v2.setDriverUserId(4L);
    when(rescueVehicleService.requireAssignable(1L)).thenReturn(v1);
    when(rescueVehicleService.requireAssignable(2L)).thenReturn(v2);
    when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
        DispatchOrder o = inv.getArgument(0);
        o.setId(11L);
        return 1;
    });
    when(dispatchOrderMapper.findById(11L)).thenAnswer(inv -> {
        DispatchOrder o = new DispatchOrder();
        o.setId(11L);
        o.setStatus(order.getStatus() != null ? order.getStatus() : "PENDING");
        o.setVehicleId(order.getVehicleId());
        o.setRescuerId(order.getRescuerId());
        o.setDispatchedAt(order.getDispatchedAt());
        return o;
    });
    when(assignmentMapper.countByOrderIdAndVehicleId(eq(11L), anyLong())).thenReturn(0);
    when(assignmentMapper.insert(any())).thenAnswer(inv -> {
        DispatchVehicleAssignment a = inv.getArgument(0);
        a.setId(a.getVehicleId());
        return 1;
    });
    when(assignmentMapper.findByOrderId(11L)).thenAnswer(inv -> {
        List<DispatchVehicleAssignment> list = new ArrayList<>();
        for (Long vid : List.of(1L, 2L)) {
            DispatchVehicleAssignment a = new DispatchVehicleAssignment();
            a.setVehicleId(vid);
            a.setStatus("DISPATCHED");
            list.add(a);
        }
        return list;
    });
    when(dispatchOrderMapper.update(any())).thenReturn(1);

    assertTrue(service.create(order, 7L));
    verify(assignmentMapper, times(2)).insert(any());
}

@Test
void createWithBadVehicleDoesNotLeavePartialAssign() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    order.setPlateNo("粤B00003");
    order.setVehicleIds(List.of(1L, 99L));
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    Role t = new Role(); t.setRoleCode("TOW_DRIVER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
    RescueVehicle v1 = new RescueVehicle();
    v1.setId(1L); v1.setStatus("IDLE"); v1.setDriverUserId(3L);
    when(rescueVehicleService.requireAssignable(1L)).thenReturn(v1);
    when(rescueVehicleService.requireAssignable(99L))
            .thenThrow(new RuntimeException("车辆不存在"));
    when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
        DispatchOrder o = inv.getArgument(0);
        o.setId(12L);
        return 1;
    });
    when(dispatchOrderMapper.findById(12L)).thenAnswer(inv -> {
        DispatchOrder o = new DispatchOrder();
        o.setId(12L);
        o.setStatus("PENDING");
        return o;
    });
    when(assignmentMapper.countByOrderIdAndVehicleId(eq(12L), anyLong())).thenReturn(0);
    when(assignmentMapper.insert(any())).thenAnswer(inv -> {
        DispatchVehicleAssignment a = inv.getArgument(0);
        a.setId(1L);
        return 1;
    });
    // refreshOrderStatus after first assign
    when(assignmentMapper.findByOrderId(12L)).thenReturn(List.of());
    when(dispatchOrderMapper.update(any())).thenReturn(1);

    assertThrows(RuntimeException.class, () -> service.create(order, 7L));
}
```

同步改掉仍依赖「预填不派车」的用例：

- `createRejectsMissingVehicle` / `createRejectsOfflineVehicle`：保留，但行为改为走 `assign`（`findById` mock 必需）。
- `createRejectsInvalidRescuerRole`：若仅测客户端乱传 `rescuerId`，改为「无 vehicleIds 时忽略/清空 rescuerId，仍 PENDING」或删除该用例（create 不再用预填 rescuer）。

- [ ] **Step 2: 跑测确认失败**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;d:\cursorworkspace\.tools\apache-maven\bin;" + $env:Path
Set-Location d:\cursorworkspace\backend
mvn -q "-Dtest=DispatchOrderServiceTest#createWithVehicleIdsAssignsAll" test
```

Expected: FAIL（尚无 `vehicleIds` / 仍预填不派）

- [ ] **Step 3: 实体加字段**

在 `DispatchOrder.java` 非表字段区增加：

```java
/** Request-only: vehicles to assign on create. Not a DB column. */
private List<Long> vehicleIds;
```

- [ ] **Step 4: 实现 create 流程**

在 `DispatchOrderService`：

```java
List<Long> resolveVehicleIdsToAssign(DispatchOrder order) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    if (order.getVehicleIds() != null) {
        for (Long id : order.getVehicleIds()) {
            if (id != null) ids.add(id);
        }
    }
    if (order.getVehicleId() != null) {
        ids.add(order.getVehicleId());
    }
    order.setVehicleId(null);
    order.setRescuerId(null);
    order.setVehicleIds(null);
    return new ArrayList<>(ids);
}
```

改 `applyDispatcherAndPrefill`：删除「若 vehicleId != null 则 requireAssignable 并写 rescuerId」整段；仅保留 dispatcher 角色校验。

改 `create`：

```java
@Transactional
public boolean create(DispatchOrder order, Long currentUserId) {
    List<Long> toAssign = resolveVehicleIdsToAssign(order);
    order.setOrderNo(generateOrderNo());
    order.setStatus("PENDING");
    applyDispatcherAndPrefill(order, currentUserId);
    List<DispatchAccidentVehicle> vehicles = normalizeAndValidateAccidentVehicles(
            resolveAccidentVehiclesInput(order));
    syncFirstAccidentVehicleToOrder(order, vehicles.get(0));
    order.setAbortReason(null);
    order.setDispatchedAt(null);
    order.setCompletedAt(null);
    if (dispatchOrderMapper.insert(order) <= 0) {
        return false;
    }
    replaceAccidentVehicles(order.getId(), vehicles);
    order.setAccidentVehicles(vehicles);
    if (!toAssign.isEmpty()) {
        assign(order.getId(), toAssign);
        DispatchOrder latest = dispatchOrderMapper.findById(order.getId());
        if (latest != null) {
            order.setStatus(latest.getStatus());
            order.setVehicleId(latest.getVehicleId());
            order.setRescuerId(latest.getRescuerId());
            order.setDispatchedAt(latest.getDispatchedAt());
        }
    }
    return true;
}
```

说明：同类内调用 `assign` 不走代理，但外层 `create` 的 `@Transactional` 已覆盖整段，失败会回滚。

- [ ] **Step 5: 跑全量相关单测**

```powershell
mvn -q "-Dtest=DispatchOrderServiceTest" test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/DispatchOrder.java \
  backend/src/main/java/com/example/backend/service/DispatchOrderService.java \
  backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java \
  docs/superpowers/specs/2026-10-26-dispatch-create-multi-vehicle-assign-design.md \
  docs/superpowers/plans/2026-10-26-dispatch-create-multi-vehicle-assign.md
git commit -m "$(cat <<'EOF'
feat: assign multiple vehicles on dispatch create

EOF
)"
```

（PowerShell 无 HEREDOC 时用：`git commit -m "feat: assign multiple vehicles on dispatch create"`）

---

### Task 2: 新建页多选 UI 与提交 vehicleIds

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`

**Interfaces:**
- Consumes: `createDispatch({ vehicleIds, ... })`（已有 POST）
- Produces: 表单 `selectedVehicleIds: string[]`；提交 `vehicleIds: number[]`

- [ ] **Step 1: 表单状态改为多选**

将 `form.vehicleId` / `form.rescuerId` 替换为：

```js
const selectedVehicleIds = ref([]) // string ids

function toggleVehicle(id) {
  const s = String(id)
  const i = selectedVehicleIds.value.indexOf(s)
  if (i >= 0) selectedVehicleIds.value.splice(i, 1)
  else selectedVehicleIds.value.push(s)
}

function clearSelectedVehicles() {
  selectedVehicleIds.value = []
}

function isVehicleSelected(id) {
  return selectedVehicleIds.value.includes(String(id))
}
```

删除对 `form.vehicleId` 的 watch；`boundRescuerLabel` 改为：

```js
const selectedVehicleSummaries = computed(() => {
  return selectedVehicleIds.value.map((id) => {
    const v = selectableVehicles.value.find((x) => String(x.id) === id)
    if (!v) return { id, plate: `#${id}`, rescuer: '—' }
    const rescuer =
      v.driverName ||
      (v.driverUserId != null ? `用户#${v.driverUserId}` : '该车辆未绑定施救员')
    return { id, plate: v.plateNo || `#${id}`, rescuer }
  })
})
```

- [ ] **Step 2: 模板 — 复选列表 + 施救员汇总**

替换「救援车辆」`<select>` 为：

```html
<label class="vehicle-pick">
  救援车辆
  <div class="vehicle-pick-toolbar">
    <button type="button" class="linkish" @click="clearSelectedVehicles"
            :disabled="!selectedVehicleIds.length">清空已选</button>
    <span class="field-hint">可选多辆；保存时立即派单。不选则仅建待派工单。</span>
  </div>
  <ul class="vehicle-check-list">
    <li v-for="v in selectableVehicles" :key="v.id">
      <label>
        <input type="checkbox"
               :checked="isVehicleSelected(v.id)"
               @change="toggleVehicle(v.id)" />
        {{ vehicleDisplay(v) }}
      </label>
    </li>
  </ul>
  <span v-if="nearbyHint" class="field-hint">{{ nearbyHint }}</span>
</label>
<label>
  施救员
  <div v-if="!selectedVehicleIds.length" class="readonly-input">请先选择救援车辆</div>
  <ul v-else class="rescuer-summary">
    <li v-for="row in selectedVehicleSummaries" :key="row.id">
      {{ row.plate }} → {{ row.rescuer }}
    </li>
  </ul>
</label>
```

补充 scoped 样式（列表可滚动、max-height ~12rem，与现有表单变量一致）。

- [ ] **Step 3: 地图点击改为 toggle**

`syncVehicleMarkers` 内 `marker.on('click')`：

```js
marker.on('click', () => {
  toggleVehicle(v.id)
})
```

标记 label 在已选时加「✓」或「（已选）」前缀（用 `isVehicleSelected(v.id)`）。选中变化时 `watch(selectedVehicleIds, syncVehicleMarkers, { deep: true })`。

- [ ] **Step 4: 提交 payload**

```js
const res = await createDispatch({
  rescueReason: form.rescueReason,
  accidentAddress: form.accidentAddress,
  longitude: lng,
  latitude: lat,
  dispatcherId: userStore.userId,
  vehicleIds: selectedVehicleIds.value
    .map((id) => Number(id))
    .filter((n) => !Number.isNaN(n)),
  accidentVehicles: vehiclesPayload
})
```

去掉 `rescuerId` / 单个 `vehicleId`。

- [ ] **Step 5: 手测**

1. 重启后端使 Task 1 生效；前端 HMR 即可。
2. 新建：不选车 → 详情 PENDING、无已派列表。
3. 勾选 2 辆（含任务中）→ 详情 `DISPATCHED`、已派车辆 2 条。
4. 地图点两辆 → 复选同步；再点取消。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/dispatch/DispatchCreate.vue
git commit -m "feat: multi-select rescue vehicles on dispatch create"
```

---

## Spec coverage (self-review)

| Spec 要求 | Task |
|-----------|------|
| 无车 PENDING | Task 1 `createWithNoVehiclesStaysPending` |
| 有车即时 assign | Task 1 `createWithVehicleIdsAssignsAll` |
| vehicleId 合并 | Task 1 `createMergesVehicleIdIntoVehicleIds` |
| 失败回滚 | Task 1 `createWithBadVehicle...` + `@Transactional` |
| 实体 vehicleIds | Task 1 Step 3 |
| 前端复选/地图多选/汇总/提交 | Task 2 |
| 不改详情加派 UI | 无任务（明确不做） |

无 TBD/TODO 占位。
