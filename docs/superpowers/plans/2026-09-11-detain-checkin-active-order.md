# 移动端入库按车牌关联运行中工单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 停车场 H5 入库输入车牌后检索未结束工单，按规范化车牌关联 `dispatchOrderId`；1 条自动绑、多条点选、0 条仍可入库。

**Architecture:** 共享 `PlateNos.normalize`；`GET /api/detain/active-orders`（`detain:query`）在 Java 侧过滤 `PENDING|DISPATCHED|ACCEPTED`；`POST /api/detain` 二次校验状态与车牌。移动端把匹配/空字段补全抽到 `plateOrder.js`，入库页防抖调用。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、uni-app Vue3 H5、Node test runner

**Spec:** `docs/superpowers/specs/2026-09-11-detain-checkin-active-order-design.md`

## Global Constraints

- 运行中状态仅：`PENDING`、`DISPATCHED`、`ACCEPTED`（不含 `COMPLETED`、`ABORTED`）
- 只比 `dispatch_order.plate_no`，不比 `dispatch_field_record.plate_no`
- 规范化：删除 Unicode 空白、`·`（U+00B7）、`.` 后 `toUpperCase(Locale.ROOT)` / JS `toUpperCase()`
- 规范化后长度 &lt; 2：检索返回 `Result.error(400, "请输入车牌")`，不查库
- 空字段才带出：`vehicleType` ← `vehicleTypeName`，`rescueAddress` ← `accidentAddress`
- 权限：`detain:query`；不新增权限码、不改角色种子、不给停车场开 `dispatch:query`
- 不改 PC 入库表单；不把施救「入库登记」写入扣车表
- Maven 必须用 JDK 17：`JAVA_HOME=C:\Program Files\Java\jdk-17`，可执行文件 `d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd`

## File Structure

### Backend
- Create: `backend/src/main/java/com/example/backend/util/PlateNos.java`
- Create: `backend/src/test/java/com/example/backend/util/PlateNosTest.java`
- Create: `backend/src/main/java/com/example/backend/dto/ActiveDispatchSummary.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java`（`GET /active-orders` 必须写在 `GET /{id}` **之前**）
- Test: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

### Mobile
- Create: `mobile-rescuer/src/utils/plateOrder.js`
- Create: `mobile-rescuer/src/utils/plateOrder.test.cjs`
- Modify: `mobile-rescuer/package.json`（`test:workspace` 脚本加入新测试文件）
- Modify: `mobile-rescuer/src/api/detain.js`
- Modify: `mobile-rescuer/src/pages/detain/checkin.vue`

---

### Task 1: `PlateNos.normalize`

**Files:**
- Create: `backend/src/main/java/com/example/backend/util/PlateNos.java`
- Test: `backend/src/test/java/com/example/backend/util/PlateNosTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `PlateNos.normalize(String raw) -> String`（`null` 得到 `""`）；后续检索与入库校验必须调用它，禁止另写一套去空白逻辑

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/example/backend/util/PlateNosTest.java`:

```java
package com.example.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlateNosTest {

    @Test
    void normalizeStripsSpaceDotMiddleDotAndUppercases() {
        assertEquals("粤B12345", PlateNos.normalize(" 粤b·12345 "));
        assertEquals("粤B12345", PlateNos.normalize("粤B.12345"));
        assertEquals("", PlateNos.normalize(null));
        assertEquals("", PlateNos.normalize(" ·. "));
        assertEquals("AB", PlateNos.normalize("A B"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
& "d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd" -f "d:\cursorworkspace\backend\pom.xml" test -Dtest=PlateNosTest
```

Expected: FAIL compiling (`cannot find symbol: class PlateNos`) or test missing method.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/example/backend/util/PlateNos.java`:

```java
package com.example.backend.util;

import java.util.Locale;

public final class PlateNos {

