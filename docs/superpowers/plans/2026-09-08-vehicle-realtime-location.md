# 施救车辆实时位置与就近派单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 施救员 H5 前台定时上报 GPS；调度派单详情按距事故点纯距离推荐有新鲜定位的空闲车，地图标注这些车，位置过期/未知车辆另段可派。

**Architecture:** `rescue_vehicle` 增加 `location_updated_at`；移动端 `POST /api/mobile/rescuer/location` 写坐标；`findNearby` 拆分 fresh/stale 并纯距离排序；`DispatchDetail.vue` 分段列表 + 地图车标 + 约 20s 轮询。不做轨迹 / WebSocket。

**Tech Stack:** Spring Boot 3.2、MyBatis 注解、Spring Security JWT、MySQL、JUnit5+Mockito、Vue 3、高德 JS API 2.0、uni-app H5

**Spec:** `docs/superpowers/specs/2026-09-08-vehicle-realtime-location-design.md`

## Global Constraints

- 新鲜度阈值：`LOCATION_STALE_SECONDS = 180`（3 分钟）
- 移动端上报间隔约 30 秒；仅 App 前台；仅更新当前用户绑定车
- 推荐排序：**纯距离升序**；`inMatchedDistrict` / `matchedDistrict` 仅展示
- 地图只标 `vehicles`（fresh IDLE）；`staleVehicles` 不上图、默认折叠仍可派
- 管理端 CRUD 更新坐标时**不**刷新 `location_updated_at`
- 权限 id：`67` = `rescuer:location`；授予 `TOW_DRIVER`（role_id=3）
- 统一 `Result`；业务冲突 `RuntimeException` → Controller `Result.error(message)`
- YAGNI：无轨迹表、无 WS、无新建工单页选车地图、不改派单状态机

## File Structure

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-08_vehicle_location.sql`

### Backend
- Modify: `entity/RescueVehicle.java` — `locationUpdatedAt`
- Modify: `mapper/RescueVehicleMapper.java` — `updateLocation`
- Modify: `dto/NearbyVehicleVO.java` — `locationFresh`、`locationUpdatedAt`；`distanceMeters` 改为 `Double`
- Modify: `dto/NearbyVehiclesResponse.java` — `staleVehicles`
- Create: `dto/LocationReportRequest.java`、`dto/LocationReportResponse.java`
- Modify: `service/RescueVehicleService.java` — fresh/stale + 纯距离
- Modify: `service/RescuerMobileService.java` — `reportLocation`
- Modify: `controller/RescuerMobileController.java` — `POST /location`
- Modify: `test/.../RescueVehicleServiceTest.java`
- Modify: `test/.../RescuerMobileServiceTest.java`

### Mobile
- Modify: `mobile-rescuer/src/api/rescuer.js` — `reportLocation`
- Create: `mobile-rescuer/src/utils/locationReporter.js`
- Modify: `mobile-rescuer/src/App.vue` — onShow/onHide 启停

### Frontend
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue` — 分段、地图标车、轮询

---

### Task 1: 数据库与权限种子

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-08_vehicle_location.sql`

**Interfaces:**
- Produces: 列 `rescue_vehicle.location_updated_at`；权限 id `67` `rescuer:location`；`TOW_DRIVER` 拥有该权限

- [ ] **Step 1: 修改 `init.sql` 中 `rescue_vehicle` DDL**

在 `latitude` 后增加：

```sql
  `location_updated_at` DATETIME DEFAULT NULL COMMENT '最近一次移动端 GPS 上报时间',
