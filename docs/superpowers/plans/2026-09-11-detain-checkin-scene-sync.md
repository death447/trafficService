# 入库同步签到时间与工单受损照 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 停车场 H5 入库绑定运行中工单时，用工单签到时间覆盖施救时间，预览并在提交时把工单 `DAMAGE` 照片复制为扣车 `SCENE`。

**Architecture:** 扩展现有 `GET /api/detain/active-orders` 摘要（`checkedInAt`、`damagePhotoPaths`）。`POST /api/detain` 在插入成功后于同一事务内把磁盘文件从 `uploads/dispatch/...` 复制到 `uploads/detain/{id}/` 并写 `detain_media`。移动端只预览 URL，不把工单图当本地文件再传。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、uni-app Vue3 H5、Node test runner

**Spec:** `docs/superpowers/specs/2026-09-11-detain-checkin-scene-sync-design.md`

## Global Constraints

- 仅移动端入库 + 现有 `GET /api/detain/active-orders` 与 `POST /api/detain`；不改 PC
- 绑工单且 `checked_in_at` 非空 → 覆盖 `form.rescueTime`；未签到 → 保持打开页时的 `formatNow()`；仍可手改
- 预览工单 `DAMAGE`；提交时服务端复制为扣车 `SCENE`；再上传停车场本地新拍
- 源文件缺失或非法路径：跳过该张；复制 IO 失败：抛错并回滚整单
- 取消关联：清工单预览图；`rescueTime = formatNow()`；保留本地 `scenePhotos`
- 权限仍为 `detain:query` / `detain:add`；不新增权限码；不复制工单 `PARK`
- Maven：`JAVA_HOME=C:\Program Files\Java\jdk-17`，`d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd`
- 工作目录：`d:\cursorworkspace`，当前功能分支 `feat/detain-checkin-active-order`（不要新建无关分支除非已不在该分支）

## File Structure

### Backend
- Modify: `backend/src/main/java/com/example/backend/service/LocalFileStorageService.java`
- Test: `backend/src/test/java/com/example/backend/service/LocalFileStorageServiceTest.java`
- Modify: `backend/src/main/java/com/example/backend/dto/ActiveDispatchSummary.java`
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`
- Reuse: `DispatchMediaMapper.findByOrderIdAndBizType`（已存在）

### Mobile
- Modify: `mobile-rescuer/src/utils/plateOrder.js`
- Modify: `mobile-rescuer/src/utils/plateOrder.test.cjs`
- Modify: `mobile-rescuer/src/pages/detain/checkin.vue`
- Reuse: `mediaUrl` in `mobile-rescuer/src/utils/request.js`

---

### Task 1: `LocalFileStorageService.copyToDetainMedia`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/LocalFileStorageService.java`
- Create: `backend/src/test/java/com/example/backend/service/LocalFileStorageServiceTest.java`

**Interfaces:**
- Consumes: 现有 `app.upload-dir` 根目录
- Produces: `String copyToDetainMedia(Long detainId, String sourceRelativePath)`  
  - 成功：返回 `detain/{id}/{timestamp}{ext}`  
  - 源不存在、空白、扩展名不是 `.jpg/.jpeg/.png/.webp`、或解析后不在 upload 根下：返回 `null`（调用方跳过）  
  - 源存在但 `Files.copy` 抛 `IOException`：抛 `RuntimeException("文件复制失败")`

- [ ] **Step 1: Write the failing test**

Create `LocalFileStorageServiceTest.java`:

```java
package com.example.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir Path tmp;
    LocalFileStorageService storage = new LocalFileStorageService();

    @BeforeEach
    void setDir() {
        ReflectionTestUtils.setField(storage, "uploadDir", tmp.toString());
    }

    @Test
    void copyToDetainMediaCopiesExistingJpeg() throws Exception {
        Path src = tmp.resolve("dispatch/7/a.jpg");
        Files.createDirectories(src.getParent());
        Files.write(src, new byte[] {1, 2, 3});

        String dest = storage.copyToDetainMedia(88L, "dispatch/7/a.jpg");

        assertNotNull(dest);
        assertTrue(dest.startsWith("detain/88/"));
        assertTrue(dest.endsWith(".jpg"));
        assertTrue(Files.exists(tmp.resolve(dest)));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(tmp.resolve(dest)));
    }

    @Test
    void copyToDetainMediaReturnsNullWhenMissing() {
        assertNull(storage.copyToDetainMedia(1L, "dispatch/9/nope.jpg"));
        assertNull(storage.copyToDetainMedia(1L, null));
        assertNull(storage.copyToDetainMedia(1L, "dispatch/9/x.gif"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
& "d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd" -f "d:\cursorworkspace\backend\pom.xml" test "-Dtest=LocalFileStorageServiceTest"
```

