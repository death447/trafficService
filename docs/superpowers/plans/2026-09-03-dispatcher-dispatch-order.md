# 调度员端工单与施救车辆 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为调度员落地救援工单（新建/地图选点/派单/完成/中止）与施救车辆最小 CRUD，含高德选点与按距离推荐空闲车。

**Architecture:** 与现有 RBAC 同构新增 `RescueVehicle`、`DispatchOrder` 两套 Entity→Mapper→Service→Controller；派单状态机与车辆 IDLE/BUSY 在同一事务中更新；前端新增任务/车辆页，高德 JS API 仅用于选点与地图展示，推荐排序在后端 Haversine 完成。

**Tech Stack:** Spring Boot 3.2、MyBatis、Spring Security JWT、MySQL、Vue 3、Pinia、Vue Router、Axios、高德地图 JS API 2.0

**Spec:** `docs/superpowers/specs/2026-09-03-dispatcher-dispatch-order-design.md`

## Global Constraints

- 工单状态仅四态：`PENDING` / `DISPATCHED` / `COMPLETED` / `ABORTED`
- 车辆状态：`IDLE` / `BUSY` / `OFFLINE`；派单只可选 `IDLE`
- 推荐算法本轮仅直线距离；`district_id` 可空预留，不做围栏
- `order_no` 格式：`RO` + `yyyyMMdd` + 4 位当日序号（如 `RO202609030001`）
- 统一响应 `com.example.backend.common.Result`；业务冲突用 `RuntimeException`，Controller catch 后 `Result.error(message)`（与 Role/Permission 一致）
- 权限码固定见 Task 1；`DISPATCHER` 与 `ADMIN` 均需 dispatch + vehicle 权限
- 高德 Key：`VITE_AMAP_KEY`，不入库；无 Key 时允许手填经纬度
- 路由 `/dispatches/new` 必须注册在 `/dispatches/:id` 之前
- 不引入新 UI 库；沿用 `enterprise.css` 与 UserList 弹窗/表格风格
- YAGNI：无片区表、无轨迹、无移动端、无高德服务端代理

## File Structure

### Backend（新建）
- `entity/RescueVehicle.java`、`entity/DispatchOrder.java`
- `mapper/RescueVehicleMapper.java`、`mapper/DispatchOrderMapper.java`
- `service/RescueVehicleService.java`、`service/DispatchOrderService.java`
- `controller/VehicleController.java`、`controller/DispatchController.java`
- `dto/AssignDispatchRequest.java`、`dto/AbortDispatchRequest.java`、`dto/NearbyVehicleVO.java`
- `test/.../service/RescueVehicleServiceTest.java`、`DispatchOrderServiceTest.java`

### Backend（修改）
- 无强制改 SecurityConfig（沿用 JWT + `@PreAuthorize`）

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-03_dispatch_vehicle.sql`（已有库增量）

### Frontend（新建）
- `src/api/vehicle.js`、`src/api/dispatch.js`
- `src/utils/amap.js`
- `src/views/vehicle/VehicleList.vue`
- `src/views/dispatch/DispatchList.vue`、`DispatchCreate.vue`、`DispatchDetail.vue`
- `frontend/.env.example`

### Frontend（修改）
- `src/router/index.js`、`src/App.vue`、`src/views/Home.vue`

---

### Task 1: 数据库表、权限与种子数据

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-03_dispatch_vehicle.sql`
- Test: MySQL 执行后 `SHOW TABLES` / 查 permission / 登录调度员

**Interfaces:**
- Produces: 表 `rescue_vehicle`、`dispatch_order`；权限 id `20–30`；种子车；用户 `dispatcher` / `admin123`（BCrypt 与现 admin 相同哈希）

- [ ] **Step 1: 在 `init.sql` 的 DROP 段增加两表**

在现有 `DROP TABLE IF EXISTS` 列表最前增加（注意外键依赖顺序：先工单后车辆）：

