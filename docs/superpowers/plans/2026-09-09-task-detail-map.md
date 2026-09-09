# 任务详情地图（事故点 + 本单车辆）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理端非 PENDING 工单详情与移动端任务详情展示高德地图，标注事故点与本单绑定车辆当前位置，约 20 秒轮询刷新。

**Architecture:** 管理端扩展 `DispatchDetail`：PENDING 保留 nearby 选派；其它状态用 `getVehicle(vehicleId)` 跟踪本单车。后端在 `RescuerTaskDetail` 增加 `assignedVehicle` 快照，供移动端免 `vehicle:query`。移动端 H5 嵌入高德，约 20s 重拉 `getTask`。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、Vue 3、高德 JS API 2.0、uni-app H5

**Spec:** `docs/superpowers/specs/2026-09-09-task-detail-map-design.md`

## Global Constraints

- LOCATION 跟踪轮询间隔约 **20 秒**（与现有 nearby 一致）
- PENDING 管理端 nearby + 选派地图行为**不变**
- 已派后仅标**本单** `vehicleId` 车；无轨迹 / WS / 导航
- 移动端**不**直调 `/api/vehicle/{id}`；靠 `assignedVehicle` enrichment
- 无新权限码；施救员仍无 `vehicle:query`
- 黄卡车图标：管理端复用 `createVehicleMapIcon`；移动端视觉尽量一致
- 无 `VITE_AMAP_KEY`：友好占位，不白屏
- 本轮**不强制**按 180s 隐藏跟踪车点（可标库内最新坐标）

## File Structure

### Backend
- Create: `backend/src/main/java/com/example/backend/dto/AssignedVehicleSnapshot.java`
- Modify: `backend/src/main/java/com/example/backend/dto/RescuerTaskDetail.java`
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Modify: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

### Frontend
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

### Mobile
- Create: `mobile-rescuer/src/utils/amap.js`
- Modify: `mobile-rescuer/.env.example`
- Modify: `mobile-rescuer/src/pages/task/detail.vue`

---

### Task 1: 后端 `assignedVehicle` enrichment

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/AssignedVehicleSnapshot.java`
- Modify: `backend/src/main/java/com/example/backend/dto/RescuerTaskDetail.java`
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Modify: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

**Interfaces:**
- Consumes: `RescueVehicleMapper.findById(Long)`；`DispatchOrder.vehicleId`
- Produces: `RescuerTaskDetail.assignedVehicle` → `AssignedVehicleSnapshot`（`id`, `plateNo`, `longitude`, `latitude`, `locationUpdatedAt`）；`getTask` 在 `order.vehicleId != null` 时填充，车不存在则为 `null`

- [ ] **Step 1: Write the failing tests**

在 `RescuerMobileServiceTest` 增加：

```java
@Test
void getTaskFillsAssignedVehicleWhenOrderHasVehicleId() {
    DispatchOrder order = new DispatchOrder();
    order.setId(1L);
    order.setRescuerId(9L);
    order.setStatus("DISPATCHED");
    order.setVehicleId(7L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(order);
    when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);

    RescueVehicle vehicle = new RescueVehicle();
    vehicle.setId(7L);
    vehicle.setPlateNo("粤B救援1");
    vehicle.setLongitude(new BigDecimal("114.05"));
    vehicle.setLatitude(new BigDecimal("22.54"));
    vehicle.setLocationUpdatedAt(java.time.LocalDateTime.of(2026, 9, 9, 10, 0));
    when(rescueVehicleMapper.findById(7L)).thenReturn(vehicle);

    var detail = service.getTask(9L, 1L);

    assertNotNull(detail.getAssignedVehicle());
    assertEquals(7L, detail.getAssignedVehicle().getId());
    assertEquals("粤B救援1", detail.getAssignedVehicle().getPlateNo());
    assertEquals(0, new BigDecimal("114.05").compareTo(detail.getAssignedVehicle().getLongitude()));
    assertEquals(0, new BigDecimal("22.54").compareTo(detail.getAssignedVehicle().getLatitude()));
    assertNotNull(detail.getAssignedVehicle().getLocationUpdatedAt());
}

@Test
void getTaskAssignedVehicleNullWhenNoVehicleId() {
    DispatchOrder order = new DispatchOrder();
    order.setId(1L);
    order.setRescuerId(9L);
    order.setStatus("DISPATCHED");
    order.setVehicleId(null);
    when(dispatchOrderMapper.findById(1L)).thenReturn(order);
    when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);

    var detail = service.getTask(9L, 1L);

    assertNull(detail.getAssignedVehicle());
    verify(rescueVehicleMapper, never()).findById(any());
}