```

- [ ] **Step 2: 在 `init.sql` permission 插入末尾追加（id=67）**

在现有 `(66, '车型编辑', ...)` 后改为带逗号并追加：

```sql
(66, '车型编辑', 'vehicle-type:edit', 'BUTTON', 63, 3),
(67, '位置上报', 'rescuer:location', 'BUTTON', 53, 10);
```

- [ ] **Step 3: 调整 TOW_DRIVER 权限授予**

将：

```sql
SELECT 3, id FROM `permission` WHERE id BETWEEN 53 AND 62;
```

改为同时覆盖 67，例如：

```sql
SELECT 3, id FROM `permission` WHERE id BETWEEN 53 AND 62 OR id = 67;
```

- [ ] **Step 4: 创建 migrate 脚本**

`database/migrate_2026-09-08_vehicle_location.sql`：

```sql
-- 施救车辆实时位置
ALTER TABLE `rescue_vehicle`
  ADD COLUMN IF NOT EXISTS `location_updated_at` DATETIME DEFAULT NULL
    COMMENT '最近一次移动端 GPS 上报时间' AFTER `latitude`;

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 67 AS id, '位置上报' AS permission_name, 'rescuer:location' AS permission_code,
         'BUTTON' AS permission_type, 53 AS parent_id, 10 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 67 OR `permission_code` = 'rescuer:location');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, 67 FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 3 AND `permission_id` = 67
);
```

若目标 MySQL 版本不支持 `ADD COLUMN IF NOT EXISTS`，改为先检查信息_schema 或文档注明「列已存在则跳过手动执行」。本仓库既有 migrate 风格优先与 `migrate_2026-09-08_dispatch_plate_vehicle_type.sql` 一致（可复制其 `INSERT ... WHERE NOT EXISTS` 模式；ALTER 用普通 `ADD COLUMN`，执行前人工确认）。

推荐 migrate ALTER 写成：

```sql
ALTER TABLE `rescue_vehicle`
  ADD COLUMN `location_updated_at` DATETIME DEFAULT NULL
    COMMENT '最近一次移动端 GPS 上报时间' AFTER `latitude`;
```

（重复执行会失败属预期；一次性迁移。）

- [ ] **Step 5: Commit**

```bash
git add database/init.sql database/migrate_2026-09-08_vehicle_location.sql
git commit -m "chore(db): add vehicle location_updated_at and rescuer:location permission"
```

---

### Task 2: Entity / Mapper 位置更新

**Files:**
- Modify: `backend/src/main/java/com/example/backend/entity/RescueVehicle.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java`

**Interfaces:**
- Produces: `RescueVehicle.locationUpdatedAt: LocalDateTime`
- Produces: `RescueVehicleMapper.updateLocation(id, lng, lat, locationUpdatedAt) -> int`
- Note: 现有 `insert`/`update` **不**写入 `location_updated_at`（管理端改坐标保持未刷新）

- [ ] **Step 1: Entity 增加字段**

在 `latitude` 后：

```java
private LocalDateTime locationUpdatedAt;
```

- [ ] **Step 2: Mapper 增加专用更新**

```java
@Update("UPDATE rescue_vehicle SET longitude = #{longitude}, latitude = #{latitude}, " +
        "location_updated_at = #{locationUpdatedAt} WHERE id = #{id}")
int updateLocation(@Param("id") Long id,
                   @Param("longitude") BigDecimal longitude,
                   @Param("latitude") BigDecimal latitude,
                   @Param("locationUpdatedAt") LocalDateTime locationUpdatedAt);
```

（`import java.math.BigDecimal; import java.time.LocalDateTime;`）

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/RescueVehicle.java \
  backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java
git commit -m "feat(backend): map rescue vehicle location_updated_at and updateLocation"
```

---

### Task 3: nearby 新鲜度拆分与纯距离排序

**Files:**
- Modify: `backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java`
- Modify: `backend/src/main/java/com/example/backend/dto/NearbyVehiclesResponse.java`
- Modify: `backend/src/main/java/com/example/backend/service/RescueVehicleService.java`
- Modify: `backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java`

**Interfaces:**
- Consumes: `RescueVehicle.locationUpdatedAt`
- Produces: `NearbyVehiclesResponse.vehicles`（fresh）、`staleVehicles`；`NearbyVehicleVO.locationFresh`、`locationUpdatedAt`、`Double distanceMeters`
- Constant: `public static final int LOCATION_STALE_SECONDS = 180`（或 package-private 同名常量于 Service）

- [ ] **Step 1: 先改失败用例 — 片区不再优先**

将 `nearbyPrefersMatchedDistrictThenDistance` 改为纯距离期望（近的外区车应排第一）：