Expected: FAIL (`copyToDetainMedia` not found).

- [ ] **Step 3: Minimal implementation**

Add to `LocalFileStorageService.java` (keep existing store/delete methods). Allowed extensions same as store: `.jpg` `.jpeg` `.png` `.webp`.

```java
public String copyToDetainMedia(Long detainId, String sourceRelativePath) {
    if (detainId == null || sourceRelativePath == null || sourceRelativePath.isBlank()) {
        return null;
    }
    Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
    Path source = root.resolve(sourceRelativePath).normalize();
    if (!source.startsWith(root) || !Files.isRegularFile(source)) {
        return null;
    }
    String name = source.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot >= 0 ? name.substring(dot).toLowerCase() : "";
    if (!List.of(".jpg", ".jpeg", ".png", ".webp").contains(ext)) {
        return null;
    }
    try {
        Path dir = root.resolve(Paths.get("detain", String.valueOf(detainId))).normalize();
        if (!dir.startsWith(root)) {
            return null;
        }
        Files.createDirectories(dir);
        String destName = System.currentTimeMillis() + ext;
        Path target = dir.resolve(destName).normalize();
        if (!target.startsWith(dir)) {
            return null;
        }
        Files.copy(source, target);
        return "detain/" + detainId + "/" + destName;
    } catch (IOException e) {
        throw new RuntimeException("文件复制失败");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Same Maven command. Expected: 2 tests, FAIL=0.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/LocalFileStorageService.java backend/src/test/java/com/example/backend/service/LocalFileStorageServiceTest.java
git commit -m "feat: copy dispatch media files into detain upload dir"
```

PowerShell: do not use bash HEREDOC; `git commit -m "..."` is fine.

---

### Task 2: 摘要带 `checkedInAt` 与 `damagePhotoPaths`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/dto/ActiveDispatchSummary.java`
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

**Interfaces:**
- Consumes: `DispatchOrder.getCheckedInAt()`；`DispatchMediaMapper.findByOrderIdAndBizType(orderId, "DAMAGE")`（已有）
- Produces: `ActiveDispatchSummary.checkedInAt`（`LocalDateTime`，未签到 `null`）；`damagePhotoPaths`（`List<String>`，永不 `null`，不含 `PARK`）

- [ ] **Step 1: Write failing tests**

Add `@Mock DispatchMediaMapper dispatchMediaMapper;` to `DetainedVehicleServiceTest` (field already has other mocks). Import `DispatchMedia`, `java.time.LocalDateTime`.

Extend helper `order(...)` only if needed; in new tests set `checkedInAt` on the entity after `order(...)`.

```java
@Test
void listActiveOrdersIncludesCheckinAndDamagePathsNotPark() {
    DispatchOrder row = order(3L, "ACCEPTED", "粤B12345");
    row.setCheckedInAt(LocalDateTime.of(2026, 9, 9, 16, 38, 15));
    when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(row));
    DispatchMedia dmg = new DispatchMedia();
    dmg.setFilePath("dispatch/3/a.jpg");
    dmg.setBizType("DAMAGE");
    DispatchMedia park = new DispatchMedia();
    park.setFilePath("dispatch/3/p.jpg");
    park.setBizType("PARK");
    when(dispatchMediaMapper.findByOrderIdAndBizType(3L, "DAMAGE")).thenReturn(List.of(dmg));

    List<ActiveDispatchSummary> list = service.listActiveOrdersByPlate("粤B12345");

    assertEquals(1, list.size());
    assertEquals(LocalDateTime.of(2026, 9, 9, 16, 38, 15), list.get(0).getCheckedInAt());
    assertEquals(List.of("dispatch/3/a.jpg"), list.get(0).getDamagePhotoPaths());
}

@Test
void listActiveOrdersNullCheckinAndEmptyPhotos() {
    when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(
            order(2L, "PENDING", "粤B12345")
    ));
    when(dispatchMediaMapper.findByOrderIdAndBizType(2L, "DAMAGE")).thenReturn(List.of());

    ActiveDispatchSummary s = service.listActiveOrdersByPlate("粤B12345").get(0);
    assertNull(s.getCheckedInAt());
    assertNotNull(s.getDamagePhotoPaths());
    assertTrue(s.getDamagePhotoPaths().isEmpty());
}
```

Existing `listActiveOrdersMatchesNormalizedPlateNewestFirst` will call `findByOrderIdAndBizType` (default mock returns `null`). Implementation must treat `null` as empty list so this test still passes without extra stubs.