@Test
void getTaskAssignedVehicleNullWhenVehicleMissing() {
    DispatchOrder order = new DispatchOrder();
    order.setId(1L);
    order.setRescuerId(9L);
    order.setStatus("ACCEPTED");
    order.setVehicleId(99L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(order);
    when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);
    when(rescueVehicleMapper.findById(99L)).thenReturn(null);

    var detail = service.getTask(9L, 1L);

    assertNull(detail.getAssignedVehicle());
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
cd backend && ..\.tools\apache-maven\bin\mvn.cmd test "-Dtest=RescuerMobileServiceTest#getTaskFillsAssignedVehicleWhenOrderHasVehicleId"
```

Expected: compile error or assertion fail（`getAssignedVehicle` 尚不存在）。

- [ ] **Step 3: Create `AssignedVehicleSnapshot`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AssignedVehicleSnapshot {
    private Long id;
    private String plateNo;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime locationUpdatedAt;
}
```

- [ ] **Step 4: Extend `RescuerTaskDetail`**

```java
private AssignedVehicleSnapshot assignedVehicle;
```

- [ ] **Step 5: Fill in `getTask`**

```java
public RescuerTaskDetail getTask(Long userId, Long orderId) {
    DispatchOrder order = requireOwned(orderId, userId);
    RescuerTaskDetail detail = new RescuerTaskDetail();
    detail.setOrder(order);
    detail.setFieldRecord(fieldRecordMapper.findByOrderId(orderId));
    if (order.getVehicleId() != null) {
        RescueVehicle vehicle = rescueVehicleMapper.findById(order.getVehicleId());
        if (vehicle != null) {
            AssignedVehicleSnapshot snap = new AssignedVehicleSnapshot();
            snap.setId(vehicle.getId());
            snap.setPlateNo(vehicle.getPlateNo());
            snap.setLongitude(vehicle.getLongitude());
            snap.setLatitude(vehicle.getLatitude());
            snap.setLocationUpdatedAt(vehicle.getLocationUpdatedAt());
            detail.setAssignedVehicle(snap);
        }
    }
    return detail;
}
```

- [ ] **Step 6: Run tests — expect PASS**

```bash
cd backend && ..\.tools\apache-maven\bin\mvn.cmd test "-Dtest=RescuerMobileServiceTest"
```

Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/AssignedVehicleSnapshot.java \
  backend/src/main/java/com/example/backend/dto/RescuerTaskDetail.java \
  backend/src/main/java/com/example/backend/service/RescuerMobileService.java \
  backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java
git commit -m "feat(backend): enrich rescuer task detail with assigned vehicle snapshot"
```

---

### Task 2: 管理端 `DispatchDetail` 跟踪地图

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

**Interfaces:**
- Consumes: `getVehicle(id)` from `frontend/src/api/vehicle.js`；`createVehicleMapIcon` / `loadAmap` / `hasAmapKey`
- Produces: 非 PENDING 且 `hasCoords && amapReady` 时展示「位置跟踪」面板；事故点 + 本单车黄卡车标；约 20s 轮询 `getVehicle`；与 nearby 轮询互斥

- [ ] **Step 1: 模板 — 非 PENDING 增加跟踪地图面板**

在 `DISPATCHED / ACCEPTED` 的 `action-panel` **之前**（或同级上方），以及 `COMPLETED / ABORTED` 只读区上方，用统一条件渲染跟踪地图（避免只覆盖两种状态）：

在 `</template>`（PENDING 块结束）之后、现有 `v-else-if="DISPATCHED || ACCEPTED"` 之前插入：

```vue
      <!-- Non-PENDING: location tracking map -->
      <div
        v-if="order.status !== 'PENDING' && (canShowMap || hasCoords || !amapReady)"
        class="panel map-panel track-map-panel"
      >
        <h2 class="section-title">位置跟踪</h2>
        <div v-if="canShowMap" ref="trackMapEl" class="map-box" />
        <div v-else class="map-placeholder">
          <p v-if="!hasCoords">工单缺少坐标，无法在地图上展示。</p>
          <p v-else-if="!amapReady">未配置 VITE_AMAP_KEY，地图不可用。</p>
        </div>
        <p v-if="trackHint" class="hint-inline">{{ trackHint }}</p>
        <p v-if="trackPollHint" class="hint-inline">{{ trackPollHint }}</p>
        <p v-if="mapError" class="error">{{ mapError }}</p>
      </div>
```

保持 PENDING 的 `ref="mapEl"` 不变；跟踪模式用独立 `ref="trackMapEl"`，避免与选派地图 DOM 冲突。

- [ ] **Step 2: script — import 与状态**

确保已有：

```js
import { nearbyVehicles, listVehicles, getVehicle } from '../../api/vehicle'
```

新增 refs / 变量：

```js
const trackMapEl = ref(null)
const trackHint = ref('')
const trackPollHint = ref('')
const trackedVehicle = ref(null) // { id, plateNo, longitude, latitude, ... }
let trackPollTimer = null
let trackVehicleMarker = null
```

- [ ] **Step 3: 加载 / 轮询本单车**

```js
async function loadTrackedVehicle({ silent = false } = {}) {
  trackPollHint.value = ''
  const vehicleId = order.value?.vehicleId
  if (!vehicleId) {
    trackedVehicle.value = null
    trackHint.value = '本单未绑定车辆'
    syncTrackMarkers()
    return
  }
  try {
    const res = await getVehicle(vehicleId)
    trackedVehicle.value = res.data || null
    const v = trackedVehicle.value
    if (v && v.longitude != null && v.latitude != null) {
      trackHint.value = v.plateNo ? `施救车辆：${v.plateNo}` : ''
    } else {
      trackHint.value = '车辆暂无位置'
    }
    syncTrackMarkers()
  } catch (e) {
    if (silent) {
      trackPollHint.value = e.response?.data?.message || e.message || '刷新车辆位置失败'
    } else {
      trackHint.value = e.response?.data?.message || e.message || '加载车辆位置失败'
      trackedVehicle.value = null
    }
  }
}

function startTrackPoll() {
  stopTrackPoll()
  if (order.value?.status === 'PENDING' || !order.value?.vehicleId) return
  trackPollTimer = setInterval(() => {
    loadTrackedVehicle({ silent: true })
  }, 20000)
}

function stopTrackPoll() {
  if (trackPollTimer) {
    clearInterval(trackPollTimer)
    trackPollTimer = null
  }
}
```

- [ ] **Step 4: 跟踪地图 init / markers / destroy**

```js
function clearTrackVehicleMarker() {
  if (trackVehicleMarker && typeof trackVehicleMarker.setMap === 'function') {
    trackVehicleMarker.setMap(null)
  }
  trackVehicleMarker = null
}

function syncTrackMarkers() {
  if (!mapInstance || !window.AMap) return
  clearTrackVehicleMarker()
  const v = trackedVehicle.value
  if (v?.longitude == null || v?.latitude == null) return
  const AMap = window.AMap
  const icon = createVehicleMapIcon(AMap)
  trackVehicleMarker = new AMap.Marker({
    position: [Number(v.longitude), Number(v.latitude)],
    map: mapInstance,
    icon,
    offset: new AMap.Pixel(-22, -18),
    title: v.plateNo || '',
    label: {
      content: v.plateNo || '施救车',
      direction: 'top',
      offset: new AMap.Pixel(0, -4)
    }
  })
  const accident = [Number(order.value.longitude), Number(order.value.latitude)]
  mapInstance.setFitView(
    [new AMap.Marker({ position: accident }), trackVehicleMarker],
    false,
    [60, 60, 60, 60]
  )
}

async function initTrackMap() {
  destroyMap()
  mapError.value = ''
  if (!canShowMap.value || !trackMapEl.value) return
  try {
    const AMap = await loadAmap()
    const lng = Number(order.value.longitude)
    const lat = Number(order.value.latitude)
    mapInstance = new AMap.Map(trackMapEl.value, {
      zoom: 14,
      center: [lng, lat]
    })
    new AMap.Marker({ position: [lng, lat], map: mapInstance })
    syncTrackMarkers()
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}
```

更新 `destroyMap`：在清 nearby markers 时同时 `clearTrackVehicleMarker()`。

- [ ] **Step 5: 统一 status / route watchers**

将 status watcher 改为：

```js
watch(
  () => order.value?.status,
  async (status) => {
    destroyMap()
    stopNearbyPoll()
    stopTrackPoll()
    trackedVehicle.value = null
    trackHint.value = ''
    trackPollHint.value = ''
    if (!status) return
    if (status === 'PENDING') {
      await loadNearby()
      await nextTick()
      await initMap()
      startNearbyPoll()
    } else {
      await loadTrackedVehicle()
      await nextTick()
      await initTrackMap()
      startTrackPoll()
    }
  }
)
```

route.params.id watcher 同理：清两侧轮询；若 PENDING → nearby；否则 → track。

`onBeforeUnmount`：`stopNearbyPoll(); stopTrackPoll(); destroyMap()`。

- [ ] **Step 6: 手动核对（无自动化 UI 测试）**

- PENDING：附近车选派图与轮询仍正常
- DISPATCHED/ACCEPTED：见「位置跟踪」、事故点 + 黄卡车；无坐标时文案「车辆暂无位置」
- 离开页后无残留 interval（DevTools 或切换工单观察）

- [ ] **Step 7: Commit**

```bash
git add frontend/src/views/dispatch/DispatchDetail.vue
git commit -m "feat(frontend): show accident and assigned vehicle on non-pending dispatch detail map"
```

---

### Task 3: 移动端任务详情地图

**Files:**
- Create: `mobile-rescuer/src/utils/amap.js`
- Modify: `mobile-rescuer/.env.example`
- Modify: `mobile-rescuer/src/pages/task/detail.vue`
- Optional local only (勿提交密钥若已在 `.gitignore`): `mobile-rescuer/.env` 增加 `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE`（与管理端同 Key 或独立 Key）

**Interfaces:**
- Consumes: `getTask` → `order.longitude/latitude` + `assignedVehicle`
- Produces: H5 高德地图标事故点 + 本单车；有车坐标时约 20s 重拉详情；`onHide`/`onUnload` 清定时器

- [ ] **Step 1: `.env.example` 增加 Key 说明**

```env
# 高德 JS API Key（H5 任务详情地图；无 Key 时显示占位文案）
# VITE_AMAP_KEY=
# VITE_AMAP_SECURITY_CODE=
```

- [ ] **Step 2: 创建 `mobile-rescuer/src/utils/amap.js`**

精简移植管理端加载逻辑 + 黄卡车 icon（可从 `frontend/src/utils/amap.js` 复制 `VEHICLE_ICON_*` 与 `createVehicleMapIcon` / `hasAmapKey` / `loadAmap`；**不要**复制 picker/district 地图工厂）。

最小导出：

```js
export function hasAmapKey() { /* Boolean(import.meta.env.VITE_AMAP_KEY) */ }
export function loadAmap() { /* script tag → window.AMap；支持 VITE_AMAP_SECURITY_CODE */ }
export function createVehicleMapIcon(AMap) { /* 同管理端黄卡车 */ }
```

- [ ] **Step 3: `detail.vue` 模板增加地图块**

在首个 `card`（工单信息）之后：

```vue
    <view class="card map-card">
      <view class="section-title">位置</view>
      <view v-if="canShowMap" class="map-box" id="task-detail-map" />
      <view v-else class="map-placeholder">
        <text v-if="!hasAccidentCoords">暂无事故坐标，无法展示地图</text>
        <text v-else-if="!amapReady">未配置地图 Key</text>
      </view>
      <view v-if="mapHint" class="line muted">{{ mapHint }}</view>
      <view v-if="mapError" class="line error">{{ mapError }}</view>
    </view>
```

- [ ] **Step 4: script — 状态、加载、轮询、地图**

```js
import { ref, computed, nextTick, getCurrentInstance } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { hasAmapKey, loadAmap, createVehicleMapIcon } from '../../utils/amap'
// ... existing imports

const assignedVehicle = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
const mapHint = ref('')
let mapInstance = null
let vehicleMarker = null
let pollTimer = null

const hasAccidentCoords = computed(() =>
  order.value?.longitude != null && order.value?.latitude != null
)
const canShowMap = computed(() => amapReady && hasAccidentCoords.value)

async function load() {
  try {
    const res = await getTask(id.value)
    order.value = res.data?.order || null
    fieldRecord.value = res.data?.fieldRecord || null
    assignedVehicle.value = res.data?.assignedVehicle || null
    updateMapHint()
    await nextTick()
    await ensureMap()
    syncVehicleMarker()
  } catch (_) {
    order.value = null
  }
}

function updateMapHint() {
  const v = assignedVehicle.value
  if (!v) {
    mapHint.value = order.value?.vehicleId ? '车辆信息暂不可用' : ''
    return
  }
  if (v.longitude == null || v.latitude == null) {
    mapHint.value = '车辆暂无位置'
  } else {
    mapHint.value = v.plateNo ? `施救车辆：${v.plateNo}` : ''
  }
}

async function ensureMap() {
  mapError.value = ''
  if (!canShowMap.value) {
    destroyMap()
    return
  }
  const el = document.getElementById('task-detail-map')
  if (!el) return
  try {
    const AMap = await loadAmap()
    if (!mapInstance) {
      const lng = Number(order.value.longitude)
      const lat = Number(order.value.latitude)
      mapInstance = new AMap.Map(el, { zoom: 14, center: [lng, lat] })
      new AMap.Marker({ position: [lng, lat], map: mapInstance })
    }
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

function syncVehicleMarker() {
  if (!mapInstance || !window.AMap) return
  if (vehicleMarker) {
    vehicleMarker.setMap(null)
    vehicleMarker = null
  }
  const v = assignedVehicle.value
  if (v?.longitude == null || v?.latitude == null) return
  const AMap = window.AMap
  const icon = createVehicleMapIcon(AMap)
  vehicleMarker = new AMap.Marker({
    position: [Number(v.longitude), Number(v.latitude)],
    map: mapInstance,
    icon,
    offset: new AMap.Pixel(-22, -18),
    title: v.plateNo || ''
  })
  const accident = [Number(order.value.longitude), Number(order.value.latitude)]
  mapInstance.setFitView(
    [new AMap.Marker({ position: accident }), vehicleMarker],
    false,
    [40, 40, 40, 40]
  )
}

function destroyMap() {
  if (vehicleMarker) {
    vehicleMarker.setMap(null)
    vehicleMarker = null
  }
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
}

function startPoll() {
  stopPoll()
  const v = assignedVehicle.value
  const hasVehicleCoords = v && v.longitude != null && v.latitude != null
  // Spec: poll when tracking vehicle location; also refresh if vehicleId exists so coords can appear later
  if (!order.value?.vehicleId && !hasVehicleCoords) return
  pollTimer = setInterval(() => {
    load()
  }, 20000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onShow(() => {
  if (id.value) {
    load().then(() => startPoll())
  }
})

onHide(() => {
  stopPoll()
})

onUnload(() => {
  stopPoll()
  destroyMap()
})
```

注意：`load()` 内不要无条件 `startPoll`（由 `onShow` 负责），避免重复 interval。H5 下使用 `document.getElementById`；若构建目标含非 H5，可用 `#ifdef H5` 包裹地图逻辑，非 H5 仅显示占位「请使用 H5 查看地图」。

- [ ] **Step 5: 样式**

```css
.map-box {
  width: 100%;
  height: 360rpx;
  margin-top: 16rpx;
  border-radius: 12rpx;
  overflow: hidden;
}
.map-placeholder {
  margin-top: 16rpx;
  color: #888;
  font-size: 26rpx;
}
.error {
  color: #c62828;
}
```

- [ ] **Step 6: 手动核对**

- 有事故坐标 + Key：见地图事故点；有 `assignedVehicle` 坐标见黄卡车
- 约 20s 后车点随上报更新（需施救员前台上报）
- 无 Key：占位文案，无控制台未捕获异常
- 离开详情页定时器停止

- [ ] **Step 7: Commit**

```bash
git add mobile-rescuer/src/utils/amap.js mobile-rescuer/.env.example mobile-rescuer/src/pages/task/detail.vue
git commit -m "feat(mobile): show accident and assigned vehicle map on task detail"
```

（勿将含真实 Key 的 `mobile-rescuer/.env` 提交，除非仓库已约定且无密钥泄露风险。）

---

## Spec coverage

| Spec 项 | Task |
|---------|------|
| PENDING 管理端选派图不变 | Task 2（保留原分支） |
| 非 PENDING 管理端事故点 + 本单车 + 20s | Task 2 |
| 本单车无坐标：仅事故点 +「车辆暂无位置」 | Task 2 / Task 3 |
| `RescuerTaskDetail.assignedVehicle` | Task 1 |
| 移动端地图 + 20s 重拉 | Task 3 |
| 无 `vehicle:query` 给施救员 | Task 1 enrichment |
| 无 Key 友好占位 | Task 2 / Task 3 |
| 不做轨迹/WS/导航/原生 map | 全任务 YAGNI |

## Self-review notes

- 类型名统一：`AssignedVehicleSnapshot`；JSON 字段 camelCase：`assignedVehicle`
- 管理端跟踪用 `trackMapEl`，与 PENDING `mapEl` 分离，避免 v-if 切换后 ref 错位
- 轮询互斥：`stopNearbyPoll` ↔ `stopTrackPoll`
- 移动端轮询在 `onShow` 启动、`onHide`/`onUnload` 停止