    private PlateNos() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || cp == 0x00B7 || cp == '.') {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command as Step 2.

Expected: `PlateNosTest` tests = 1, FAIL = 0.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/util/PlateNos.java backend/src/test/java/com/example/backend/util/PlateNosTest.java
git commit -m "feat: add PlateNos.normalize for detain-order matching"
```

---

### Task 2: `GET /api/detain/active-orders`

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/ActiveDispatchSummary.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java`
- Test: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

**Interfaces:**
- Consumes: `PlateNos.normalize(String)`; `DispatchOrderMapper.findActiveWithPlate()`
- Produces: `DetainedVehicleService.listActiveOrdersByPlate(String plateNo) -> List<ActiveDispatchSummary>`；规范化后长度 &lt; 2 时抛 `RuntimeException("请输入车牌")`；`GET /api/detain/active-orders?plateNo=` 权限 `detain:query`，成功 `data.list`，非法车牌 `Result.error(400, "请输入车牌")`

- [ ] **Step 1: Write failing service tests**

Add to `DetainedVehicleServiceTest.java` (keep existing tests). Helper at bottom of test class:

```java
private static DispatchOrder order(long id, String status, String plate) {
    DispatchOrder o = new DispatchOrder();
    o.setId(id);
    o.setOrderNo("RO" + id);
    o.setStatus(status);
    o.setPlateNo(plate);
    o.setVehicleTypeName("小型汽车");
    o.setAccidentAddress("测试路" + id);
    return o;
}
```

Add import: `com.example.backend.dto.ActiveDispatchSummary`, `com.example.backend.entity.DispatchOrder`, `java.util.List`.

```java
@Test
void listActiveOrdersRejectsShortPlate() {
    RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.listActiveOrdersByPlate(" ·A. "));
    assertEquals("请输入车牌", ex.getMessage());
    verify(dispatchOrderMapper, never()).findActiveWithPlate();
}

@Test
void listActiveOrdersMatchesNormalizedPlateNewestFirst() {
    when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(
            order(3L, "ACCEPTED", "粤B12345"),
            order(2L, "PENDING", "粤b·12345"),
            order(1L, "DISPATCHED", "粤A00000")
    ));

    List<ActiveDispatchSummary> list = service.listActiveOrdersByPlate(" 粤B.12345 ");

    assertEquals(2, list.size());
    assertEquals(3L, list.get(0).getId());
    assertEquals(2L, list.get(1).getId());
    assertEquals("粤b·12345", list.get(1).getPlateNo());
}

@Test
void listActiveOrdersReturnsEmptyWhenNoMatch() {
    when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(
            order(1L, "PENDING", "粤A1")
    ));
    assertTrue(service.listActiveOrdersByPlate("粤B99999").isEmpty());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
& "d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd" -f "d:\cursorworkspace\backend\pom.xml" test -Dtest=DetainedVehicleServiceTest
```

Expected: FAIL (`listActiveOrdersByPlate` / `findActiveWithPlate` not found). Existing check-in tests must still compile.

- [ ] **Step 3: Mapper + DTO + service + controller**

`ActiveDispatchSummary.java`:

```java
package com.example.backend.dto;

import lombok.Data;

@Data
public class ActiveDispatchSummary {
    private Long id;
    private String orderNo;
    private String status;
    private String plateNo;
    private String vehicleTypeName;
    private String accidentAddress;
}
```

Add to `DispatchOrderMapper.java`:

```java
@Select("SELECT * FROM dispatch_order WHERE status IN ('PENDING','DISPATCHED','ACCEPTED') "
        + "AND plate_no IS NOT NULL AND TRIM(plate_no) <> '' ORDER BY id DESC")
List<DispatchOrder> findActiveWithPlate();
```

Add to `DetainedVehicleService` (imports: `ActiveDispatchSummary`, `PlateNos`, `ArrayList`):

```java
public List<ActiveDispatchSummary> listActiveOrdersByPlate(String plateNo) {
    String needle = PlateNos.normalize(plateNo);
    if (needle.length() < 2) {
        throw new RuntimeException("请输入车牌");
    }
    List<DispatchOrder> rows = dispatchOrderMapper.findActiveWithPlate();
    List<ActiveDispatchSummary> out = new ArrayList<>();
    for (DispatchOrder row : rows) {
        if (needle.equals(PlateNos.normalize(row.getPlateNo()))) {
            ActiveDispatchSummary s = new ActiveDispatchSummary();
            s.setId(row.getId());
            s.setOrderNo(row.getOrderNo());
            s.setStatus(row.getStatus());
            s.setPlateNo(row.getPlateNo());
            s.setVehicleTypeName(row.getVehicleTypeName());
            s.setAccidentAddress(row.getAccidentAddress());
            out.add(s);
        }
    }
    return out;
}
```

In `DetainedVehicleController.java`, insert **between** `list(...)` and `@GetMapping("/{id}")`:

```java
@GetMapping("/active-orders")
@PreAuthorize("hasAuthority('detain:query')")
public Result<Map<String, Object>> listActiveOrders(@RequestParam(required = false) String plateNo) {
    try {
        return Result.success(Map.of("list", detainedVehicleService.listActiveOrdersByPlate(plateNo)));
    } catch (RuntimeException e) {
        if ("请输入车牌".equals(e.getMessage())) {
            return Result.error(400, e.getMessage());
        }
        return Result.error(e.getMessage());
    }
}
```

Do **not** place this mapping after `/{id}`.

- [ ] **Step 4: Run tests to verify they pass**

Same Maven command as Step 2.

Expected: `DetainedVehicleServiceTest` all PASS, including the three new tests and existing check-in tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/ActiveDispatchSummary.java backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java backend/src/main/java/com/example/backend/service/DetainedVehicleService.java backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java
git commit -m "feat: look up active dispatch orders by normalized plate"
```

---

### Task 3: 入库提交二次校验关联工单

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`（`checkIn` 里 `dispatchOrderId` 分支）
- Test: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

**Interfaces:**
- Consumes: `PlateNos.normalize`；`dispatchOrderMapper.findById`
- Produces: `checkIn` 在 `dispatchOrderId != null` 时：不存在 → `关联救援工单不存在`；状态不是 PENDING/DISPATCHED/ACCEPTED → `工单已结束，无法关联`；规范化车牌不等 → `工单车牌与入库车牌不一致`；通过则照旧写入 `dispatchOrderId`

- [ ] **Step 1: Write failing tests**

Add helper in the test class:

```java
private DetainInRequest baseIn(String plate) {
    DetainInRequest req = new DetainInRequest();
    req.setDetainNo("DV-X");
    req.setPlateNo(plate);
    req.setParkingLotId(1L);
    ParkingLot lot = new ParkingLot();
    lot.setId(1L);
    lot.setStatus("ENABLED");
    when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
    when(detainedVehicleMapper.findByDetainNo("DV-X")).thenReturn(null);
    when(detainedVehicleMapper.countInYardByPlateNo(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
    return req;
}
```

If `countInYardByPlateNo` is stubbed with `anyString()`, existing tests that stub a concrete plate still work (more specific stubs win only if declared; prefer stubbing the exact trimmed plate in each new test instead of `anyString`).

Use exact plates:

```java
@Test
void checkInRejectsFinishedLinkedOrder() {
    DetainInRequest req = new DetainInRequest();
    req.setDetainNo("DV-F");
    req.setPlateNo("粤B12345");
    req.setParkingLotId(1L);
    req.setDispatchOrderId(9L);
    ParkingLot lot = new ParkingLot();
    lot.setId(1L);
    lot.setStatus("ENABLED");
    when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
    when(detainedVehicleMapper.findByDetainNo("DV-F")).thenReturn(null);
    when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
    DispatchOrder finished = order(9L, "COMPLETED", "粤B12345");
    when(dispatchOrderMapper.findById(9L)).thenReturn(finished);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 4L));
    assertTrue(ex.getMessage().contains("已结束"));
    verify(detainedVehicleMapper, never()).insert(any());
}

@Test
void checkInRejectsLinkedOrderPlateMismatch() {
    DetainInRequest req = new DetainInRequest();
    req.setDetainNo("DV-M");
    req.setPlateNo("粤B12345");
    req.setParkingLotId(1L);
    req.setDispatchOrderId(8L);
    ParkingLot lot = new ParkingLot();
    lot.setId(1L);
    lot.setStatus("ENABLED");
    when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
    when(detainedVehicleMapper.findByDetainNo("DV-M")).thenReturn(null);
    when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
    when(dispatchOrderMapper.findById(8L)).thenReturn(order(8L, "ACCEPTED", "粤A00000"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 4L));
    assertTrue(ex.getMessage().contains("车牌"));
    verify(detainedVehicleMapper, never()).insert(any());
}

@Test
void checkInAcceptsLinkedOrderWhenNormalizedPlateMatches() {
    DetainInRequest req = new DetainInRequest();
    req.setDetainNo("DV-OK");
    req.setPlateNo("粤B12345");
    req.setParkingLotId(1L);
    req.setDispatchOrderId(7L);
    ParkingLot lot = new ParkingLot();
    lot.setId(1L);
    lot.setStatus("ENABLED");
    when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
    when(detainedVehicleMapper.findByDetainNo("DV-OK")).thenReturn(null);
    when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
    when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
    when(dispatchOrderMapper.findById(7L)).thenReturn(order(7L, "ACCEPTED", "粤b·12345"));
    when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
        DetainedVehicle v = inv.getArgument(0);
        v.setId(101L);
        return 1;
    });

    DetainedVehicle created = service.checkIn(req, 4L);
    assertEquals(7L, created.getDispatchOrderId());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
& "d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd" -f "d:\cursorworkspace\backend\pom.xml" test -Dtest=DetainedVehicleServiceTest
```

Expected: `checkInRejectsFinishedLinkedOrder` / plate mismatch FAIL because current code only checks existence. `checkInAcceptsLinkedOrderWhenNormalizedPlateMatches` may PASS accidentally (existence-only); if it passes, keep it as regression for the happy path after you add checks.

- [ ] **Step 3: Minimal checkIn change**

Replace the `if (req.getDispatchOrderId() != null)` block in `checkIn` with:

```java
if (req.getDispatchOrderId() != null) {
    DispatchOrder order = dispatchOrderMapper.findById(req.getDispatchOrderId());
    if (order == null) {
        throw new RuntimeException("关联救援工单不存在");
    }
    String st = order.getStatus();
    if (!"PENDING".equals(st) && !"DISPATCHED".equals(st) && !"ACCEPTED".equals(st)) {
        throw new RuntimeException("工单已结束，无法关联");
    }
    if (!PlateNos.normalize(plate).equals(PlateNos.normalize(order.getPlateNo()))) {
        throw new RuntimeException("工单车牌与入库车牌不一致");
    }
}
```

`plate` is already `requireTrimmedPlate(...)` above this block. ABORTED also hits「工单已结束，无法关联」.

- [ ] **Step 4: Run tests to verify they pass**

Same Maven command as Step 2.

Expected: all `DetainedVehicleServiceTest` PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/DetainedVehicleService.java backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java
git commit -m "feat: validate linked dispatch order on detain check-in"
```

---

### Task 4: 移动端匹配与空字段补全纯函数

**Files:**
- Create: `mobile-rescuer/src/utils/plateOrder.js`
- Create: `mobile-rescuer/src/utils/plateOrder.test.cjs`
- Modify: `mobile-rescuer/package.json`（scripts）

**Interfaces:**
- Consumes: 无
- Produces: `normalizePlate(raw) -> string`；`shouldSearchPlate(raw) -> boolean`（规范化后 `length >= 2`）；`classifyMatches(list) -> { kind: 'none'|'one'|'many', order?, orders? }`；`applyEmptyOrderFields(form, order)` 只填空的 `vehicleType` / `rescueAddress`；`ORDER_STATUS_TEXT`：`PENDING` 待派、`DISPATCHED` 已派、`ACCEPTED` 已接单

- [ ] **Step 1: Write the failing test**

Create `mobile-rescuer/src/utils/plateOrder.test.cjs`:

```javascript
'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const {
  normalizePlate,
  shouldSearchPlate,
  classifyMatches,
  applyEmptyOrderFields
} = require('./plateOrder.js')