- [ ] **Step 2: Run tests to verify they fail**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
& "d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd" -f "d:\cursorworkspace\backend\pom.xml" test "-Dtest=DetainedVehicleServiceTest"
```

Expected: new tests FAIL (fields/mapper missing). Existing list tests must still compile.

- [ ] **Step 3: Implement**

`ActiveDispatchSummary.java` add:

```java
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

private LocalDateTime checkedInAt;
private List<String> damagePhotoPaths = new ArrayList<>();
```

`DetainedVehicleService`: `@Autowired DispatchMediaMapper dispatchMediaMapper;`

In `listActiveOrdersByPlate` after setting accidentAddress:

```java
s.setCheckedInAt(row.getCheckedInAt());
List<String> paths = new ArrayList<>();
List<DispatchMedia> medias = dispatchMediaMapper.findByOrderIdAndBizType(row.getId(), "DAMAGE");
if (medias != null) {
    for (DispatchMedia m : medias) {
        if (m != null && m.getFilePath() != null && !m.getFilePath().isBlank()) {
            paths.add(m.getFilePath());
        }
    }
}
s.setDamagePhotoPaths(paths);
```

Do not query `PARK`. Mapper already filters `biz_type`.

- [ ] **Step 4: Run tests to verify they pass**

Same Maven command. Expected: all `DetainedVehicleServiceTest` PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/ActiveDispatchSummary.java backend/src/main/java/com/example/backend/service/DetainedVehicleService.java backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java
git commit -m "feat: include check-in time and damage paths on active orders"
```

---

### Task 3: `checkIn` 复制 DAMAGE → SCENE

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

**Interfaces:**
- Consumes: `copyToDetainMedia`；`findByOrderIdAndBizType(id, "DAMAGE")`；`detainMediaMapper.insert`
- Produces: `checkIn` 在 `insert` 成功且 `dispatchOrderId != null` 时，对每张成功复制的图插入 `detain_media`（`bizType=SCENE`，`uploadedBy=operatorUserId`）。`copyToDetainMedia` 返回 `null` 则跳过。无 `dispatchOrderId` 不调用复制。

- [ ] **Step 1: Write failing tests**

```java
@Test
void checkInCopiesDamagePhotosToSceneAndSkipsMissing() {
    DetainInRequest req = new DetainInRequest();
    req.setDetainNo("DV-CP");
    req.setPlateNo("粤B12345");
    req.setParkingLotId(1L);
    req.setDispatchOrderId(7L);
    ParkingLot lot = new ParkingLot();
    lot.setId(1L);
    lot.setStatus("ENABLED");
    when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
    when(detainedVehicleMapper.findByDetainNo("DV-CP")).thenReturn(null);
    when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
    when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
    when(dispatchOrderMapper.findById(7L)).thenReturn(order(7L, "ACCEPTED", "粤B12345"));
    when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
        DetainedVehicle v = inv.getArgument(0);
        v.setId(101L);
        return 1;
    });
    DispatchMedia keep = new DispatchMedia();
    keep.setFilePath("dispatch/7/a.jpg");
    DispatchMedia miss = new DispatchMedia();
    miss.setFilePath("dispatch/7/gone.jpg");
    when(dispatchMediaMapper.findByOrderIdAndBizType(7L, "DAMAGE")).thenReturn(List.of(keep, miss));
    when(fileStorageService.copyToDetainMedia(101L, "dispatch/7/a.jpg")).thenReturn("detain/101/1.jpg");
    when(fileStorageService.copyToDetainMedia(101L, "dispatch/7/gone.jpg")).thenReturn(null);
    when(detainMediaMapper.insert(any())).thenReturn(1);

    service.checkIn(req, 4L);

    org.mockito.ArgumentCaptor<DetainMedia> cap = org.mockito.ArgumentCaptor.forClass(DetainMedia.class);
    verify(detainMediaMapper, times(1)).insert(cap.capture());
    assertEquals("SCENE", cap.getValue().getBizType());
    assertEquals("detain/101/1.jpg", cap.getValue().getFilePath());
    assertEquals(101L, cap.getValue().getDetainId());
    assertEquals(4L, cap.getValue().getUploadedBy());
}

@Test
void checkInWithoutOrderDoesNotCopyMedia() {
    DetainInRequest req = new DetainInRequest();
    req.setDetainNo("DV-NO");
    req.setPlateNo("粤B停01");
    req.setParkingLotId(1L);
    ParkingLot lot = new ParkingLot();
    lot.setId(1L);
    lot.setStatus("ENABLED");
    when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
    when(detainedVehicleMapper.findByDetainNo("DV-NO")).thenReturn(null);
    when(detainedVehicleMapper.countInYardByPlateNo("粤B停01")).thenReturn(0);
    when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
    when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
        DetainedVehicle v = inv.getArgument(0);
        v.setId(88L);
        return 1;
    });

    service.checkIn(req, 4L);

    verify(dispatchMediaMapper, never()).findByOrderIdAndBizType(any(), any());
    verify(fileStorageService, never()).copyToDetainMedia(any(), any());
}
```

