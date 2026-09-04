# 片区管理与排班管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 PC 片区电子围栏（CRUD + 点落区解析 + 车辆绑定）与自定义时段排班（调度员/施救员，施救员必绑车），不改派单推荐算法。

**Architecture:** 与现有车辆/工单同构新增 `District`、`DutySchedule` 两套 Entity→Mapper→Service→Controller；围栏存 `fence_json`，射线法点落区在 `GeoUtils`；排班冲突与角色校验在 `DutyScheduleService`；前端复用高德 JS API 画多边形，风格对齐 `VehicleList.vue`。

**Tech Stack:** Spring Boot 3.2、MyBatis、Spring Security JWT、MySQL、JUnit5+Mockito、Vue 3、Vue Router、Axios、高德地图 JS API 2.0

**Spec:** `docs/superpowers/specs/2026-09-04-district-schedule-design.md`

## Global Constraints

- 片区状态仅：`ENABLED` / `DISABLED`
- 排班 `role_type` 仅：`DISPATCHER` / `TOW_DRIVER`
- 点落区：只匹配 `ENABLED`；重叠按 `id` 升序取第一个；无命中返回 `null`
- 冲突判定：`start < other.end AND end > other.start`（两端均闭的重叠）
- `duty_date` 一律由后端取 `start_time.toLocalDate()` 覆盖写入
- `TOW_DRIVER` 班次 `vehicle_id` 必填；`DISPATCHER` 班次 `vehicle_id` 必须为 `null`
- 统一响应 `Result`；业务冲突 `RuntimeException`，Controller `catch` 后 `Result.error(message)`
- 权限 id：`31–41`（见 Task 1）；授予 `ADMIN`(role_id=5) 与 `DISPATCHER`(role_id=2)
- 不改 `nearby` / 派单状态机；YAGNI：无轨迹、无片区优先推荐、无移动端
- 不引入新 UI 库；沿用 `enterprise.css` 与车辆列表弹窗风格
- 高德 Key：`VITE_AMAP_KEY`；无 Key 时片区页允许手填 `fence_json` 文本（JSON 数组）

## File Structure

### Backend（新建）
- `util/GeoUtils.java`
- `entity/District.java`、`entity/DutySchedule.java`
- `mapper/DistrictMapper.java`、`mapper/DutyScheduleMapper.java`
- `service/DistrictService.java`、`service/DutyScheduleService.java`
- `controller/DistrictController.java`、`controller/ScheduleController.java`
- `dto/DistrictRequest.java`、`dto/ScheduleRequest.java`、`dto/LngLat.java`
- `test/.../util/GeoUtilsTest.java`
- `test/.../service/DistrictServiceTest.java`
- `test/.../service/DutyScheduleServiceTest.java`

### Backend（修改）
- `service/RescueVehicleService.java`（绑定片区时校验 ENABLED）

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-04_district_schedule.sql`

### Frontend（新建）
- `src/api/district.js`、`src/api/schedule.js`
- `src/views/district/DistrictList.vue`
- `src/views/schedule/ScheduleList.vue`
- 扩展 `src/utils/amap.js`：`createPolygonEditor`

### Frontend（修改）
- `src/router/index.js`、`src/App.vue`、`src/views/Home.vue`、`src/views/vehicle/VehicleList.vue`

---

### Task 1: 数据库表、权限与种子数据

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-04_district_schedule.sql`
- Test: MySQL 执行后 `SHOW TABLES` / 查 permission / 登录调度员

**Interfaces:**
- Produces: 表 `district`、`duty_schedule`；权限 id `31–41`；种子片区 2 个；用户 `towdriver`；样例排班；车辆 1/2 绑定片区 1

- [ ] **Step 1: 在 `init.sql` DROP 段最前增加**

```sql
DROP TABLE IF EXISTS `duty_schedule`;
DROP TABLE IF EXISTS `district`;
```

（保持先 DROP `dispatch_order` / `rescue_vehicle` 的既有顺序；`district` 无 FK 约束，但逻辑上车辆引用片区，DROP 车辆后再 DROP 片区亦可——本库无物理 FK，按上序即可。）

- [ ] **Step 2: 在 `rescue_vehicle` / `dispatch_order` DDL 之后追加两表**

使用 spec 中完整 `CREATE TABLE district` 与 `CREATE TABLE duty_schedule`（字段、索引、注释一致）。

- [ ] **Step 3: 扩展 permission（保留 1–30，追加 31–41）**

