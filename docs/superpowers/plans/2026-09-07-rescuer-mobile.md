# 施救员移动端（uni-app）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地施救员 uni-app 端完整闭环（登录、绑车、任务接单/退单、500m 签到、现场/入库含照片、完成）及配套后端 `/api/mobile/rescuer` 与 PC 二维码/状态文案。

**Architecture:** 扩展 `dispatch_order` 状态机（`ACCEPTED`）与签到字段；新增 `dispatch_field_record` / `dispatch_media`；`RescuerMobileService` + `RescuerMobileController` 竖切；本地 `uploads/` 静态映射；新建 `mobile-rescuer/` uni-app（首期 H5）；PC 车辆页展示 `RV:{id}` 二维码。

**Tech Stack:** Spring Boot 3.2、MyBatis、Spring Security JWT、MySQL、JUnit5+Mockito、uni-app Vue3、Vue3 PC、`qrcode`

**Spec:** `docs/superpowers/specs/2026-09-07-rescuer-mobile-design.md`

## Global Constraints

- 工单状态仅：`PENDING` → `DISPATCHED` → `ACCEPTED` → `COMPLETED`；可 `ABORTED`；退单 → `PENDING` 并清空 `vehicle_id`/`rescuer_id`，车辆 `IDLE`
- 已签到（`checked_in_at != null`）禁止退单
- 签到：`AUTO` 须坐标且距事故点 Haversine ≤ **500** 米；`MANUAL` 须非空 `remark`
- 接单/签到/现场/入库/完成仅当 `rescuer_id = 当前用户`
- 入库登记只写 `dispatch_field_record`，**不**写 `detained_vehicle`
- 照片：jpg/png/jpeg/webp，单文件 ≤5MB，落盘 `uploads/dispatch/{orderId}/`，库存相对路径
- 二维码载荷：`RV:{vehicleId}` 明文
- 权限 id：`53–62`；授予 `TOW_DRIVER`(role_id=3) 与 `ADMIN`(role_id=5)
- 占车计数：`status IN ('DISPATCHED','ACCEPTED')`
- 统一 `Result`；业务冲突 `RuntimeException` → Controller `Result.error(message)`
- YAGNI：无交警/停车场端、无对象存储、无轨迹、无小程序上架
- 首期 uni-app 目标：**H5**