Existing `checkInAcceptsLinkedOrderWhenNormalizedPlateMatches` will now query DAMAGE. Stub `when(dispatchMediaMapper.findByOrderIdAndBizType(7L, "DAMAGE")).thenReturn(List.of());` in that test so it stays green.

- [ ] **Step 2: Run tests to verify they fail**

Same `-Dtest=DetainedVehicleServiceTest`. Expected: copy tests FAIL (no insert SCENE).

- [ ] **Step 3: Implement after insert in `checkIn`**

```java
if (detainedVehicleMapper.insert(v) <= 0) {
    throw new RuntimeException("入库失败");
}
if (req.getDispatchOrderId() != null) {
    copyDamageToScene(req.getDispatchOrderId(), v.getId(), operatorUserId);
}
return v;
```

Private method:

```java
private void copyDamageToScene(Long orderId, Long detainId, Long operatorUserId) {
    List<DispatchMedia> medias = dispatchMediaMapper.findByOrderIdAndBizType(orderId, "DAMAGE");
    if (medias == null) {
        return;
    }
    for (DispatchMedia m : medias) {
        if (m == null) {
            continue;
        }
        String dest = fileStorageService.copyToDetainMedia(detainId, m.getFilePath());
        if (dest == null) {
            continue;
        }
        DetainMedia row = new DetainMedia();
        row.setDetainId(detainId);
        row.setBizType("SCENE");
        row.setFilePath(dest);
        row.setSortOrder(0);
        row.setUploadedBy(operatorUserId);
        detainMediaMapper.insert(row);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Expected: all `DetainedVehicleServiceTest` PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/DetainedVehicleService.java backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java
git commit -m "feat: copy order damage photos to detain scene on check-in"
```

---

### Task 4: 移动端时间覆盖与预览 URL 纯函数

**Files:**
- Modify: `mobile-rescuer/src/utils/plateOrder.js`
- Modify: `mobile-rescuer/src/utils/plateOrder.test.cjs`

**Interfaces:**
- Consumes: 无网络
- Produces:  
  `formatCheckedInAt(raw) -> string`（`yyyy-MM-dd HH:mm:ss`；不能解析则 `''`）  
  `applyCheckedInAt(form, order)`：仅当 `order.checkedInAt` 能格式化时覆盖 `form.rescueTime`  
  `damagePreviewUrls(paths, toUrl)`：过滤空 path，用 `toUrl(path)` 生成 URL 列表  
  `resetRescueTimeNow(form, nowText)`：`form.rescueTime = nowText`

- [ ] **Step 1: Write failing tests** in `plateOrder.test.cjs` (import new names):

```javascript
test('applyCheckedInAt overwrites only when checked in', () => {
  const form = { rescueTime: '2026-09-11 10:00:00' }
  applyCheckedInAt(form, {})
  assert.equal(form.rescueTime, '2026-09-11 10:00:00')
  applyCheckedInAt(form, { checkedInAt: '2026-09-09T16:38:15' })
  assert.equal(form.rescueTime, '2026-09-09 16:38:15')
})

test('damagePreviewUrls maps paths', () => {
  const urls = damagePreviewUrls(['dispatch/3/a.jpg', '', null], (p) => `/uploads/${p}`)
  assert.deepEqual(urls, ['/uploads/dispatch/3/a.jpg'])
})

test('resetRescueTimeNow writes provided now', () => {
  const form = { rescueTime: 'old' }
  resetRescueTimeNow(form, '2026-09-11 11:00:00')
  assert.equal(form.rescueTime, '2026-09-11 11:00:00')
})
```

- [ ] **Step 2: Run to verify fail**

```powershell
cd d:\cursorworkspace\mobile-rescuer
npm run test:workspace
```

Expected: FAIL missing exports.

- [ ] **Step 3: Implement in `plateOrder.js`**