```java
@Test
void nearbySortsByDistanceOnlyIgnoringDistrict() {
    District matched = new District();
    matched.setId(1L);
    matched.setName("福田中心片区");
    matched.setCode("FT-CENTER");
    matched.setStatus("ENABLED");
    when(districtService.resolve(any(), any())).thenReturn(matched);

    RescueVehicle inDistrictFar = freshVehicle(10L, "粤B远本区", "114.100", "22.600", 1L);
    RescueVehicle otherNear = freshVehicle(11L, "粤B近外区", "114.058", "22.543", 2L);
    RescueVehicle inDistrictNear = freshVehicle(12L, "粤B近本区", "114.058", "22.544", 1L);
    when(vehicleMapper.findByStatus("IDLE"))
            .thenReturn(List.of(inDistrictFar, otherNear, inDistrictNear));

    NearbyVehiclesResponse resp = service.findNearby(
            new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

    assertEquals(1L, resp.getMatchedDistrict().getId());
    assertEquals(List.of(11L, 12L, 10L),
            resp.getVehicles().stream().map(v -> v.getVehicle().getId()).toList());
    assertTrue(resp.getVehicles().get(0).isLocationFresh());
    assertTrue(resp.getStaleVehicles() == null || resp.getStaleVehicles().isEmpty());
}
```

并增加 helper（与现有 `vehicle` 并存）：

```java
private static RescueVehicle freshVehicle(Long id, String plate, String lng, String lat, Long districtId) {
    RescueVehicle v = vehicle(id, plate, lng, lat);
    v.setDistrictId(districtId);
    v.setLocationUpdatedAt(java.time.LocalDateTime.now());
    return v;
}
```

同步修改 `nearbySortsIdleByDistanceAscending` / `nearbyUnmatchedMarksAllFalse`：测试车辆须设 `locationUpdatedAt = now()`，否则会进 stale。

- [ ] **Step 2: 增加 fresh/stale 用例**

```java
@Test
void nearbySplitsFreshAndStaleByLocationUpdatedAt() {
    when(districtService.resolve(any(), any())).thenReturn(null);
    RescueVehicle fresh = freshVehicle(1L, "粤B新", "114.058", "22.543", null);
    RescueVehicle stale = vehicle(2L, "粤B旧", "114.059", "22.544");
    stale.setLocationUpdatedAt(java.time.LocalDateTime.now().minusMinutes(10));
    RescueVehicle never = vehicle(3L, "粤B无", "114.060", "22.545");
    never.setLocationUpdatedAt(null);
    RescueVehicle noCoord = vehicle(4L, "粤B空", "114.061", "22.546");
    noCoord.setLongitude(null);
    noCoord.setLatitude(null);
    noCoord.setLocationUpdatedAt(java.time.LocalDateTime.now());
    when(vehicleMapper.findByStatus("IDLE"))
            .thenReturn(List.of(fresh, stale, never, noCoord));

    NearbyVehiclesResponse resp = service.findNearby(
            new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

    assertEquals(1, resp.getVehicles().size());
    assertEquals(1L, resp.getVehicles().get(0).getVehicle().getId());
    assertEquals(3, resp.getStaleVehicles().size());
    assertTrue(resp.getStaleVehicles().stream().noneMatch(NearbyVehicleVO::isLocationFresh));
}
```

（需 `import com.example.backend.dto.NearbyVehicleVO;`）

- [ ] **Step 3: Run tests — expect FAIL**

```bash
cd backend && mvn test -Dtest=RescueVehicleServiceTest
```

Expected: FAIL（旧排序断言 / 缺字段 / 未拆分 stale）

- [ ] **Step 4: 更新 DTO**

`NearbyVehicleVO.java`：

```java
@Data
public class NearbyVehicleVO {
    private RescueVehicle vehicle;
    private Double distanceMeters;
    private boolean inMatchedDistrict;
    private boolean locationFresh;
    private java.time.LocalDateTime locationUpdatedAt;
}
```

`NearbyVehiclesResponse.java`：

```java
@Data
public class NearbyVehiclesResponse {
    private MatchedDistrictVO matchedDistrict;
    private List<NearbyVehicleVO> vehicles;
    private List<NearbyVehicleVO> staleVehicles;
}
```

- [ ] **Step 5: 重写 `findNearby`**

替换 `RescueVehicleService.findNearby` 主体逻辑为：