test('normalizePlate strips space middle-dot dot and uppercases', () => {
  assert.equal(normalizePlate(' 粤b·12345 '), '粤B12345')
  assert.equal(normalizePlate('粤B.12345'), '粤B12345')
  assert.equal(normalizePlate(null), '')
})

test('shouldSearchPlate requires two normalized chars', () => {
  assert.equal(shouldSearchPlate(' ·A. '), false)
  assert.equal(shouldSearchPlate('粤B'), true)
})

test('classifyMatches one many none', () => {
  assert.equal(classifyMatches([]).kind, 'none')
  assert.equal(classifyMatches([{ id: 1 }]).kind, 'one')
  assert.equal(classifyMatches([{ id: 1 }]).order.id, 1)
  assert.equal(classifyMatches([{ id: 1 }, { id: 2 }]).kind, 'many')
  assert.equal(classifyMatches([{ id: 1 }, { id: 2 }]).orders.length, 2)
})

test('applyEmptyOrderFields fills only blanks', () => {
  const blank = { vehicleType: '', rescueAddress: '' }
  applyEmptyOrderFields(blank, { vehicleTypeName: '小型汽车', accidentAddress: '事故路' })
  assert.equal(blank.vehicleType, '小型汽车')
  assert.equal(blank.rescueAddress, '事故路')

  const filled = { vehicleType: '已填', rescueAddress: '已有地点' }
  applyEmptyOrderFields(filled, { vehicleTypeName: '覆盖', accidentAddress: '覆盖地' })
  assert.equal(filled.vehicleType, '已填')
  assert.equal(filled.rescueAddress, '已有地点')
})
```

Add `src/utils/plateOrder.test.cjs` to `package.json` script `test:workspace`:

```json
"test:workspace": "node --test src/utils/workspace.test.cjs src/utils/plateOrder.test.cjs cjsNamedExportsToEsm.test.cjs"
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
cd d:\cursorworkspace\mobile-rescuer
npm run test:workspace
```

Expected: FAIL cannot find `./plateOrder.js`.

- [ ] **Step 3: Write `plateOrder.js`**

Create `mobile-rescuer/src/utils/plateOrder.js` (CJS, 与 `workspace.js` 相同，供 Vite 改写 named export)：

```javascript
'use strict'