```javascript
function pad2(n) {
  return String(n).padStart(2, '0')
}

function formatCheckedInAt(raw) {
  if (raw == null || raw === '') return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

function applyCheckedInAt(form, order) {
  if (!form || !order) return
  const text = formatCheckedInAt(order.checkedInAt)
  if (text) form.rescueTime = text
}

function damagePreviewUrls(paths, toUrl) {
  const list = paths || []
  const out = []
  for (const p of list) {
    if (!p) continue
    out.push(toUrl(p))
  }
  return out
}

function resetRescueTimeNow(form, nowText) {
  if (!form) return
  form.rescueTime = nowText
}
```

Export the four new names on `module.exports`.

- [ ] **Step 4: Run tests to verify they pass**

`npm run test:workspace` — all previous + new PASS.

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/utils/plateOrder.js mobile-rescuer/src/utils/plateOrder.test.cjs
git commit -m "feat: apply order check-in time and damage preview urls"
```

---

### Task 5: 入库页预览工单受损照并覆盖施救时间

**Files:**
- Modify: `mobile-rescuer/src/pages/detain/checkin.vue`

**Interfaces:**
- Consumes: `applyCheckedInAt`、`damagePreviewUrls`、`resetRescueTimeNow`、`mediaUrl`、现有 `bindOrder` / `clearLink`
- Produces: 第二步现场区先展示工单预览图（只读），再展示本地 `scenePhotos` 与上传按钮；`bindOrder` 覆盖签到时间并填充预览；`clearLink` 清预览并 `resetRescueTimeNow(form, formatNow())`，不改 `scenePhotos`；提交不把预览图当 `uploadDetainMedia` 本地路径

- [ ] **Step 1: Wire imports and state**

Import `mediaUrl` from `../../utils/request.js`.  
Import `applyCheckedInAt`, `damagePreviewUrls`, `resetRescueTimeNow` from `plateOrder.js`.

```javascript
const orderSceneUrls = ref([])
```

- [ ] **Step 2: Update bind/clear**

```javascript
function bindOrder(order) {
  linkedOrder.value = order
  pickerVisible.value = false
  applyEmptyOrderFields(form, order)
  applyCheckedInAt(form, order)
  orderSceneUrls.value = damagePreviewUrls(order.damagePhotoPaths || [], mediaUrl)
}

function clearLink() {
  searchSeq++
  linkedOrder.value = null
  pickerVisible.value = false
  orderSceneUrls.value = []
  resetRescueTimeNow(form, formatNow())
}
```

Keep `onPlateInput` calling `clearLink()` as today.

- [ ] **Step 3: Template — 现场照片 field**

Replace the existing `v-for="(p, i) in scenePhotos"` block so order previews come first:

```html
        <view class="field">
          <view class="field-label">现场照片</view>
          <view class="media-grid">
            <image
              v-for="(p, i) in orderSceneUrls"
              :key="'o' + i"
              class="thumb"
              :src="p"
              mode="aspectFill"
              @click="previewLocal(orderSceneUrls, i)"
            />
            <image
              v-for="(p, i) in scenePhotos"
              :key="'s' + i"
              class="thumb"
              :src="p"
              mode="aspectFill"
              @click="previewLocal(scenePhotos, i)"
            />
            <view class="thumb add" @click="pickPhoto('scene')">点击上传</view>
          </view>
        </view>
```

Do **not** push order URLs into `scenePhotos`. `onSubmit` must still only `uploadDetainMedia` for `scenePhotos` (local temp paths).

- [ ] **Step 4: Run unit tests**

```powershell
cd d:\cursorworkspace\mobile-rescuer
npm run test:workspace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/pages/detain/checkin.vue
git commit -m "feat: preview order damage photos and sync rescue time on check-in"
```

Manual smoke (if servers up): `parkingadmin` 入库，绑一张已签到且有 DAMAGE 的工单 → 第二步时间与预览图正确 → 提交后详情能看到 SCENE；取消关联时间回到现在、预览消失、本地拍的还在。

---

## Spec coverage

| Spec | Task |
|------|------|
| 摘要 `checkedInAt` / `damagePhotoPaths` | 2 |
| 未签到 null、不含 PARK | 2 |
| 复制 DAMAGE→SCENE、跳过缺失、无工单不复制 | 1+3 |
| 有签到才覆盖时间 | 4+5 |
| 取消关联清预览、时间回到 now、本地图保留 | 4+5 |
| 前端不上传工单图 | 5 |
| 不改 PC / 不新权限 | 全局 |

## Placeholder scan

无 TBD。方法名：`copyToDetainMedia`、`copyDamageToScene`、`applyCheckedInAt`、`damagePreviewUrls`、`resetRescueTimeNow`、`orderSceneUrls`。