```java
public static final int LOCATION_STALE_SECONDS = 180;

public NearbyVehiclesResponse findNearby(BigDecimal lng, BigDecimal lat, Integer limit) {
    int effectiveLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 50);
    double originLng = lng.doubleValue();
    double originLat = lat.doubleValue();

    District matched = districtService.resolve(lng, lat);
    Long matchedId = matched == null ? null : matched.getId();
    LocalDateTime freshAfter = LocalDateTime.now().minusSeconds(LOCATION_STALE_SECONDS);

    List<NearbyVehicleVO> fresh = new ArrayList<>();
    List<NearbyVehicleVO> stale = new ArrayList<>();

    for (RescueVehicle v : vehicleMapper.findByStatus("IDLE")) {
        NearbyVehicleVO vo = toNearbyVo(v, originLng, originLat, matchedId, freshAfter);
        if (vo.isLocationFresh()) {
            fresh.add(vo);
        } else {
            stale.add(vo);
        }
    }

    fresh.sort(Comparator.comparingDouble(x -> x.getDistanceMeters()));
    if (fresh.size() > effectiveLimit) {
        fresh = new ArrayList<>(fresh.subList(0, effectiveLimit));
    }

    NearbyVehiclesResponse resp = new NearbyVehiclesResponse();
    resp.setMatchedDistrict(MatchedDistrictVO.from(matched));
    resp.setVehicles(fresh);
    resp.setStaleVehicles(stale);
    return resp;
}

private NearbyVehicleVO toNearbyVo(RescueVehicle v, double originLng, double originLat,
                                   Long matchedId, LocalDateTime freshAfter) {
    NearbyVehicleVO vo = new NearbyVehicleVO();
    vo.setVehicle(v);
    vo.setInMatchedDistrict(matchedId != null && matchedId.equals(v.getDistrictId()));
    vo.setLocationUpdatedAt(v.getLocationUpdatedAt());

    boolean hasCoords = v.getLongitude() != null && v.getLatitude() != null;
    boolean fresh = hasCoords
            && v.getLocationUpdatedAt() != null
            && !v.getLocationUpdatedAt().isBefore(freshAfter);
    vo.setLocationFresh(fresh);

    if (hasCoords) {
        vo.setDistanceMeters(haversineMeters(
                originLng, originLat,
                v.getLongitude().doubleValue(), v.getLatitude().doubleValue()));
    } else {
        vo.setDistanceMeters(null);
    }
    return vo;
}
```

补充 imports：`ArrayList`、`LocalDateTime`。删除旧的「filter 无坐标 + 片区优先 comparator」逻辑。

注意：fresh 列表中 `distanceMeters` 必非 null（因 fresh 要求有坐标）；`comparingDouble` 安全。

- [ ] **Step 6: Run tests — expect PASS**

```bash
cd backend && mvn test -Dtest=RescueVehicleServiceTest
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java \
  backend/src/main/java/com/example/backend/dto/NearbyVehiclesResponse.java \
  backend/src/main/java/com/example/backend/service/RescueVehicleService.java \
  backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java
git commit -m "feat(backend): nearby by fresh distance with staleVehicles segment"
```

---

### Task 4: 移动端位置上报 API

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/LocationReportRequest.java`
- Create: `backend/src/main/java/com/example/backend/dto/LocationReportResponse.java`
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/RescuerMobileController.java`
- Modify: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

**Interfaces:**
- Consumes: `RescueVehicleMapper.findByDriverUserId`、`updateLocation`
- Produces: `RescuerMobileService.reportLocation(userId, LocationReportRequest) -> LocationReportResponse`
- HTTP: `POST /api/mobile/rescuer/location`，权限 `rescuer:location`

- [ ] **Step 1: 写失败测试**

在 `RescuerMobileServiceTest` 追加：