## File Structure

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-07_rescuer_mobile.sql`

### Backend（新建）
- `entity/DispatchFieldRecord.java`、`entity/DispatchMedia.java`
- `mapper/DispatchFieldRecordMapper.java`、`mapper/DispatchMediaMapper.java`
- `dto/RescuerProfileUpdateRequest.java`、`dto/BindVehicleRequest.java`、`dto/RejectRequest.java`、`dto/CheckinRequest.java`、`dto/SceneRequest.java`、`dto/ParkRequest.java`
- `service/RescuerMobileService.java`、`service/LocalFileStorageService.java`
- `controller/RescuerMobileController.java`
- `config/WebMvcConfig.java`（`/uploads/**` 映射）
- `test/.../service/RescuerMobileServiceTest.java`、扩展 `DispatchOrderServiceTest.java`、`GeoUtilsTest.java`

### Backend（修改）
- `entity/DispatchOrder.java`、`mapper/DispatchOrderMapper.java`、`mapper/RescueVehicleMapper.java`
- `service/DispatchOrderService.java`、`util/GeoUtils.java`
- `config/SecurityConfig.java`（放行 `GET /uploads/**`）

### Frontend PC（修改）
- `frontend/src/views/vehicle/VehicleList.vue`（二维码）
- `frontend/src/views/dispatch/DispatchList.vue`、`DispatchDetail.vue`（`ACCEPTED` 文案与操作）

### Mobile（新建）
- `mobile-rescuer/` uni-app 工程（pages、api、store、utils）

---

### Task 1: 数据库表、权限与实体/Mapper 字段

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-07_rescuer_mobile.sql`
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java`
- Test: MySQL 执行 migrate 后 `DESC dispatch_order`；查 permission 53–62

**Interfaces:**
- Produces: 增量列；表 `dispatch_field_record`、`dispatch_media`；权限 53–62；Mapper 读写新列；`clearDriverByUserId` / `findByDriverUserId`

- [ ] **Step 1: 编写 migrate SQL**

在 `database/migrate_2026-09-07_rescuer_mobile.sql` 写入（幂等用注释说明「仅执行一次」）：

```sql
ALTER TABLE `dispatch_order`
  ADD COLUMN `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
  ADD COLUMN `checked_in_at` DATETIME DEFAULT NULL COMMENT '签到时间',
  ADD COLUMN `checkin_lng` DECIMAL(10,7) DEFAULT NULL,
  ADD COLUMN `checkin_lat` DECIMAL(10,7) DEFAULT NULL,
  ADD COLUMN `checkin_mode` VARCHAR(20) DEFAULT NULL COMMENT 'AUTO/MANUAL',
  ADD COLUMN `checkin_remark` VARCHAR(500) DEFAULT NULL,
  ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '最近退单原因';

-- 同步 init 时 status 注释已在 init 改；migrate 不强制 MODIFY COMMENT

CREATE TABLE IF NOT EXISTS `dispatch_field_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `plate_no` VARCHAR(20) DEFAULT NULL,
  `vehicle_type` VARCHAR(50) DEFAULT NULL,
  `damage_desc` VARCHAR(1000) DEFAULT NULL,
  `scene_remark` VARCHAR(500) DEFAULT NULL,
  `park_address` VARCHAR(255) DEFAULT NULL,
  `park_remark` VARCHAR(500) DEFAULT NULL,
  `scene_submitted_at` DATETIME DEFAULT NULL,
  `park_submitted_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场与入库登记';

CREATE TABLE IF NOT EXISTS `dispatch_media` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(20) NOT NULL COMMENT 'DAMAGE/PARK',
  `file_path` VARCHAR(500) NOT NULL,
  `sort_order` INT DEFAULT 0,
  `uploaded_by` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_biz` (`dispatch_order_id`, `biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场媒体';

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
(53, '施救员移动端', 'mobile:rescuer', 'MODULE', 0, 12),
(54, '施救资料', 'rescuer:profile', 'BUTTON', 53, 1),
(55, '扫码绑车', 'rescuer:bind-vehicle', 'BUTTON', 53, 2),
(56, '施救任务', 'rescuer:task', 'BUTTON', 53, 3),
(57, '施救接单', 'rescuer:accept', 'BUTTON', 53, 4),
(58, '施救退单', 'rescuer:reject', 'BUTTON', 53, 5),
(59, '施救签到', 'rescuer:checkin', 'BUTTON', 53, 6),
(60, '现场采集', 'rescuer:scene', 'BUTTON', 53, 7),
(61, '入库登记', 'rescuer:park', 'BUTTON', 53, 8),
(62, '施救完成', 'rescuer:complete', 'BUTTON', 53, 9);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, id FROM `permission` WHERE id BETWEEN 53 AND 62;
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 53 AND 62;
```

- [ ] **Step 2: 同步 `database/init.sql`**

1. `DROP TABLE IF EXISTS dispatch_media;` / `dispatch_field_record;`（在 `dispatch_order` 相关 DROP 附近）
2. `dispatch_order` CREATE 增加上述 7 列；status 注释含 `ACCEPTED`
3. 在业务表段追加两表 CREATE（与 migrate 一致）
4. permission INSERT 在 id=52 后追加 53–62
5. `role_permission` 为 role 3 与 5 追加 53–62（可 `INSERT ... SELECT` 或逐条）

- [ ] **Step 3: 扩展 `DispatchOrder` 实体字段**

增加：`acceptedAt`、`checkedInAt`、`checkinLng`、`checkinLat`、`checkinMode`、`checkinRemark`、`rejectReason`（类型与现有字段一致：`LocalDateTime` / `BigDecimal` / `String`）。

- [ ] **Step 4: 更新 `DispatchOrderMapper` insert/update/占车计数**

```java
@Select("SELECT COUNT(*) FROM dispatch_order WHERE vehicle_id = #{vehicleId} AND status IN ('DISPATCHED','ACCEPTED')")
int countDispatchedByVehicleId(Long vehicleId);

@Select("SELECT COUNT(*) FROM dispatch_order WHERE vehicle_id = #{vehicleId} AND status IN ('PENDING','DISPATCHED','ACCEPTED')")
int countActiveByVehicleId(Long vehicleId);
```

`insert`/`update` SQL 列清单加入全部新字段（与实体一一对应）。

- [ ] **Step 5: `RescueVehicleMapper` 增加**

```java
@Select("SELECT * FROM rescue_vehicle WHERE driver_user_id = #{driverUserId} LIMIT 1")
RescueVehicle findByDriverUserId(Long driverUserId);

@Update("UPDATE rescue_vehicle SET driver_user_id = NULL WHERE driver_user_id = #{driverUserId}")
int clearDriverByUserId(Long driverUserId);
```

- [ ] **Step 6: 执行 migrate（有 MySQL 时）并抽查**

```bash
mysql -u root -p vue_springboot_system < database/migrate_2026-09-07_rescuer_mobile.sql
```

Expected: 新列与两表存在；`SELECT id,permission_code FROM permission WHERE id>=53` 返回 10 行。

- [ ] **Step 7: Commit**

```bash
git add database/init.sql database/migrate_2026-09-07_rescuer_mobile.sql \
  backend/src/main/java/com/example/backend/entity/DispatchOrder.java \
  backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java \
  backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java
git commit -m "chore(db): add rescuer mobile schema and permissions"
```

---

### Task 2: GeoUtils 距离 + 工单状态机（接单/退单/签到/完成/中止）

**Files:**
- Modify: `backend/src/main/java/com/example/backend/util/GeoUtils.java`
- Modify: `backend/src/test/java/com/example/backend/util/GeoUtilsTest.java`
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`

**Interfaces:**
- Consumes: Task1 Mapper 新列与 `countDispatchedByVehicleId` 含 ACCEPTED
- Produces: `GeoUtils.distanceMeters`；`accept`/`reject`/`checkin`/`complete`(含 ACCEPTED)/`abort`(含 ACCEPTED)

- [ ] **Step 1: 写失败单测 `distanceMeters` 与 accept/reject/checkin**

在 `GeoUtilsTest` 增加：同一点距离约 0；已知两点约 500m 量级断言（允许误差 20m）。

在 `DispatchOrderServiceTest` 增加（Mockito）：

```java
@Test
void acceptMovesDispatchedToAccepted() {
    DispatchOrder o = new DispatchOrder();
    o.setId(1L); o.setStatus("DISPATCHED"); o.setRescuerId(3L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(o);
    when(dispatchOrderMapper.update(any())).thenReturn(1);
    service.accept(1L, 3L);
    assertEquals("ACCEPTED", o.getStatus());
    assertNotNull(o.getAcceptedAt());
}

@Test
void acceptRejectsWrongRescuer() {
    DispatchOrder o = new DispatchOrder();
    o.setId(1L); o.setStatus("DISPATCHED"); o.setRescuerId(9L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(o);
    assertThrows(RuntimeException.class, () -> service.accept(1L, 3L));
}

@Test
void rejectClearsAssignmentAndReleasesVehicle() {
    DispatchOrder o = new DispatchOrder();
    o.setId(1L); o.setStatus("DISPATCHED"); o.setRescuerId(3L); o.setVehicleId(8L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(o);
    when(dispatchOrderMapper.update(any())).thenReturn(1);
    when(dispatchOrderMapper.countDispatchedByVehicleId(8L)).thenReturn(0);
    service.reject(1L, 3L, "无法到达");
    assertEquals("PENDING", o.getStatus());
    assertNull(o.getVehicleId());
    assertNull(o.getRescuerId());
    verify(rescueVehicleService).markIdle(8L);
}

@Test
void rejectFailsAfterCheckin() {
    DispatchOrder o = new DispatchOrder();
    o.setId(1L); o.setStatus("ACCEPTED"); o.setRescuerId(3L);
    o.setCheckedInAt(LocalDateTime.now());
    when(dispatchOrderMapper.findById(1L)).thenReturn(o);
    assertThrows(RuntimeException.class, () -> service.reject(1L, 3L, "x"));
}

@Test
void checkinAutoFailsWhenTooFar() {
    DispatchOrder o = new DispatchOrder();
    o.setId(1L); o.setStatus("ACCEPTED"); o.setRescuerId(3L);
    o.setLongitude(new BigDecimal("121.0000000"));
    o.setLatitude(new BigDecimal("31.0000000"));
    when(dispatchOrderMapper.findById(1L)).thenReturn(o);
    // ~0.01 deg lat ≈ 1.1km
    assertThrows(RuntimeException.class, () ->
        service.checkin(1L, 3L, new BigDecimal("121.0000000"), new BigDecimal("31.0100000"), "AUTO", null));
}

@Test
void completeAcceptedRequiresCheckin() {
    DispatchOrder o = new DispatchOrder();
    o.setId(1L); o.setStatus("ACCEPTED"); o.setRescuerId(3L); o.setVehicleId(8L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(o);
    assertThrows(RuntimeException.class, () -> service.complete(1L));
}

@Test
void abortAcceptedReleasesVehicle() {
    DispatchOrder o = new DispatchOrder();
    o.setId(1L); o.setStatus("ACCEPTED"); o.setVehicleId(8L); o.setRescuerId(3L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(o);
    when(dispatchOrderMapper.update(any())).thenReturn(1);
    when(dispatchOrderMapper.countDispatchedByVehicleId(8L)).thenReturn(0);
    service.abort(1L, "取消");
    assertEquals("ABORTED", o.getStatus());
    assertEquals(3L, o.getRescuerId()); // 保留以便 aborted 列表
    verify(rescueVehicleService).markIdle(8L);
}
```

注意：`@InjectMocks` 若缺少 `RescueVehicleMapper` mock，现有测试已有；保持与现文件一致。`complete(1L)` 现签名无 userId——PC complete 保持；移动端 complete 在 `RescuerMobileService` 校验本人后调用 `complete`。

- [ ] **Step 2: 运行单测确认失败**

```bash
cd backend && mvn -q -Dtest=GeoUtilsTest,DispatchOrderServiceTest test
```

Expected: 新用例 FAIL（方法不存在或旧 complete 行为不符）。

- [ ] **Step 3: 实现 `GeoUtils.distanceMeters`**

```java
public static double distanceMeters(BigDecimal lng1, BigDecimal lat1, BigDecimal lng2, BigDecimal lat2) {
    if (lng1 == null || lat1 == null || lng2 == null || lat2 == null) {
        throw new RuntimeException("坐标无效");
    }
    double lon1 = Math.toRadians(lng1.doubleValue());
    double lat1r = Math.toRadians(lat1.doubleValue());
    double lon2 = Math.toRadians(lng2.doubleValue());
    double lat2r = Math.toRadians(lat2.doubleValue());
    double dlon = lon2 - lon1;
    double dlat = lat2r - lat1r;
    double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
            + Math.cos(lat1r) * Math.cos(lat2r) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return 6371000.0 * c;
}
```

（需 `import java.math.BigDecimal;`）

- [ ] **Step 4: 实现 `DispatchOrderService` 方法并改 `complete`/`abort`**

```java
@Transactional
public void accept(Long orderId, Long rescuerId) {
    DispatchOrder order = requireOrder(orderId);
    assertRescuer(order, rescuerId);
    if (!"DISPATCHED".equals(order.getStatus())) {
        throw new RuntimeException("仅已派单状态可接单");
    }
    order.setStatus("ACCEPTED");
    order.setAcceptedAt(LocalDateTime.now());
    dispatchOrderMapper.update(order);
}

@Transactional
public void reject(Long orderId, Long rescuerId, String reason) {
    DispatchOrder order = requireOrder(orderId);
    assertRescuer(order, rescuerId);
    if (!"DISPATCHED".equals(order.getStatus()) && !"ACCEPTED".equals(order.getStatus())) {
        throw new RuntimeException("当前状态不可退单");
    }
    if (order.getCheckedInAt() != null) {
        throw new RuntimeException("已签到不可退单");
    }
    if (reason == null || reason.isBlank()) {
        throw new RuntimeException("请填写退单原因");
    }
    Long vehicleId = order.getVehicleId();
    order.setStatus("PENDING");
    order.setRejectReason(reason.trim());
    order.setVehicleId(null);
    order.setRescuerId(null);
    order.setDispatchedAt(null);
    order.setAcceptedAt(null);
    dispatchOrderMapper.update(order);
    releaseVehicleIfUnused(vehicleId);
}

@Transactional
public void checkin(Long orderId, Long rescuerId, BigDecimal lng, BigDecimal lat, String mode, String remark) {
    DispatchOrder order = requireOrder(orderId);
    assertRescuer(order, rescuerId);
    if (!"ACCEPTED".equals(order.getStatus())) {
        throw new RuntimeException("仅已接单状态可签到");
    }
    if (order.getCheckedInAt() != null) {
        throw new RuntimeException("已签到");
    }
    if ("AUTO".equals(mode)) {
        if (order.getLongitude() == null || order.getLatitude() == null) {
            throw new RuntimeException("工单缺少事故坐标，请使用手动签到");
        }
        if (lng == null || lat == null) {
            throw new RuntimeException("自动签到需要定位坐标");
        }
        double meters = GeoUtils.distanceMeters(order.getLongitude(), order.getLatitude(), lng, lat);
        if (meters > 500.0) {
            throw new RuntimeException("距离事故点超过500米，无法自动签到");
        }
        order.setCheckinLng(lng);
        order.setCheckinLat(lat);
    } else if ("MANUAL".equals(mode)) {
        if (remark == null || remark.isBlank()) {
            throw new RuntimeException("手动签到须填写原因");
        }
        order.setCheckinRemark(remark.trim());
        order.setCheckinLng(lng);
        order.setCheckinLat(lat);
    } else {
        throw new RuntimeException("签到模式无效");
    }
    order.setCheckinMode(mode);
    order.setCheckedInAt(LocalDateTime.now());
    dispatchOrderMapper.update(order);
}

// complete: 允许 DISPATCHED（兼容旧 PC）或 ACCEPTED；若 ACCEPTED 则必须已签到
@Transactional
public void complete(Long orderId) {
    DispatchOrder order = requireOrder(orderId);
    if ("ACCEPTED".equals(order.getStatus())) {
        if (order.getCheckedInAt() == null) {
            throw new RuntimeException("请先签到再完成");
        }
    } else if (!"DISPATCHED".equals(order.getStatus())) {
        throw new RuntimeException("当前状态不可完成");
    }
    Long vehicleId = order.getVehicleId();
    order.setStatus("COMPLETED");
    order.setCompletedAt(LocalDateTime.now());
    dispatchOrderMapper.update(order);
    releaseVehicleIfUnused(vehicleId);
}

@Transactional
public void abort(Long orderId, String abortReason) {
    DispatchOrder order = requireOrder(orderId);
    String st = order.getStatus();
    if (!"PENDING".equals(st) && !"DISPATCHED".equals(st) && !"ACCEPTED".equals(st)) {
        throw new RuntimeException("当前状态不可作废");
    }
    Long vehicleId = order.getVehicleId();
    boolean occupied = "DISPATCHED".equals(st) || "ACCEPTED".equals(st);
    order.setStatus("ABORTED");
    order.setAbortReason(abortReason);
    // 保留 rescuer_id / vehicle_id 历史信息供列表；释放占用
    dispatchOrderMapper.update(order);
    if (occupied) {
        releaseVehicleIfUnused(vehicleId);
    }
}

private DispatchOrder requireOrder(Long id) {
    DispatchOrder order = dispatchOrderMapper.findById(id);
    if (order == null) throw new RuntimeException("工单不存在");
    return order;
}

private void assertRescuer(DispatchOrder order, Long rescuerId) {
    if (rescuerId == null || !rescuerId.equals(order.getRescuerId())) {
        throw new RuntimeException("无权操作该工单");
    }
}
```

更新既有 `complete`/`abort` 单测期望（若断言「仅 DISPATCHED」需改）。

- [ ] **Step 5: 跑通单测**

```bash
cd backend && mvn -q -Dtest=GeoUtilsTest,DispatchOrderServiceTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/util/GeoUtils.java \
  backend/src/main/java/com/example/backend/service/DispatchOrderService.java \
  backend/src/test/java/com/example/backend/util/GeoUtilsTest.java \
  backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java
git commit -m "feat(backend): extend dispatch status for rescuer accept checkin reject"
```

---

### Task 3: 现场记录、媒体存储与 RescuerMobileService

**Files:**
- Create: entities/mappers for field+media；`LocalFileStorageService.java`；`RescuerMobileService.java`；DTOs；`RescuerMobileServiceTest.java`
- Modify: `application.yml`（可选 `app.upload-dir: uploads`）
- Create: `config/WebMvcConfig.java`
- Modify: `SecurityConfig.java` 放行 `GET /uploads/**`

**Interfaces:**
- Consumes: Task2 `DispatchOrderService` 方法；Task1 表
- Produces: `listTasks`/`getTask`/`saveScene`/`savePark`/`addMedia`/`listMedia`/`updateProfile`/`bindVehicle`/`getBoundVehicle`

- [ ] **Step 1: 写 `RescuerMobileServiceTest` 关键用例（先失败）**

覆盖：`bindVehicle` 解析 `RV:1`、清除旧绑；`saveScene` 非 ACCEPTED 失败；`addMedia` 非法 bizType 失败。Mock mappers + `DispatchOrderService` 或直接用真实 service 注入 mapper mocks。

- [ ] **Step 2: 实现 Entity + Mapper**

`DispatchFieldRecordMapper`：`findByOrderId`、`insert`、`update`  
`DispatchMediaMapper`：`insert`、`findByOrderId`、`findByOrderIdAndBizType`

- [ ] **Step 3: `LocalFileStorageService`**

```java
@Service
public class LocalFileStorageService {
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public String storeDispatchMedia(Long orderId, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot).toLowerCase();
        if (!List.of(".jpg", ".jpeg", ".png", ".webp").contains(ext)) {
            throw new RuntimeException("仅支持 jpg/png/webp");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("文件不能超过5MB");
        }
        Path dir = Paths.get(uploadDir, "dispatch", String.valueOf(orderId)).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = System.currentTimeMillis() + ext;
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new RuntimeException("非法路径");
        }
        file.transferTo(target);
        return "dispatch/" + orderId + "/" + name; // 相对 uploadDir
    }
}
```

`WebMvcConfig`：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
```

`SecurityConfig`：`.requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()`

`.gitignore` 增加 `/uploads/`（若尚未忽略）。

- [ ] **Step 4: 实现 `RescuerMobileService` 核心方法**

要点：
- `listTasks(userId, tab)`：filter `findAll()` 按 tab；todo=`DISPATCHED|ACCEPTED`；done=`COMPLETED`；aborted=`ABORTED`；均 `rescuerId==userId`
- `requireOwnedAccepted` 后 `upsert` field record
- `bindVehicle`：payload 匹配 `^RV:(\\d+)$`；`clearDriverByUserId`；设 `driverUserId`；`update`
- `updateProfile`：只改 realName/phone/email，经 `UserMapper.update`

- [ ] **Step 5: 跑测**

```bash
cd backend && mvn -q -Dtest=RescuerMobileServiceTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/DispatchFieldRecord.java \
  backend/src/main/java/com/example/backend/entity/DispatchMedia.java \
  backend/src/main/java/com/example/backend/mapper/DispatchFieldRecordMapper.java \
  backend/src/main/java/com/example/backend/mapper/DispatchMediaMapper.java \
  backend/src/main/java/com/example/backend/service/LocalFileStorageService.java \
  backend/src/main/java/com/example/backend/service/RescuerMobileService.java \
  backend/src/main/java/com/example/backend/dto/*.java \
  backend/src/main/java/com/example/backend/config/WebMvcConfig.java \
  backend/src/main/java/com/example/backend/config/SecurityConfig.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java \
  .gitignore
git commit -m "feat(backend): add rescuer field media and mobile service"
```

---

### Task 4: RescuerMobileController API

**Files:**
- Create: `controller/RescuerMobileController.java`
- Modify: 无（Service 已有）

**Interfaces:**
- Consumes: `RescuerMobileService`、`DispatchOrderService`、`CustomUserDetails`
- Produces: `/api/mobile/rescuer/**` REST

- [ ] **Step 1: 实现 Controller**

```java
@RestController
@RequestMapping("/api/mobile/rescuer")
@CrossOrigin(origins = "*")
public class RescuerMobileController {
    // 从 SecurityContext 取 CustomUserDetails.getId()
    // 各方法 @PreAuthorize("hasAuthority('rescuer:...')")
    // try/catch RuntimeException -> Result.error(e.getMessage())
}
```

映射与权限严格按 spec 表。`POST /tasks/{id}/media`：`@RequestParam MultipartFile file`、`@RequestParam String bizType`；`DAMAGE` 需 `rescuer:scene`，`PARK` 需 `rescuer:park`（可用代码内校验权限或两个端点；本计划用一入口：`bizType` 分支前检查 authority）。

`POST /tasks/{id}/complete`：校验本人后 `dispatchOrderService.complete(id)`。

- [ ] **Step 2: 编译**

```bash
cd backend && mvn -q -DskipTests compile
```

Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/backend/controller/RescuerMobileController.java
git commit -m "feat(backend): expose rescuer mobile REST API"
```

---

### Task 5: PC 车辆二维码 + 工单 ACCEPTED 展示

**Files:**
- Modify: `frontend/src/views/vehicle/VehicleList.vue`
- Modify: `frontend/src/views/dispatch/DispatchList.vue`
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

**Interfaces:**
- Produces: 列表/弹层展示 `RV:{id}` QR；状态「已接单」；详情对 `ACCEPTED` 可完成/中止

- [ ] **Step 1: Dispatch 状态文案**

```js
const statusLabels = {
  PENDING: '待派单',
  DISPATCHED: '已派单',
  ACCEPTED: '已接单',
  COMPLETED: '已完成',
  ABORTED: '已中止'
}
```

筛选项增加「已接单」。`DispatchDetail`：`ACCEPTED` 显示完成/中止按钮（与 `DISPATCHED` 相同权限操作）。

- [ ] **Step 2: VehicleList 二维码**

复用已有 npm `qrcode`：点击「二维码」→ 弹层 `QR.toDataURL('RV:' + vehicle.id)` → `<img>` + 打印按钮 `window.print` 或浏览器打印当前弹层。

- [ ] **Step 3: 手动打开 PC 页目视确认（无自动化则跳过并在报告注明）**

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/vehicle/VehicleList.vue \
  frontend/src/views/dispatch/DispatchList.vue \
  frontend/src/views/dispatch/DispatchDetail.vue
git commit -m "feat(frontend): show vehicle QR and accepted dispatch status"
```

---

### Task 6: uni-app `mobile-rescuer` 工程与页面

**Files:**
- Create: `mobile-rescuer/`（建议用官方 `degjs/uni-preset-vue` Vue3 Vite 模板手动拷贝最小结构，或 `npx degit dcloudio/uni-preset-vue#vite-ts` 后改 JS；**本仓库用 JS 与 PC 一致**）

最小结构：

```
mobile-rescuer/
  package.json
  vite.config.js
  index.html
  src/
    manifest.json
    pages.json
    App.vue
    main.js
    utils/request.js
    stores/user.js
    api/auth.js
    api/rescuer.js
    pages/login/index.vue
    pages/mine/index.vue
    pages/mine/edit.vue
    pages/mine/bind.vue
    pages/task/list.vue
    pages/task/detail.vue
    pages/task/scene.vue
    pages/task/park.vue
```

**Interfaces:**
- Consumes: Task4 API、`POST /api/auth/login`
- Produces: H5 可演示闭环

- [ ] **Step 1: 脚手架 + `request.js`**

```js
// baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'
// header Authorization Bearer；业务 code!==200 toast；401 清存储跳登录
```

`pages.json` 注册全部页面；tabBar：任务 / 我的。

- [ ] **Step 2: 登录页**

登录成功后检查 `permissions` 含任一 `rescuer:*` 或 `mobile:rescuer`，否则提示无权限。种子用户：init 中施救员账号（查 `init.sql` 用户名，常见 `towdriver`/`rescuer`——**以 init 实际为准**，若无独立账号则用已绑定 `TOW_DRIVER` 的用户）。

- [ ] **Step 3: 任务列表 + 详情动作**

- list：`tab` 切换调 `GET /mobile/rescuer/tasks?tab=`
- detail：accept / reject(弹窗原因) / checkin（`uni.getLocation` → AUTO，失败改 MANUAL 填原因）/ 跳转 scene&park / complete

- [ ] **Step 4: 我的 / 编辑 / 绑车**

绑车：`uni.scanCode`（H5 可能受限则提供手输 `RV:id`）。

- [ ] **Step 5: 现场 / 入库页**

表单提交 scene/park；`uni.chooseImage` 后 `uni.uploadFile` 到 `/mobile/rescuer/tasks/{id}/media`，header 带 token；列表拉取 media 用 `/uploads/` + `filePath` 拼接显示。

- [ ] **Step 6: README**

`mobile-rescuer/README.md`：`npm i`、`npm run dev:h5`、配置 `VITE_API_BASE`、CORS 已通。

- [ ] **Step 7: Commit**

```bash
git add mobile-rescuer
git commit -m "feat(mobile): add uni-app rescuer H5 client"
```

---

### Task 7: 全量回归

**Files:** 无强制代码；可修测试缺口

- [ ] **Step 1: 后端全测**

```bash
cd backend && mvn -q test
```

Expected: 全部 PASS（含既有 78+ 新测）。

- [ ] **Step 2: 对照 spec 验收表 1–8 自检清单写入 `.superpowers/sdd/` 可选报告（若走 SDD）；否则在 PR 描述勾选。

- [ ] **Step 3: Commit（仅当有修复）**

```bash
git commit -m "test: harden rescuer mobile regression"
```

---

## Self-Review

1. **Spec coverage:** 状态机、签到 500m、现场/入库不写扣留表、绑车 RV、PC QR、uni-app 页、权限 53–62、uploads — 均有 Task。
2. **Placeholders:** 无 TBD；种子施救员用户名要求实现时读 `init.sql` 实际值。
3. **Type consistency:** `ACCEPTED`、`AUTO`/`MANUAL`、`DAMAGE`/`PARK`、`RV:{id}` 全文一致；占车计数含 ACCEPTED。
```