```sql
DROP TABLE IF EXISTS `dispatch_order`;
DROP TABLE IF EXISTS `rescue_vehicle`;
```

- [ ] **Step 2: 在 `role_permission` 建表之后追加两表 DDL**

使用 spec 中的完整 `CREATE TABLE rescue_vehicle` 与 `CREATE TABLE dispatch_order`（字段名、注释、索引与 spec 一致）。

- [ ] **Step 3: 扩展 permission 插入（保留 1–19，追加 20–30）**

将原 permission INSERT 扩展为同时包含：

```sql
(16, '派单管理', 'dispatch:manage', 'MODULE', 0, 4),
(17, '事故处理', 'accident:manage', 'MODULE', 0, 5),
(18, '救援执行', 'rescue:manage', 'MODULE', 0, 6),
(19, '停车场管理', 'parking:manage', 'MODULE', 0, 7),
(20, '工单查询', 'dispatch:query', 'BUTTON', 16, 1),
(21, '工单新增', 'dispatch:add', 'BUTTON', 16, 2),
(22, '工单编辑', 'dispatch:edit', 'BUTTON', 16, 3),
(23, '工单派单', 'dispatch:dispatch', 'BUTTON', 16, 4),
(24, '工单完成', 'dispatch:complete', 'BUTTON', 16, 5),
(25, '工单中止', 'dispatch:abort', 'BUTTON', 16, 6),
(26, '施救车辆', 'vehicle:manage', 'MODULE', 0, 8),
(27, '车辆查询', 'vehicle:query', 'BUTTON', 26, 1),
(28, '车辆新增', 'vehicle:add', 'BUTTON', 26, 2),
(29, '车辆编辑', 'vehicle:edit', 'BUTTON', 26, 3),
(30, '车辆删除', 'vehicle:delete', 'BUTTON', 26, 4);
```

- [ ] **Step 4: 改写角色授权**

替换原 ADMIN `1 AND 15` 与 DISPATCHER 单条为：

```sql
-- ADMIN: 系统管理 1-15 + 派单模块及按钮 16,20-25 + 车辆 26-30
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id = 16 OR id BETWEEN 20 AND 30;

-- DISPATCHER: 派单 + 车辆
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission` WHERE id = 16 OR id BETWEEN 20 AND 30;