```java
@Test
void reportLocationFailsWhenNotBound() {
    when(rescueVehicleMapper.findByDriverUserId(9L)).thenReturn(null);
    LocationReportRequest req = new LocationReportRequest();
    req.setLng(new BigDecimal("114.05"));
    req.setLat(new BigDecimal("22.54"));
    RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.reportLocation(9L, req));
    assertTrue(ex.getMessage().contains("绑定"));
    verify(rescueVehicleMapper, never()).updateLocation(any(), any(), any(), any());
}

@Test
void reportLocationUpdatesBoundVehicle() {
    RescueVehicle bound = new RescueVehicle();
    bound.setId(7L);
    when(rescueVehicleMapper.findByDriverUserId(9L)).thenReturn(bound);
    when(rescueVehicleMapper.updateLocation(eq(7L), any(), any(), any())).thenReturn(1);

    LocationReportRequest req = new LocationReportRequest();
    req.setLng(new BigDecimal("114.057868"));
    req.setLat(new BigDecimal("22.543099"));
    LocationReportResponse resp = service.reportLocation(9L, req);

    assertEquals(7L, resp.getVehicleId());
    assertNotNull(resp.getLocationUpdatedAt());
    verify(rescueVehicleMapper).updateLocation(eq(7L),
            eq(req.getLng()), eq(req.getLat()), any());
}
```

imports：`LocationReportRequest`、`LocationReportResponse`、`BigDecimal`。

- [ ] **Step 2: Run — expect FAIL**

```bash
cd backend && mvn test -Dtest=RescuerMobileServiceTest
```

Expected: 编译失败或方法不存在

- [ ] **Step 3: 实现 DTO**

```java
// LocationReportRequest.java
package com.example.backend.dto;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class LocationReportRequest {
    private BigDecimal lng;
    private BigDecimal lat;
    private BigDecimal accuracy; // optional, ignored by service if unused
}
```

```java
// LocationReportResponse.java
package com.example.backend.dto;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class LocationReportResponse {
    private Long vehicleId;
    private LocalDateTime locationUpdatedAt;
}
```

- [ ] **Step 4: Service 方法**

在 `RescuerMobileService`：

```java
@Transactional
public LocationReportResponse reportLocation(Long userId, LocationReportRequest request) {
    if (request == null || request.getLng() == null || request.getLat() == null) {
        throw new RuntimeException("经纬度不能为空");
    }
    double lng = request.getLng().doubleValue();
    double lat = request.getLat().doubleValue();
    if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
        throw new RuntimeException("经纬度无效");
    }
    RescueVehicle vehicle = rescueVehicleMapper.findByDriverUserId(userId);
    if (vehicle == null) {
        throw new RuntimeException("请先绑定车辆");
    }
    LocalDateTime now = LocalDateTime.now();
    rescueVehicleMapper.updateLocation(vehicle.getId(), request.getLng(), request.getLat(), now);
    LocationReportResponse resp = new LocationReportResponse();
    resp.setVehicleId(vehicle.getId());
    resp.setLocationUpdatedAt(now);
    return resp;
}
```

- [ ] **Step 5: Controller**

```java
@PostMapping("/location")
@PreAuthorize("hasAuthority('rescuer:location')")
public Result<LocationReportResponse> reportLocation(@RequestBody LocationReportRequest request) {
    try {
        return Result.success(rescuerMobileService.reportLocation(currentUserId(), request));
    } catch (RuntimeException e) {
        return Result.error(e.getMessage());
    }
}
```

- [ ] **Step 6: Run tests — PASS**

```bash
cd backend && mvn test -Dtest=RescuerMobileServiceTest,RescueVehicleServiceTest
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/LocationReportRequest.java \
  backend/src/main/java/com/example/backend/dto/LocationReportResponse.java \
  backend/src/main/java/com/example/backend/service/RescuerMobileService.java \
  backend/src/main/java/com/example/backend/controller/RescuerMobileController.java \
  backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java
git commit -m "feat(backend): rescuer location report API"
```

---

### Task 5: 移动端 H5 定时上报

**Files:**
- Modify: `mobile-rescuer/src/api/rescuer.js`
- Create: `mobile-rescuer/src/utils/locationReporter.js`
- Modify: `mobile-rescuer/src/App.vue`

**Interfaces:**
- Consumes: `POST /mobile/rescuer/location`；`getBoundVehicle`
- Produces: `startLocationReporter()` / `stopLocationReporter()`；间隔 30000ms

- [ ] **Step 1: API**

在 `rescuer.js` 追加：

```js
export function reportLocation(lng, lat, accuracy) {
  const data = { lng, lat }
  if (accuracy != null) data.accuracy = accuracy
  return request({ url: `${PREFIX}/location`, method: 'POST', data })
}
```