const ORDER_STATUS_TEXT = {
  PENDING: '待派',
  DISPATCHED: '已派',
  ACCEPTED: '已接单'
}

function normalizePlate(raw) {
  return String(raw || '')
    .replace(/[\s·.]/gu, '')
    .toUpperCase()
}

function shouldSearchPlate(raw) {
  return normalizePlate(raw).length >= 2
}

function classifyMatches(list) {
  const rows = list || []
  if (rows.length === 0) return { kind: 'none' }
  if (rows.length === 1) return { kind: 'one', order: rows[0] }
  return { kind: 'many', orders: rows }
}

function applyEmptyOrderFields(form, order) {
  if (!form || !order) return
  if (!String(form.vehicleType || '').trim() && order.vehicleTypeName) {
    form.vehicleType = order.vehicleTypeName
  }
  if (!String(form.rescueAddress || '').trim() && order.accidentAddress) {
    form.rescueAddress = order.accidentAddress
  }
}

module.exports = {
  ORDER_STATUS_TEXT,
  normalizePlate,
  shouldSearchPlate,
  classifyMatches,
  applyEmptyOrderFields
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
cd d:\cursorworkspace\mobile-rescuer
npm run test:workspace
```

Expected: all tests PASS, including existing workspace tests.

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/utils/plateOrder.js mobile-rescuer/src/utils/plateOrder.test.cjs mobile-rescuer/package.json
git commit -m "feat: add plate-order match helpers for parking check-in"
```

---

### Task 5: 入库页检索 UI 与提交 `dispatchOrderId`

**Files:**
- Modify: `mobile-rescuer/src/api/detain.js`
- Modify: `mobile-rescuer/src/pages/detain/checkin.vue`

**Interfaces:**
- Consumes: `listActiveOrders(plateNo)` → `GET /detain/active-orders`；`classifyMatches` / `applyEmptyOrderFields` / `shouldSearchPlate` / `ORDER_STATUS_TEXT` / `normalizePlate`
- Produces: 车牌防抖 400ms + 失焦检索；1 条自动关联；多条弹层点选；0 条 toast「未找到运行中工单」；可清除；改车牌先清关联再查；提交 body 在有关联时带 `dispatchOrderId`

- [ ] **Step 1: Add API client**

In `mobile-rescuer/src/api/detain.js` add:

```javascript
export function listActiveOrders(plateNo) {
  return request({ url: '/detain/active-orders', data: { plateNo } })
}
```

- [ ] **Step 2: Wire checkin page (no new failing UI test; logic already covered in Task 4)**

In `checkin.vue` template, immediately after the 车牌 `input` field, add associated-order + picker:

```html
      <view class="field">
        <view class="field-label">车牌号码 *</view>
        <input
          class="field-input"
          v-model="form.plateNo"
          placeholder="请填写车牌号码"
          @input="onPlateInput"
          @blur="searchOrders"
        />
        <view v-if="linkedOrder" class="link-row">
          <text class="link-text">已关联 {{ linkedOrder.orderNo }} / {{ statusText(linkedOrder.status) }}</text>
          <text class="link-clear" @click="clearLink">清除</text>
        </view>
      </view>

      <view v-if="pickerVisible" class="picker-mask" @click="pickerVisible = false">
        <view class="picker-sheet" @click.stop>
          <view class="picker-title">选择运行中工单</view>
          <view
            v-for="row in pickerRows"
            :key="row.id"
            class="picker-item"
            @click="bindOrder(row)"
          >
            <text>{{ row.orderNo }} · {{ statusText(row.status) }}</text>
            <text class="picker-sub">{{ row.accidentAddress || '—' }}</text>
          </view>
          <view class="picker-cancel" @click="pickerVisible = false">不关联</view>
        </view>
      </view>
```

Place the mask as a sibling inside `.page` (not only step 1) so it overlays the stepper; keep `v-if="pickerVisible"`.

Script changes — add imports:

```javascript
import { computed, reactive, ref } from 'vue'
import { checkInDetain, uploadDetainMedia, listActiveOrders } from '../../api/detain'
import {
  ORDER_STATUS_TEXT,
  applyEmptyOrderFields,
  classifyMatches,
  shouldSearchPlate
} from '../../utils/plateOrder.js'
```

Add state next to `form`:

```javascript
const linkedOrder = ref(null)
const pickerVisible = ref(false)
const pickerRows = ref([])
let plateTimer = null
let searchSeq = 0
```

`form` stays without `dispatchOrderId` field; linked order is separate.

```javascript
function statusText(status) {
  return ORDER_STATUS_TEXT[status] || status || ''
}

function clearLink() {
  linkedOrder.value = null
  pickerVisible.value = false
}

function bindOrder(order) {
  linkedOrder.value = order
  pickerVisible.value = false
  applyEmptyOrderFields(form, order)
}

function onPlateInput() {
  clearLink()
  if (plateTimer) clearTimeout(plateTimer)
  plateTimer = setTimeout(() => { searchOrders() }, 400)
}

async function searchOrders() {
  if (plateTimer) {
    clearTimeout(plateTimer)
    plateTimer = null
  }
  if (!shouldSearchPlate(form.plateNo)) return
  const seq = ++searchSeq
  try {
    const res = await listActiveOrders(String(form.plateNo || '').trim())
    if (seq !== searchSeq) return
    const list = res.data?.list || []
    const decision = classifyMatches(list)
    if (decision.kind === 'one') {
      bindOrder(decision.order)
      return
    }
    if (decision.kind === 'many') {
      pickerRows.value = decision.orders
      pickerVisible.value = true
      return
    }
    uni.showToast({ title: '未找到运行中工单', icon: 'none' })
  } catch (_) {
    /* request.js 已 toast；不打断填表 */
  }
}
```

In `onSubmit` `body` construction, after `parkingLotId`:

```javascript
  if (linkedOrder.value && linkedOrder.value.id) {
    body.dispatchOrderId = Number(linkedOrder.value.id)
  }
```

Add styles:

```css
.link-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12rpx;
  font-size: 24rpx;
}
.link-text { color: #2979ff; flex: 1; padding-right: 16rpx; }
.link-clear { color: #999; }
.picker-mask {
  position: fixed;
  left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0,0,0,0.4);
  z-index: 20;
  display: flex;
  align-items: flex-end;
}
.picker-sheet {
  width: 100%;
  background: #fff;
  border-radius: 16rpx 16rpx 0 0;
  padding: 24rpx 32rpx 48rpx;
}
.picker-title { font-size: 30rpx; font-weight: 600; margin-bottom: 16rpx; }
.picker-item {
  padding: 20rpx 0;
  border-bottom: 1px solid #eee;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.picker-sub { color: #999; font-size: 24rpx; }
.picker-cancel { text-align: center; color: #666; padding: 28rpx 0 8rpx; }
```

- [ ] **Step 3: Run unit tests again**

```powershell
cd d:\cursorworkspace\mobile-rescuer
npm run test:workspace
```

Expected: PASS.

- [ ] **Step 4: Manual check (dev servers if already running)**

1. `parkingadmin` / `admin123` 进停车场工作台 → 入库。
2. 准备一张 `PENDING`/`DISPATCHED`/`ACCEPTED` 且 `plate_no` 带 `·` 的工单；入库输入去掉点的同一车牌 → 自动出现「已关联 ROxxxx」。
3. 同牌两张未结束工单 → 弹出列表；点选后车型/地点仅在空白时带出。
4. 无匹配 → toast「未找到运行中工单」，仍可提交。
5. 点清除后提交，扣车记录 `dispatch_order_id` 为空。

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/api/detain.js mobile-rescuer/src/pages/detain/checkin.vue
git commit -m "feat: auto-link active dispatch orders on parking check-in"
```

---

## Spec coverage

| Spec | Task |
|------|------|
| 规范化算法 | 1、4 |
| GET active-orders + 400 短车牌 + list 倒序 | 2 |
| 1/多/0 命中 | 4 classify + 5 UI |
| 空字段带出、不覆盖 | 4 + 5 bindOrder |
| 提交二次校验状态/车牌 | 3 |
| 可清除、改车牌重查、防抖 400ms | 5 |
| 不改 PC / 不新权限码 | 全局约束，无对应改动 |

## Placeholder scan

无 TBD；方法名全程 `listActiveOrdersByPlate` / `findActiveWithPlate` / `normalizePlate` / `classifyMatches` / `applyEmptyOrderFields`。