-- TRAFFIC_POLICE / TOW_DRIVER / PARKING_ADMIN 保持 17/18/19
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 17);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (3, 18);
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (4, 19);
```

- [ ] **Step 5: 种子车辆 + 调度员账号**

在 admin 用户插入之后追加（密码哈希与 admin 相同 = `admin123`）：

```sql
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('dispatcher', 'dispatcher@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000001', '调度员演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (2, 2);

INSERT INTO `rescue_vehicle`
(`plate_no`, `vehicle_type`, `color`, `equipment`, `longitude`, `latitude`, `status`, `remark`) VALUES
('粤B·救援01', 'TOW', '黄', '拖车绳', 114.0578680, 22.5430990, 'IDLE', '深圳市民中心附近'),
('粤B·救援02', 'TOW', '白', '液压绞盘', 114.0859470, 22.5470000, 'IDLE', '稍偏东'),
('粤B·救援03', 'CLEARANCE', '蓝', '清障设备', 114.0300000, 22.5400000, 'IDLE', '稍偏西'),
('粤B·救援04', 'TOW', '红', NULL, 114.0578680, 22.5430990, 'OFFLINE', '离线样例');
```

- [ ] **Step 6: 编写增量脚本 `database/migrate_2026-09-03_dispatch_vehicle.sql`**

对已有库：`CREATE TABLE IF NOT EXISTS` 两表；`INSERT IGNORE` 权限 20–30；删除并重建 DISPATCHER/ADMIN 相关 `role_permission` 中 dispatch/vehicle 部分（或按 id 补插）；插入种子车与 dispatcher 用户（若用户名不存在）。脚本顶部用注释说明：新环境优先跑完整 `init.sql`。

- [ ] **Step 7: 本地执行验证**

```bash
mysql -u root -p < database/init.sql
mysql -u root -p -e "USE vue_springboot_system; SHOW TABLES; SELECT permission_code FROM permission WHERE id>=16; SELECT plate_no,status FROM rescue_vehicle;"
```

Expected: 含 `rescue_vehicle`、`dispatch_order`；权限含 `dispatch:query`、`vehicle:manage`；至少 3 辆 IDLE。

- [ ] **Step 8: Commit**

```bash
git add database/init.sql database/migrate_2026-09-03_dispatch_vehicle.sql
git commit -m "feat(db): add dispatch_order, rescue_vehicle, and dispatcher permissions"
```

---

### Task 2: 施救车辆后端（Mapper + Service TDD + Controller）

**Files:**
- Create: `backend/src/main/java/com/example/backend/entity/RescueVehicle.java`
- Create: `backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java`
- Create: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`（本任务先写 `countActiveByVehicleId`，其余方法 Task 3 补全）
- Create: `backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java`
- Create: `backend/src/main/java/com/example/backend/service/RescueVehicleService.java`
- Create: `backend/src/main/java/com/example/backend/controller/VehicleController.java`
- Test: `backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java`

**Interfaces:**
- Produces:
  - `RescueVehicleService.create/update/delete/get/list/findNearby`
  - `GET/POST/PUT/DELETE /api/vehicle...`、`GET /api/vehicle/nearby`
  - `NearbyVehicleVO { RescueVehicle vehicle; double distanceMeters; }`
- Consumes: `DispatchOrderMapper.countActiveByVehicleId(Long vehicleId)` → 统计 `status IN ('PENDING','DISPATCHED')` 且 `vehicle_id = ?` 的行数

- [ ] **Step 1: 写失败单测 `RescueVehicleServiceTest`**

```java
package com.example.backend.service;

import com.example.backend.dto.NearbyVehicleVO;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescueVehicleServiceTest {

    @Mock RescueVehicleMapper vehicleMapper;
    @Mock DispatchOrderMapper dispatchOrderMapper;
    @InjectMocks RescueVehicleService service;

    @Test
    void deleteRejectsWhenActiveOrdersExist() {
        when(dispatchOrderMapper.countActiveByVehicleId(1L)).thenReturn(2);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteVehicle(1L));
        assertTrue(ex.getMessage().contains("进行中"));
        verify(vehicleMapper, never()).deleteById(any());
    }

    @Test
    void nearbySortsIdleByDistanceAscending() {
        RescueVehicle near = vehicle(1L, "粤B1", "114.058", "22.543");
        RescueVehicle far = vehicle(2L, "粤B2", "114.100", "22.600");
        when(vehicleMapper.findByStatus("IDLE")).thenReturn(List.of(far, near));

        List<NearbyVehicleVO> list = service.findNearby(
                new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getVehicle().getId());
        assertTrue(list.get(0).getDistanceMeters() < list.get(1).getDistanceMeters());
    }

    @Test
    void createRejectsDuplicatePlate() {
        RescueVehicle v = new RescueVehicle();
        v.setPlateNo("粤B·救援01");
        when(vehicleMapper.findByPlateNo("粤B·救援01")).thenReturn(new RescueVehicle());
        assertThrows(RuntimeException.class, () -> service.createVehicle(v));
    }

    private static RescueVehicle vehicle(Long id, String plate, String lng, String lat) {
        RescueVehicle v = new RescueVehicle();
        v.setId(id);
        v.setPlateNo(plate);
        v.setStatus("IDLE");
        v.setLongitude(new BigDecimal(lng));
        v.setLatitude(new BigDecimal(lat));
        return v;
    }
}
```

- [ ] **Step 2: 运行单测确认失败**

```bash
cd backend && mvn -q -Dtest=RescueVehicleServiceTest test
```

Expected: FAIL（类不存在或方法不存在）

- [ ] **Step 3: 实现 Entity / DTO / Mapper**

`RescueVehicle` 字段与表一致（Lombok `@Data`，`BigDecimal` 经纬度，`LocalDateTime` 时间）。

`NearbyVehicleVO`：`private RescueVehicle vehicle; private double distanceMeters;`

`RescueVehicleMapper` 注解 SQL：`findById`、`findAll`、`findByStatus`、`findByPlateNo`、`insert`、`update`、`deleteById`；可选 keyword/type 筛选用动态条件或 Java 侧过滤（本轮允许 Service 内 `findAll` 后 filter）。

`DispatchOrderMapper` 本任务至少：

```java
@Select("SELECT COUNT(*) FROM dispatch_order WHERE vehicle_id = #{vehicleId} AND status IN ('PENDING','DISPATCHED')")
int countActiveByVehicleId(Long vehicleId);
```

- [ ] **Step 4: 实现 `RescueVehicleService`**

要点：
- `createVehicle`：查重车牌；默认 `status=IDLE`
- `deleteVehicle`：`countActiveByVehicleId > 0` 则 `throw new RuntimeException("该车辆有进行中的工单，无法删除")`
- `findNearby(lng, lat, limit)`：取全部 `IDLE` 且经纬度非空；Haversine 算米；升序；`limit` 默认 20、最大 50
- `markBusy` / `markIdle`：供 Task 3 调用的包内或 public 方法（`updateStatus(id, status)`）

Haversine 参考：

```java
private static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
    double R = 6371000.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.sqrt(a));
}
```

- [ ] **Step 5: 实现 `VehicleController`**

```java
@RestController
@RequestMapping("/api/vehicle")
@CrossOrigin(origins = "*")
public class VehicleController {
    // list / {id} / POST / PUT / DELETE 对应 PreAuthorize vehicle:query|add|edit|delete
    // GET /nearby?lng=&lat=&limit=  → @PreAuthorize("hasAuthority('dispatch:dispatch')")
}
```

捕获 `RuntimeException` 返回 `Result.error(e.getMessage())`。

- [ ] **Step 6: 再跑单测**

```bash
cd backend && mvn -q -Dtest=RescueVehicleServiceTest test
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/RescueVehicle.java \
  backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java \
  backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java \
  backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java \
  backend/src/main/java/com/example/backend/service/RescueVehicleService.java \
  backend/src/main/java/com/example/backend/controller/VehicleController.java \
  backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java
git commit -m "feat(backend): add rescue vehicle CRUD and nearby query"
```

---

### Task 3: 救援工单后端（状态机 TDD + API）

**Files:**
- Create: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`
- Create: `backend/src/main/java/com/example/backend/dto/AssignDispatchRequest.java`
- Create: `backend/src/main/java/com/example/backend/dto/AbortDispatchRequest.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`（补全 CRUD / 按日计数）
- Create: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Create: `backend/src/main/java/com/example/backend/controller/DispatchController.java`
- Test: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`

**Interfaces:**
- Produces:
  - `DispatchOrderService.create(order, dispatcherId)` → PENDING + 生成 orderNo
  - `update` 仅 PENDING
  - `assign(id, vehicleId, rescuerId)` → DISPATCHED + 车 BUSY
  - `complete(id)` / `abort(id, reason)`
  - REST `/api/dispatch/**` 权限见 spec
- Consumes: `RescueVehicleService.getById`、`updateStatus`；`RescueVehicleMapper` 或 Service 读车状态

- [ ] **Step 1: 写失败单测 `DispatchOrderServiceTest`**

覆盖至少：
1. `assign`：PENDING + 车 IDLE → 工单 DISPATCHED、调 `markBusy`
2. `assign`：非 PENDING → 抛错
3. `assign`：车非 IDLE → 抛错
4. `complete`：DISPATCHED → COMPLETED + `markIdle`（当无其他 DISPATCHED 占用）
5. `abort`：PENDING 无需放车；DISPATCHED 放车
6. `update`：非 PENDING 抛错

Mock：`DispatchOrderMapper`、`RescueVehicleMapper`（或 `RescueVehicleService`）。若 Service 依赖 `RescueVehicleService`，则 mock 它。

示例骨架：

```java
@Test
void assignMovesOrderAndMarksVehicleBusy() {
    DispatchOrder order = new DispatchOrder();
    order.setId(9L);
    order.setStatus("PENDING");
    RescueVehicle vehicle = new RescueVehicle();
    vehicle.setId(3L);
    vehicle.setStatus("IDLE");
    when(dispatchOrderMapper.findById(9L)).thenReturn(order);
    when(rescueVehicleService.requireIdle(3L)).thenReturn(vehicle);

    service.assign(9L, 3L, null);

    assertEquals("DISPATCHED", order.getStatus());
    assertEquals(3L, order.getVehicleId());
    verify(rescueVehicleService).markBusy(3L);
    verify(dispatchOrderMapper).update(order);
}
```

（实现时可把 `requireIdle` / `markBusy` 做成 `RescueVehicleService` 的明确方法，避免测试里散落状态字符串。）

- [ ] **Step 2: 跑测确认失败**

```bash
cd backend && mvn -q -Dtest=DispatchOrderServiceTest test
```

Expected: FAIL

- [ ] **Step 3: 补全 `DispatchOrder` 实体与 Mapper**

实体字段对齐表；可加非表字段：`vehiclePlate`、`dispatcherName`、`rescuerName` 供详情展示（Service 组装）。

Mapper 方法：
- `findById`、`findAll`、筛选 list（Java filter 亦可）
- `insert`（useGeneratedKeys）
- `update`
- `countByOrderNoPrefix(String prefix)` — 用于当日序号，如 prefix=`RO20260903`
- `countDispatchedByVehicleId(Long vehicleId)` — `status='DISPATCHED' AND vehicle_id=?`（complete/abort 放车判断）

- [ ] **Step 4: 实现 `DispatchOrderService`**

```java
@Transactional
public void assign(Long orderId, Long vehicleId, Long rescuerId) { ... }

@Transactional
public void complete(Long orderId) { ... }

@Transactional
public void abort(Long orderId, String abortReason) { ... }
```

`generateOrderNo()`：
```java
String prefix = "RO" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
int seq = dispatchOrderMapper.countByOrderNoPrefix(prefix) + 1;
return prefix + String.format("%04d", seq);
```

`releaseVehicleIfUnused(Long vehicleId)`：若 `vehicleId!=null` 且 `countDispatchedByVehicleId==0` 则 `markIdle`。

从 Security 取当前用户仅在 Controller：  
`(CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()` → `getId()`。

- [ ] **Step 5: DTO + `DispatchController`**

```java
public class AssignDispatchRequest {
    @NotNull private Long vehicleId;
    private Long rescuerId;
}
public class AbortDispatchRequest {
    @NotBlank private String abortReason;
}
```

映射：
- `GET /api/dispatch/list` → `dispatch:query`
- `GET /api/dispatch/{id}` → `dispatch:query`
- `POST /api/dispatch` → `dispatch:add`（body 为工单字段，忽略客户端 status/dispatcherId）
- `PUT /api/dispatch/{id}` → `dispatch:edit`
- `POST /api/dispatch/{id}/assign` → `dispatch:dispatch`
- `POST /api/dispatch/{id}/complete` → `dispatch:complete`
- `POST /api/dispatch/{id}/abort` → `dispatch:abort`

- [ ] **Step 6: 跑通单测**

```bash
cd backend && mvn -q -Dtest=DispatchOrderServiceTest,RescueVehicleServiceTest test
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/DispatchOrder.java \
  backend/src/main/java/com/example/backend/dto/AssignDispatchRequest.java \
  backend/src/main/java/com/example/backend/dto/AbortDispatchRequest.java \
  backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java \
  backend/src/main/java/com/example/backend/service/DispatchOrderService.java \
  backend/src/main/java/com/example/backend/service/RescueVehicleService.java \
  backend/src/main/java/com/example/backend/controller/DispatchController.java \
  backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java
git commit -m "feat(backend): add dispatch order APIs and state machine"
```

---

### Task 4: 前端车辆管理页与路由壳

**Files:**
- Create: `frontend/src/api/vehicle.js`
- Create: `frontend/src/views/vehicle/VehicleList.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/App.vue`（侧栏 + pageTitle）
- Modify: `frontend/src/views/Home.vue`（入口卡片，可选本任务或 Task 6）

**Interfaces:**
- Produces: `listVehicles`、`createVehicle`、`updateVehicle`、`deleteVehicle`；路由 `/vehicles`；侧栏「施救车辆」

- [ ] **Step 1: `api/vehicle.js`**

```js
import request from '@/utils/request'

export function listVehicles(params) {
  return request.get('/vehicle/list', { params })
}
export function getVehicle(id) {
  return request.get(`/vehicle/${id}`)
}
export function createVehicle(data) {
  return request.post('/vehicle', data)
}
export function updateVehicle(id, data) {
  return request.put(`/vehicle/${id}`, data)
}
export function deleteVehicle(id) {
  return request.delete(`/vehicle/${id}`)
}
export function nearbyVehicles(params) {
  return request.get('/vehicle/nearby', { params })
}
```

- [ ] **Step 2: 实现 `VehicleList.vue`**

对齐 `UserList.vue`：表格列车牌/类型/状态/经纬度/操作；弹窗表单字段 `plateNo, vehicleType, color, equipment, longitude, latitude, status, remark`；按钮权限 `vehicle:add|edit|delete`。

状态选项：`IDLE` / `BUSY` / `OFFLINE`。类型：`TOW` / `CLEARANCE` / `OTHER`。

- [ ] **Step 3: 注册路由与侧栏**

在 `router/index.js` 增加：

```js
{
  path: '/vehicles',
  name: 'VehicleList',
  component: () => import('../views/vehicle/VehicleList.vue'),
  meta: { permissions: ['vehicle:manage'] }
}
```

`App.vue` 在「系统管理」前或后增加「业务」分区：

```html
<p class="nav-section">业务调度</p>
<router-link v-auth="'dispatch:manage'" to="/dispatches" ...>任务管理</router-link>
<router-link v-auth="'vehicle:manage'" to="/vehicles" ...>施救车辆</router-link>
```

（`/dispatches` 页可在 Task 5 再补，本任务先挂车辆；任务链接可先加上，缺页时下一任务补齐。）

`pageTitle` map 增加 `'/vehicles': '施救车辆'`。

- [ ] **Step 4: 手工验证**

用 `dispatcher` 登录 → 侧栏可见施救车辆 → CRUD 一条车。  
用无权限账号（若有）→ 403。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/vehicle.js frontend/src/views/vehicle/VehicleList.vue \
  frontend/src/router/index.js frontend/src/App.vue
git commit -m "feat(frontend): add rescue vehicle management page"
```

---

### Task 5: 高德工具 + 工单列表/新建

**Files:**
- Create: `frontend/src/utils/amap.js`
- Create: `frontend/src/api/dispatch.js`
- Create: `frontend/src/views/dispatch/DispatchList.vue`
- Create: `frontend/src/views/dispatch/DispatchCreate.vue`
- Create: `frontend/.env.example`
- Modify: `frontend/src/router/index.js`、`App.vue` pageTitle

**Interfaces:**
- Produces: `loadAmap()` → Promise&lt;AMap&gt;；选点回调 `{ lng, lat, address }`；`/dispatches`、`/dispatches/new`

- [ ] **Step 1: `.env.example`**

```env
VITE_AMAP_KEY=
VITE_AMAP_SECURITY_CODE=
```

说明：若高德控制台启用了安全密钥，在 `amap.js` 加载前设置 `window._AMapSecurityConfig = { securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE }`。

- [ ] **Step 2: 实现 `utils/amap.js`**

```js
const KEY = import.meta.env.VITE_AMAP_KEY

export function hasAmapKey() {
  return Boolean(KEY)
}

let loading
export function loadAmap() {
  if (!KEY) return Promise.reject(new Error('缺少 VITE_AMAP_KEY'))
  if (window.AMap) return Promise.resolve(window.AMap)
  if (loading) return loading
  if (import.meta.env.VITE_AMAP_SECURITY_CODE) {
    window._AMapSecurityConfig = {
      securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE
    }
  }
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${KEY}`
    script.onload = () => resolve(window.AMap)
    script.onerror = () => reject(new Error('高德地图加载失败'))
    document.head.appendChild(script)
  })
  return loading
}

/** 在 container 元素上创建地图；点击设标记并逆地理（若 Geocoder 可用） */
export async function createPickerMap(container, { lng, lat, onPicked }) {
  const AMap = await loadAmap()
  const center = lng && lat ? [Number(lng), Number(lat)] : [114.057868, 22.543099]
  const map = new AMap.Map(container, { zoom: 13, center })
  let marker
  map.on('click', (e) => {
    const { lng: x, lat: y } = e.lnglat
    if (!marker) marker = new AMap.Marker({ position: [x, y], map })
    else marker.setPosition([x, y])
    const done = (address) => onPicked?.({ lng: x, lat: y, address: address || '' })
    if (AMap.plugin) {
      AMap.plugin('AMap.Geocoder', () => {
        const geocoder = new AMap.Geocoder()
        geocoder.getAddress([x, y], (status, result) => {
          if (status === 'complete' && result.regeocode) {
            done(result.regeocode.formattedAddress)
          } else done('')
        })
      })
    } else done('')
  })
  return map
}
```

- [ ] **Step 3: `api/dispatch.js`**

```js
import request from '@/utils/request'

export function listDispatches(params) {
  return request.get('/dispatch/list', { params })
}
export function getDispatch(id) {
  return request.get(`/dispatch/${id}`)
}
export function createDispatch(data) {
  return request.post('/dispatch', data)
}
export function updateDispatch(id, data) {
  return request.put(`/dispatch/${id}`, data)
}
export function assignDispatch(id, data) {
  return request.post(`/dispatch/${id}/assign`, data)
}
export function completeDispatch(id) {
  return request.post(`/dispatch/${id}/complete`)
}
export function abortDispatch(id, data) {
  return request.post(`/dispatch/${id}/abort`, data)
}
```

- [ ] **Step 4: `DispatchList.vue`**

筛选：`orderNo`、`status`、`accidentAddress`；表格展示单号/地点/状态/调度员/创建时间；「新建工单」→ `/dispatches/new`（`v-auth="'dispatch:add'"`）；行点击或「详情」→ `/dispatches/:id`。

状态徽章文案：`PENDING=待派单`、`DISPATCHED=处理中`、`COMPLETED=已完成`、`ABORTED=已中止`。

- [ ] **Step 5: `DispatchCreate.vue`**

表单：`rescueReason`、`accidentAddress`、`longitude`、`latitude`。  
有 Key：挂载后 `createPickerMap`，选点回填。  
无 Key：顶部提示「未配置 VITE_AMAP_KEY，请手填坐标」，仍可提交。  
提交 `createDispatch` 成功后 `router.push(\`/dispatches/${id}\`)` 或列表。

- [ ] **Step 6: 路由（注意顺序）**

```js
{ path: '/dispatches', name: 'DispatchList', component: () => import('../views/dispatch/DispatchList.vue'), meta: { permissions: ['dispatch:manage'] } },
{ path: '/dispatches/new', name: 'DispatchCreate', component: () => import('../views/dispatch/DispatchCreate.vue'), meta: { permissions: ['dispatch:add'] } },
{ path: '/dispatches/:id', name: 'DispatchDetail', component: () => import('../views/dispatch/DispatchDetail.vue'), meta: { permissions: ['dispatch:query'] } },
```

本任务可先放一个占位 `DispatchDetail.vue`（仅显示「加载中 / 下一任务实现」）或直接进入 Task 6 一并提交详情——**推荐本任务占位最小页，Task 6 补全**，以便本任务可独立提交可点进新建的链路。

- [ ] **Step 7: 验证新建**

配置或不配置 Key 均可建单；列表出现 `PENDING`。

- [ ] **Step 8: Commit**

```bash
git add frontend/src/utils/amap.js frontend/src/api/dispatch.js \
  frontend/src/views/dispatch/DispatchList.vue \
  frontend/src/views/dispatch/DispatchCreate.vue \
  frontend/src/views/dispatch/DispatchDetail.vue \
  frontend/src/router/index.js frontend/.env.example frontend/src/App.vue
git commit -m "feat(frontend): add dispatch list/create with AMap picker"
```

---

### Task 6: 工单详情派单/完成/中止 + Home 入口

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`
- Modify: `frontend/src/views/Home.vue`
- Modify: `frontend/src/App.vue` pageTitle（含动态详情标题可选）

**Interfaces:**
- Consumes: `getDispatch`、`nearbyVehicles`、`assignDispatch`、`completeDispatch`、`abortDispatch`
- Produces: 完整调度员闭环 UI

- [ ] **Step 1: 实现 `DispatchDetail.vue`**

布局：
1. 工单信息区（单号、状态、地址、坐标、原因、车辆）
2. `PENDING`：地图（事故点 marker）+ 附近空闲车列表（调用 `nearbyVehicles({ lng, lat, limit: 20 })`）；点选车辆后「确认派单」→ `assignDispatch(id, { vehicleId })`
3. `DISPATCHED`：按钮「完成」（`dispatch:complete`）、「中止」（`dispatch:abort`）
4. 中止弹窗：必填 `abortReason`
5. `COMPLETED` / `ABORTED`：只读

无坐标或无 Key：附近车仍用列表（后端排序）；地图区显示提示。

- [ ] **Step 2: Home 卡片**

在 `Home.vue` 增加权限门控卡片跳转 `/dispatches`、`/vehicles`（文案：任务管理 / 施救车辆）。

- [ ] **Step 3: 端到端手工验收清单**

1. `dispatcher` / `admin123` 登录，permissions 含 `dispatch:*` 与 `vehicle:*`
2. 车辆列表可见种子车
3. 新建工单（地图或手填深圳坐标）→ PENDING
4. 详情附近车距离近的在前 → 派单 → 车变 BUSY、工单 DISPATCHED
5. 完成 → COMPLETED、车回 IDLE
6. 另建一单 → 中止（PENDING 或派单后）→ ABORTED
7. 删除仍被 DISPATCHED 占用的车 → 后端拒绝
8. `admin` 亦可访问；无权限用户 403

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/dispatch/DispatchDetail.vue frontend/src/views/Home.vue frontend/src/App.vue
git commit -m "feat(frontend): complete dispatch detail assign/complete/abort flow"
```

---

## Self-Review (plan vs spec)

| Spec 要求 | Task |
|-----------|------|
| `rescue_vehicle` / `dispatch_order` 表 | Task 1 |
| 权限 dispatch/vehicle + DISPATCHER/ADMIN | Task 1 |
| 车辆 CRUD + nearby | Task 2 |
| 工单状态机 assign/complete/abort | Task 3 |
| 前端车辆页 | Task 4 |
| 高德选点 + 列表/新建 | Task 5 |
| 详情派单/完成/中止 | Task 6 |
| 种子车、调度员账号 | Task 1 |
| `district_id` 预留、无围栏 | Task 1 DDL（无围栏任务） |
| `.env.example` / 无 Key 可手填 | Task 5 |
| 后端单测最低集 | Task 2–3 |

无 TBD/TODO 占位；类型名全程统一为 `RescueVehicle`、`DispatchOrder`、`NearbyVehicleVO`。