- [ ] **Step 2: 创建 `locationReporter.js`**

```js
import { getUserState } from '../stores/user'
import { getBoundVehicle, reportLocation } from '../api/rescuer'

const INTERVAL_MS = 30000
let timer = null
let running = false
let unboundNotified = false

function getLocationOnce() {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      success: (res) => resolve(res),
      fail: (err) => reject(err)
    })
  })
}

async function tick() {
  const { token } = getUserState()
  if (!token) {
    stopLocationReporter()
    return
  }
  try {
    const bound = await getBoundVehicle()
    if (!bound || !bound.data || !bound.data.id) {
      if (!unboundNotified) {
        unboundNotified = true
        // 非阻塞：控制台即可，避免频繁 toast
        console.warn('[locationReporter] 未绑定车辆，跳过上报')
      }
      return
    }
    unboundNotified = false
    const loc = await getLocationOnce()
    await reportLocation(loc.longitude, loc.latitude, loc.accuracy)
  } catch (e) {
    const msg = e?.message || e?.errMsg || String(e)
    if (typeof msg === 'string' && msg.includes('绑定')) {
      stopLocationReporter()
    }
    // 定位失败静默
  }
}

export function startLocationReporter() {
  if (running) return
  const { token } = getUserState()
  if (!token) return
  running = true
  tick()
  timer = setInterval(tick, INTERVAL_MS)
}

export function stopLocationReporter() {
  running = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}
```

按项目 `request` 实际返回形状微调：若 `request` 已解包为 `data` 本体，则 `bound.id` 而非 `bound.data.id`。对照 `mine/index.vue` 或 `bind.vue` 对 `getBoundVehicle` 的用法保持一致。

- [ ] **Step 3: App.vue 启停**

将 `App.vue` script 改为（保留原 onLaunch）：

```vue
<script>
import { startLocationReporter, stopLocationReporter } from './utils/locationReporter'

export default {
  onLaunch() {
    const token = uni.getStorageSync('token')
    const pages = getCurrentPages()
    const route = pages.length ? pages[pages.length - 1].route : ''
    if (!token && route !== 'pages/login/index') {
      uni.reLaunch({ url: '/pages/login/index' })
    }
  },
  onShow() {
    startLocationReporter()
  },
  onHide() {
    stopLocationReporter()
  }
}
</script>
```

登录成功后若停留在登录页不会持续报；进入主包页后 `onShow` 会启动。退出登录应 `stopLocationReporter`——在 `stores/user.js` 的 `logout` 中调用 `stopLocationReporter()`（动态 import 或顶层 import）。

- [ ] **Step 4: 手工冒烟**

1. 对已有库执行 migrate（或重建 init）
2. 施救员重新登录（刷新 JWT 含 `rescuer:location`）
3. 绑车后保持 H5 前台，约 30s 后查库 `location_updated_at` 有值

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/api/rescuer.js \
  mobile-rescuer/src/utils/locationReporter.js \
  mobile-rescuer/src/App.vue \
  mobile-rescuer/src/stores/user.js