在现有 permission INSERT 末尾 `30` 之后追加：

```sql
(31, '片区管理', 'district:manage', 'MODULE', 0, 9),
(32, '片区查询', 'district:query', 'BUTTON', 31, 1),
(33, '片区新增', 'district:add', 'BUTTON', 31, 2),
(34, '片区编辑', 'district:edit', 'BUTTON', 31, 3),
(35, '片区删除', 'district:delete', 'BUTTON', 31, 4),
(36, '片区解析', 'district:resolve', 'BUTTON', 31, 5),
(37, '排班管理', 'schedule:manage', 'MODULE', 0, 10),
(38, '排班查询', 'schedule:query', 'BUTTON', 37, 1),
(39, '排班新增', 'schedule:add', 'BUTTON', 37, 2),
(40, '排班编辑', 'schedule:edit', 'BUTTON', 37, 3),
(41, '排班删除', 'schedule:delete', 'BUTTON', 37, 4);
```

- [ ] **Step 4: 改写 ADMIN / DISPATCHER 授权**

```sql
-- ADMIN: 1-15 + 派单 16,20-25 + 车辆 26-30 + 片区/排班 31-41
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id = 16 OR id BETWEEN 20 AND 41;

-- DISPATCHER: 派单 + 车辆 + 片区 + 排班
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission` WHERE id = 16 OR id BETWEEN 20 AND 41;
```

（TRAFFIC_POLICE / TOW_DRIVER / PARKING_ADMIN 行保持不变。）

- [ ] **Step 5: 种子片区、施救员、排班、车辆绑片区**

在车辆种子之后追加（密码哈希与 admin 相同 = `admin123`）：

```sql
INSERT INTO `district` (`name`, `code`, `fence_json`, `status`, `remark`) VALUES
('福田中心片区', 'FT-CENTER',
 '[{"lng":114.040,"lat":22.530},{"lng":114.080,"lat":22.530},{"lng":114.080,"lat":22.560},{"lng":114.040,"lat":22.560}]',
 'ENABLED', '市民中心一带'),
('南山前海片区', 'NS-QIANHAI',
 '[{"lng":113.980,"lat":22.500},{"lng":114.020,"lat":22.500},{"lng":114.020,"lat":22.540},{"lng":113.980,"lat":22.540}]',
 'ENABLED', '前海样例');

UPDATE `rescue_vehicle` SET `district_id` = 1 WHERE `plate_no` IN ('粤B·救援01', '粤B·救援02');

INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('towdriver', 'tow@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000002', '施救员演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (3, 3);

INSERT INTO `duty_schedule`
(`duty_date`, `start_time`, `end_time`, `user_id`, `role_type`, `district_id`, `vehicle_id`, `remark`) VALUES
(CURDATE(), CONCAT(CURDATE(), ' 08:00:00'), CONCAT(CURDATE(), ' 18:00:00'),
 2, 'DISPATCHER', 1, NULL, '调度白班样例'),
(CURDATE(), CONCAT(CURDATE(), ' 08:00:00'), CONCAT(CURDATE(), ' 18:00:00'),
 3, 'TOW_DRIVER', 1, 1, '施救白班样例');
```

注意：若库中已有 user id=3，增量脚本应改用 `WHERE username=...` 取 id，见 Step 6。

- [ ] **Step 6: 编写增量脚本 `database/migrate_2026-09-04_district_schedule.sql`**

内容：
1. `CREATE TABLE IF NOT EXISTS district` / `duty_schedule`（同 init）
2. `INSERT IGNORE` 或按 `permission_code` 不存在时插入 31–41
3. 为 role 5、2 补齐缺失的 `role_permission`（`INSERT ... SELECT ... WHERE NOT EXISTS`）
4. 种子片区：`INSERT IGNORE` 按 `code` 唯一
5. 若无 `towdriver` 则插入用户并绑 TOW_DRIVER
6. 可选更新车辆 `district_id`；样例排班仅当当日尚无该 user 班次时插入

- [ ] **Step 7: 对已有库执行增量并抽查**

```bash
mysql -u root -p vue_springboot_system < database/migrate_2026-09-04_district_schedule.sql
mysql -u root -p -e "USE vue_springboot_system; SHOW TABLES LIKE 'district'; SELECT permission_code FROM permission WHERE id>=31; SELECT code FROM district;"
```

Expected: 有 `district`/`duty_schedule`；权限含 `district:manage`、`schedule:manage`；片区 `FT-CENTER` 存在。

- [ ] **Step 8: Commit**

```bash
git add database/init.sql database/migrate_2026-09-04_district_schedule.sql
git commit -m "chore(db): add district and duty_schedule tables with permissions"
```

---

### Task 2: GeoUtils 点落多边形

**Files:**
- Create: `backend/src/main/java/com/example/backend/util/GeoUtils.java`
- Create: `backend/src/main/java/com/example/backend/dto/LngLat.java`
- Test: `backend/src/test/java/com/example/backend/util/GeoUtilsTest.java`

**Interfaces:**
- Produces:
  - `LngLat`: `double lng`, `double lat`（Lombok `@Data` 或 record）
  - `GeoUtils.parseFence(String fenceJson): List<LngLat>` — 非法 JSON / 非数组抛 `RuntimeException("围栏格式无效")`
  - `GeoUtils.normalizeFence(List<LngLat> points): List<LngLat>` — 去掉首尾重复闭合点；若 `<3` 点抛 `RuntimeException("围栏至少需要3个顶点")`
  - `GeoUtils.contains(List<LngLat> polygon, double lng, double lat): boolean` — 射线法
  - `GeoUtils.toFenceJson(List<LngLat> points): String` — 序列化为 `[{"lng":..,"lat":..},...]`

- [ ] **Step 1: 写失败测试 `GeoUtilsTest`**

```java
@ExtendWith(MockitoExtension.class) // 可不依赖 mockito；纯 JUnit 即可
class GeoUtilsTest {
  static final List<LngLat> SQUARE = List.of(
      new LngLat(114.04, 22.53), new LngLat(114.08, 22.53),
      new LngLat(114.08, 22.56), new LngLat(114.04, 22.56));

  @Test void containsInside() {
    assertTrue(GeoUtils.contains(SQUARE, 114.057868, 22.543099));
  }
  @Test void containsOutside() {
    assertFalse(GeoUtils.contains(SQUARE, 114.10, 22.60));
  }
  @Test void normalizeRejectsTwoPoints() {
    assertThrows(RuntimeException.class,
        () -> GeoUtils.normalizeFence(List.of(new LngLat(1,1), new LngLat(2,2))));
  }
  @Test void parseAndRoundTrip() {
    String json = GeoUtils.toFenceJson(SQUARE);
    List<LngLat> parsed = GeoUtils.normalizeFence(GeoUtils.parseFence(json));
    assertEquals(4, parsed.size());
  }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -q -Dtest=GeoUtilsTest test`  
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 `LngLat` + `GeoUtils`**

使用 Jackson `ObjectMapper`（Spring 已有）或 `com.fasterxml.jackson.databind` 解析 JSON 数组。射线法标准实现：对每条边判断与向右射线相交次数奇数则在内。边界点可按「在边上视为内」或「视为外」——本项目约定：**边上视为内**（实现时用叉积容差或简单落在边段上判断）。

- [ ] **Step 4: 再跑测试通过**

Run: `mvn -q -Dtest=GeoUtilsTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/util/GeoUtils.java \
  backend/src/main/java/com/example/backend/dto/LngLat.java \
  backend/src/test/java/com/example/backend/util/GeoUtilsTest.java
git commit -m "feat(backend): add GeoUtils point-in-polygon helpers"
```

---

### Task 3: 片区 Entity / Mapper / Service / Controller

**Files:**
- Create: `entity/District.java`、`mapper/DistrictMapper.java`、`service/DistrictService.java`、`controller/DistrictController.java`、`dto/DistrictRequest.java`
- Create: `test/.../service/DistrictServiceTest.java`
- Modify: `mapper` 增加引用计数查询（车辆/排班）——可在 `DistrictMapper` 用 `@Select` 查 `rescue_vehicle` / `duty_schedule`，或注入现有 Mapper 增加 `countByDistrictId`

**Interfaces:**
- Produces `DistrictService`:
  - `List<District> list(String keyword, String status)`
  - `District findById(Long id)`
  - `boolean create(DistrictRequest req)` / `boolean update(Long id, DistrictRequest req)`
  - `boolean delete(Long id)` — 有引用抛「片区仍被车辆或排班引用，请先解绑或改为禁用」
  - `District resolve(BigDecimal lng, BigDecimal lat)` — 加载全部 ENABLED，按 id 升序，第一个 `contains` 命中；无则 `null`
- `DistrictRequest`: `name`, `code`, `fenceJson`（或 `List<LngLat> fence`）, `status`, `remark`
- Mapper: `findAll`, `findById`, `findByCode`, `findByStatus`, `insert`, `update`, `deleteById`, `countVehiclesByDistrictId`, `countSchedulesByDistrictId`

- [ ] **Step 1: 写 `DistrictServiceTest`（Mockito）**

覆盖：
1. `create` 拒绝 `<3` 顶点 / 重复 code
2. `resolve` 命中正方形内点返回 id 较小片区
3. `resolve` 外点返回 null
4. `delete` 有车辆引用时拒绝
5. `create` 默认 status `ENABLED`

- [ ] **Step 2: 跑测试确认失败**

`mvn -q -Dtest=DistrictServiceTest test` → FAIL

- [ ] **Step 3: 实现 Entity / Mapper / Request / Service**

`District` 字段与表一致（camelCase）。`create`/`update` 流程：
1. 解析并 `normalizeFence` → `toFenceJson` 写回
2. `code` 唯一校验
3. `status` 仅允许 ENABLED/DISABLED

`resolve`：`findByStatus("ENABLED")` 后 `sorted(Comparator.comparing(District::getId))`。

- [ ] **Step 4: 实现 `DistrictController`**

```java
@RestController
@RequestMapping("/api/district")
@CrossOrigin(origins = "*")
public class DistrictController {
  // GET /list, GET /{id}, POST /, PUT /{id}, DELETE /{id}
  // GET /resolve?lng=&lat=  → Result.success(district or null)
  // 各方法 @PreAuthorize 对齐权限码
  // try/catch RuntimeException → Result.error
}
```

注意：将 `/resolve` 注册在 `/{id}` 之前，或使用不同路径避免把 `resolve` 当成 id（本项目用 `GET /resolve` 静态路径 + `GET /{id}`，Spring 可区分字符串字面量路径，**把 `/resolve` 方法写在 `/{id}` 前面**）。

- [ ] **Step 5: 跑测试通过**

`mvn -q -Dtest=DistrictServiceTest,GeoUtilsTest test` → PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/District.java \
  backend/src/main/java/com/example/backend/mapper/DistrictMapper.java \
  backend/src/main/java/com/example/backend/service/DistrictService.java \
  backend/src/main/java/com/example/backend/controller/DistrictController.java \
  backend/src/main/java/com/example/backend/dto/DistrictRequest.java \
  backend/src/test/java/com/example/backend/service/DistrictServiceTest.java
git commit -m "feat(backend): add district CRUD and resolve API"
```

---

### Task 4: 排班 Entity / Mapper / Service / Controller

**Files:**
- Create: `entity/DutySchedule.java`、`mapper/DutyScheduleMapper.java`、`service/DutyScheduleService.java`、`controller/ScheduleController.java`、`dto/ScheduleRequest.java`
- Test: `test/.../service/DutyScheduleServiceTest.java`
- Uses: `UserMapper.findRolesByUserId`、`RescueVehicleMapper.findById`、`DistrictMapper.findById`

**Interfaces:**
- Produces `DutyScheduleService`:
  - `List<DutySchedule> list(LocalDate from, LocalDate to, String roleType, Long districtId, Long userId)`
  - `DutySchedule findById(Long id)`
  - `boolean create(ScheduleRequest req)` / `boolean update(Long id, ScheduleRequest req)` / `boolean delete(Long id)`
- `ScheduleRequest`: `startTime`, `endTime`（`LocalDateTime`）、`userId`、`roleType`、`districtId`、`vehicleId`、`remark`
- 校验顺序：
  1. `endTime.isAfter(startTime)` 否则「结束时间必须晚于开始时间」
  2. `roleType` 合法
  3. 用户存在且角色列表含对应 `role_code`（DISPATCHER / TOW_DRIVER）
  4. DISPATCHER → `vehicleId` 必须 null；TOW_DRIVER → vehicle 非空且存在
  5. 若 `districtId` 非空：片区存在且 `ENABLED`（更新时若片区已 DISABLED 且未改 district 可保留——简化：**任何写入非空 districtId 均要求 ENABLED**）
  6. 冲突：查同 user 重叠、同 vehicle 重叠（更新时排除自身 id）
  7. 设置 `dutyDate = startTime.toLocalDate()`

Mapper 需提供：
```java
List<DutySchedule> findOverlappingByUser(@Param("userId") Long userId,
    @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
    @Param("excludeId") Long excludeId);
List<DutySchedule> findOverlappingByVehicle(...); // vehicleId 非空时
```

SQL 条件：`start_time < #{end} AND end_time > #{start}`，且 `(excludeId IS NULL OR id <> excludeId)`。

- [ ] **Step 1: 写 `DutyScheduleServiceTest`**

用例：
1. 施救员无 vehicle → 失败
2. 调度员带 vehicle → 失败
3. 同人时间重叠 → 失败
4. 同车时间重叠 → 失败
5. 跨日班次与次日早晨重叠 → 失败
6. 合法创建成功且 `dutyDate` 等于 start 日期
7. 用户无对应角色 → 失败
8. 绑定 DISABLED 片区 → 失败

- [ ] **Step 2: 跑测失败 → Step 3 实现全部类 → Step 4 跑测通过**

Controller：`/api/schedule`，权限 `schedule:*`。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/DutySchedule.java \
  backend/src/main/java/com/example/backend/mapper/DutyScheduleMapper.java \
  backend/src/main/java/com/example/backend/service/DutyScheduleService.java \
  backend/src/main/java/com/example/backend/controller/ScheduleController.java \
  backend/src/main/java/com/example/backend/dto/ScheduleRequest.java \
  backend/src/test/java/com/example/backend/service/DutyScheduleServiceTest.java
git commit -m "feat(backend): add duty schedule CRUD with conflict checks"
```

---

### Task 5: 车辆绑定片区校验

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/RescueVehicleService.java`
- Modify: `backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java`

**Interfaces:**
- Consumes: `DistrictMapper.findById`
- 在 `createVehicle` / `updateVehicle`：若 `districtId != null`，片区必须存在且 `ENABLED`，否则抛「片区不存在或已禁用」

- [ ] **Step 1: 测试** — 绑 DISABLED 失败；`districtId=null` 成功；绑 ENABLED 成功  
- [ ] **Step 2: 实现并跑 `RescueVehicleServiceTest` PASS**  
- [ ] **Step 3: Commit** `fix(backend): validate district when binding vehicle`

---

### Task 6: 前端片区 API + 高德多边形 + 列表页

**Files:**
- Create: `frontend/src/api/district.js`
- Modify: `frontend/src/utils/amap.js` — 增加 `createPolygonEditor`
- Create: `frontend/src/views/district/DistrictList.vue`
- Modify: `frontend/src/router/index.js`、`frontend/src/App.vue`、`frontend/src/views/Home.vue`

**Interfaces:**
- `api/district.js`: `listDistricts`, `getDistrict`, `createDistrict`, `updateDistrict`, `deleteDistrict`, `resolveDistrict`
- `createPolygonEditor(container, { path, onChange })`：
  - 有 Key：创建地图，`AMap.Polygon` + 点击加点或使用 `MouseTool`/`PolygonEditor`（若 2.0 插件可用）；`onChange(points: {lng,lat}[])`
  - 无 Key：不建地图，由页面显示 textarea 编辑 JSON

- [ ] **Step 1: 实现 `district.js`**（对齐 `vehicle.js` 风格，base path `/district`）

- [ ] **Step 2: 扩展 `amap.js`**

```js
export async function createPolygonEditor(container, { path = [], onChange } = {}) {
  const AMap = await loadAmap()
  const map = new AMap.Map(container, { zoom: 12, center: [114.057868, 22.543099] })
  let polygon
  const apply = (ring) => {
    const pathLL = ring.map((p) => [p.lng, p.lat])
    if (!polygon) polygon = new AMap.Polygon({ path: pathLL, map, strokeWeight: 2 })
    else polygon.setPath(pathLL)
    onChange?.(ring)
  }
  if (path.length) apply(path)
  // 简化：点击地图追加顶点；双击结束（或提供「完成绘制」按钮由页面调用 flush）
  map.on('click', (e) => {
    const next = [...(polygon ? polygon.getPath().map(ll => ({ lng: ll.lng, lat: ll.lat })) : []),
      { lng: e.lnglat.lng, lat: e.lnglat.lat }]
    apply(next)
  })
  return {
    map,
    setPath: apply,
    clear() { if (polygon) { map.remove(polygon); polygon = null; onChange?.([]) } }
  }
}
```

（实现时可加「撤销一点」「清空」按钮；至少 3 点才允许提交。）

- [ ] **Step 3: `DistrictList.vue`**

列表列：名称、编码、状态、操作（编辑/删除）。  
弹窗字段：name、code、status、remark、地图/JSON 围栏。  
样式类名对齐 `VehicleList.vue`（`page`、`panel`、`data-table`、`modal`）。

- [ ] **Step 4: 路由与导航**

```js
{ path: '/districts', name: 'DistrictList',
  component: () => import('../views/district/DistrictList.vue'),
  meta: { permissions: ['district:manage'] } }
```

`App.vue`「业务调度」下增加：

```html
<router-link v-auth="'district:manage'" to="/districts" ...>片区管理</router-link>
```

`Home.vue` 增加入口卡片（若已有车辆/任务卡片模式则同构）。

- [ ] **Step 5: 手动验收**

登录 `dispatcher`/`admin123` → `/districts` 可新建片区 → 列表可见 → `GET /api/district/resolve` 用围栏内坐标返回该片区。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/district.js frontend/src/utils/amap.js \
  frontend/src/views/district/DistrictList.vue \
  frontend/src/router/index.js frontend/src/App.vue frontend/src/views/Home.vue
git commit -m "feat(frontend): add district management with map fence editor"
```

---

### Task 7: 前端排班页 + 车辆绑片区

**Files:**
- Create: `frontend/src/api/schedule.js`、`frontend/src/views/schedule/ScheduleList.vue`
- Modify: `frontend/src/views/vehicle/VehicleList.vue`
- Modify: `frontend/src/router/index.js`、`frontend/src/App.vue`、`frontend/src/views/Home.vue`
- Uses: `listUsers`（现有 user API）、`listVehicles`、`listDistricts`

**Interfaces:**
- `schedule.js`: list/get/create/update/delete
- 排班表单：`roleType` 切换时清空/显示 `vehicleId`；人员下拉按角色过滤（调用用户列表后前端 `roles` 过滤，或仅展示全部用户并依赖后端校验）
- 筛选：`from`/`to` 日期（默认当天）

- [ ] **Step 1: 实现 `schedule.js` + `ScheduleList.vue`**

列表列：日期、时段、人员、角色、片区、车辆、操作。  
冲突错误展示 `error` 文案（后端 message）。

- [ ] **Step 2: 路由 `/schedules`，meta `schedule:manage`；侧栏「排班管理」**

- [ ] **Step 3: `VehicleList.vue` 增加片区列与下拉**

加载 `listDistricts({ status: 'ENABLED' })`；编辑时可显示当前已绑 DISABLED 片区为只读选项 + 启用列表；允许清空为未绑定。提交 `districtId: null` 或数字。

- [ ] **Step 4: 手动联调清单**

| 步骤 | 期望 |
|------|------|
| 调度员建排班不选车 | 成功 |
| 施救员不选车 | 失败提示 |
| 同人重叠班次 | 失败提示 |
| 车辆绑启用片区 | 成功，列表显示名称 |
| 无 `district:manage` 账号 | 无菜单，直链 403 |
| 派单 nearby | 行为与改前一致 |

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/schedule.js frontend/src/views/schedule/ScheduleList.vue \
  frontend/src/views/vehicle/VehicleList.vue \
  frontend/src/router/index.js frontend/src/App.vue frontend/src/views/Home.vue
git commit -m "feat(frontend): add schedule management and vehicle district binding"
```

---

### Task 8: 回归测试与收尾

**Files:** 无强制新文件

- [ ] **Step 1: 后端全量单测**

```bash
cd backend && mvn -q test
```

Expected: BUILD SUCCESS（含 GeoUtils / District / DutySchedule / RescueVehicle / 既有用例）

- [ ] **Step 2: 对照 spec 验收表 1–7 手工勾选**

- [ ] **Step 3: 若有文档漂移，小改 spec/plan 勾选状态后可选 commit** `docs: note district-schedule implementation complete`（仅当用户要求提交时）

---

## Spec coverage (self-review)

| Spec 项 | Task |
|---------|------|
| district / duty_schedule DDL + init/migrate | 1 |
| 权限 31–41 授 ADMIN/DISPATCHER | 1 |
| 种子片区/排班/towdriver/车辆绑片区 | 1 |
| 射线法 / fence 校验 | 2–3 |
| 片区 CRUD + resolve | 3 |
| 排班 CRUD + 冲突/角色/绑车 | 4 |
| 禁用片区不可新绑车辆 | 5 |
| 禁用片区不可新排班 | 4 |
| 删片区有引用拒绝 | 3 |
| 前端片区/排班/车辆/导航 | 6–7 |
| 不改派单推荐 | 约束 + Task 8 |
| duty_date 取 start 日期 | 4 |

无 TBD 占位；权限 id、类名、路径与 spec 一致。