git commit -m "feat(mobile): report GPS while rescuer app is foreground"
```

---

### Task 6: 派单详情列表分段、地图标车与轮询

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

**Interfaces:**
- Consumes: `nearby` 响应 `vehicles` + `staleVehicles`；`locationFresh`
- Produces: UI 两段；地图 accident + fresh markers；20s 轮询（仅 PENDING 且有坐标）

- [ ] **Step 1: 状态与分段**

增加：

```js
const staleNearby = ref([])
const staleExpanded = ref(false)
const nearbyPollHint = ref('')
let nearbyPollTimer = null
let vehicleMarkers = []
```

替换 `vehicleSections`：

```js
const vehicleSections = computed(() => [
  {
    key: 'fresh',
    title: '附近空闲（实时）',
    items: nearby.value,
    emptyText: '暂无实时定位的空闲车辆',
    collapsible: false
  },
  {
    key: 'stale',
    title: '位置未知 / 过期',
    items: staleNearby.value,
    emptyText: '无',
    collapsible: true
  }
])
```

模板中对 `collapsible` 段：用 `staleExpanded` 控制是否渲染 list；标题旁按钮「展开/收起」。

`formatDistance`：`meters == null` 时显示「距离未知」。

- [ ] **Step 2: 改造 `loadNearby`**

- 增加参数 `opts = { preserveSelection = false, silent = false }`
- `silent` 时不把 `nearbyLoading` 置 true（轮询不闪屏）
- 成功时：
  - `nearby.value = res.data?.vehicles || []`
  - `staleNearby.value = res.data?.staleVehicles || []`
- 无坐标兜底：`listVehicles` 映射项设 `locationFresh: false`，放入 `staleNearby`，`nearby` 置 `[]`（或全部进 stale，与 spec「无距离排序兜底」一致）
- 预填选中：在 `nearby` 与 `staleNearby` 中查找
- `preserveSelection` 为 true 时不清空 `selectedVehicleId`（除非选中车已不在两列表）
- 失败且 `silent`：设 `nearbyPollHint`，**不清空**现有列表

调用成功后若 `canShowMap`：`syncVehicleMarkers()`

- [ ] **Step 3: 地图标记**

扩展 `initMap`：创建地图与事故点标记后调用 `syncVehicleMarkers()`。

```js
function clearVehicleMarkers() {
  vehicleMarkers.forEach((m) => {
    if (m && typeof m.setMap === 'function') m.setMap(null)
  })
  vehicleMarkers = []
}

function syncVehicleMarkers() {
  if (!mapInstance || !window.AMap) return
  clearVehicleMarkers()
  const AMap = window.AMap
  nearby.value.forEach((item) => {
    const v = item.vehicle
    if (v?.longitude == null || v?.latitude == null) return
    const marker = new AMap.Marker({
      position: [Number(v.longitude), Number(v.latitude)],
      map: mapInstance,
      title: v.plateNo || '',
      label: {
        content: `${v.plateNo || ''} ${formatDistance(item.distanceMeters)}`,
        direction: 'top'
      }
    })
    marker.on('click', () => {
      selectedVehicleId.value = v.id
    })
    vehicleMarkers.push(marker)
  })
}
```

`destroyMap` 时先 `clearVehicleMarkers()`。

- [ ] **Step 4: 轮询**

```js
function startNearbyPoll() {
  stopNearbyPoll()
  if (!hasCoords.value || order.value?.status !== 'PENDING') return
  nearbyPollTimer = setInterval(() => {
    loadNearby({ preserveSelection: true, silent: true })
  }, 20000)
}

function stopNearbyPoll() {
  if (nearbyPollTimer) {
    clearInterval(nearbyPollTimer)
    nearbyPollTimer = null
  }
}
```

在现有 `watch`/加载 PENDING 成功路径：`loadNearby` → `initMap` → `startNearbyPoll`；`onBeforeUnmount` 与离开 PENDING：`stopNearbyPoll`。

- [ ] **Step 5: 手工验收**

1. 两台新鲜车：列表近→远；地图两标；点标记选中
2. 停报 >3 分钟：车进折叠段，地图消失
3. 折叠段仍可确认派单
4. 无 AMap Key：列表与轮询可用

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/dispatch/DispatchDetail.vue
git commit -m "feat(frontend): map fresh rescue vehicles and poll nearby on dispatch detail"
```

---

## Spec coverage (self-review)

| Spec 项 | Task |
|---------|------|
| `location_updated_at` + migrate/init | Task 1–2 |
| 仅移动端上报刷新新鲜度 | Task 2（update 不含该列）、Task 4 |
| `POST .../location` + `rescuer:location` | Task 1、4 |
| H5 30s 前台上报 | Task 5 |
| nearby 纯距离 + stale 分段 | Task 3 |
| 地图标 fresh、列表两段、20s 轮询 | Task 6 |
| 未绑车 / 定位失败 / 轮询失败行为 | Task 4–6 |
| 不做轨迹/WS/新建页选车 | 全局约束，无对应任务 |

## Placeholder / consistency check

- 权限 id 统一 `67`；常量 `180` / 上报 `30000` / 轮询 `20000`
- `distanceMeters` 统一 `Double`
- `updateLocation` 签名与测试 `verify` 一致
- 移动端 `getBoundVehicle` 返回形状以实现时对照现有页面为准（计划已注明）
