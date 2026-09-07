# 停车场与扣留车辆 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 PC 停车场 CRUD 与扣留车辆出入库主线（入库 / 编辑 / 出库 / 清理 / 浏览器吊牌打印 + QR），供 ADMIN 与 PARKING_ADMIN 使用。

**Architecture:** 与片区/车辆同构新增 `ParkingLot`、`DetainedVehicle` 两套 Entity→Mapper→Service→Controller；状态机与同牌在库校验在 Service；前端两个列表页 + 独立吊牌打印路由，QR 用 npm 包 `qrcode`。

**Tech Stack:** Spring Boot 3.2、MyBatis、Spring Security JWT、MySQL、JUnit5+Mockito、Vue 3、Vue Router、Axios、`qrcode`

**Spec:** `docs/superpowers/specs/2026-09-07-parking-detain-design.md`

## Global Constraints

- 停车场状态仅：`ENABLED` / `DISABLED`
- 扣留车状态仅：`IN_YARD` → `OUT` → `CLEARED`（单向）
- 同牌全局同时最多一条 `IN_YARD`；`plate_no` 入库/编辑前 `trim`，精确匹配不做大小写折叠
- `detain_no` 格式：`DV` + `yyyyMMdd` + 至少 4 位当日序号（左补 0）
- 可选 `dispatch_order_id`：传则须存在；不强制从工单入库
- 禁用停车场不可作为入库或编辑换场目标
- 有任意 `IN_YARD` 扣留车时禁止物理删除停车场
- 统一响应 `Result`；业务冲突 `RuntimeException`，Controller `catch` 后 `Result.error(message)`
- 权限 id：`42–52`（见 Task 1）；`parking:manage` 已有 id=`19`；授予 `ADMIN`(role_id=5) 与 `PARKING_ADMIN`(role_id=4)
- YAGNI：无统计导出、无照片/PDF、无移动端、不改派单/施救车辆
- 不引入新 UI 库；沿用 `enterprise.css`；列表假分页与现有模块一致

## File Structure

### Backend（新建）
- `entity/ParkingLot.java`、`entity/DetainedVehicle.java`
- `mapper/ParkingLotMapper.java`、`mapper/DetainedVehicleMapper.java`
- `service/ParkingLotService.java`、`service/DetainedVehicleService.java`
- `controller/ParkingLotController.java`、`controller/DetainedVehicleController.java`
- `dto/ParkingLotRequest.java`、`dto/DetainInRequest.java`、`dto/DetainUpdateRequest.java`
- `test/.../service/ParkingLotServiceTest.java`、`test/.../service/DetainedVehicleServiceTest.java`

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-07_parking_detain.sql`

### Frontend（新建）
- `src/api/parking.js`、`src/api/detain.js`
- `src/views/parking/ParkingList.vue`
- `src/views/detain/DetainedVehicleList.vue`
- `src/views/detain/HangtagPrint.vue`

### Frontend（修改）
- `package.json`（依赖 `qrcode`）
- `src/router/index.js`、`src/App.vue`、`src/views/Home.vue`

---

### Task 1: 数据库表、权限与种子数据

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-07_parking_detain.sql`
- Test: MySQL 执行后 `SHOW TABLES` / 查 permission / 登录 `parkingadmin`

**Interfaces:**
- Produces: 表 `parking_lot`、`detained_vehicle`；权限 id `42–52`；种子停车场 ≥2；扣留车至少 1 条 `IN_YARD`、1 条 `OUT`；用户 `parkingadmin` / `admin123`

- [ ] **Step 1: 在 `init.sql` DROP 段最前增加**

```sql
DROP TABLE IF EXISTS `detained_vehicle`;
DROP TABLE IF EXISTS `parking_lot`;
```

（放在 `duty_schedule` / `district` 之前或之后均可；无物理 FK。）

- [ ] **Step 2: 在现有业务表 DDL 之后追加两表**

使用 spec 中完整 `CREATE TABLE parking_lot` 与 `CREATE TABLE detained_vehicle`（字段、索引、注释一致）。

- [ ] **Step 3: 扩展 permission（保留 1–41，追加 42–52）**

在现有 permission INSERT 末尾 `41` 之后追加：

```sql
(42, '停车场查询', 'parking:query', 'BUTTON', 19, 1),
(43, '停车场新增', 'parking:add', 'BUTTON', 19, 2),
(44, '停车场编辑', 'parking:edit', 'BUTTON', 19, 3),
(45, '停车场删除', 'parking:delete', 'BUTTON', 19, 4),
(46, '扣留车辆', 'detain:manage', 'MODULE', 0, 11),
(47, '扣留查询', 'detain:query', 'BUTTON', 46, 1),
(48, '扣留入库', 'detain:add', 'BUTTON', 46, 2),
(49, '扣留编辑', 'detain:edit', 'BUTTON', 46, 3),
(50, '扣留出库', 'detain:out', 'BUTTON', 46, 4),
(51, '扣留清理', 'detain:clear', 'BUTTON', 46, 5),
(52, '吊牌打印', 'detain:print', 'BUTTON', 46, 6);
```

- [ ] **Step 4: 改写 ADMIN / PARKING_ADMIN 授权**

将 ADMIN 的 permission 选择改为包含 `19` 与 `42–52`（保留原有 1–15、16、20–41）：

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id = 16 OR id = 19 OR id BETWEEN 20 AND 52;
```

将原 `PARKING_ADMIN` 单条 `(4, 19)` 改为：

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 4, id FROM `permission` WHERE id = 19 OR id BETWEEN 42 AND 52;
```

- [ ] **Step 5: 种子用户与样例数据**

在用户种子后追加（密码同 `admin123` 的 BCrypt）：

```sql
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('parkingadmin', 'parking@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000004', '停车场演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT id, 4 FROM `user` WHERE username = 'parkingadmin';
```

样例停车场与扣留车（`detain_no` 手写固定值即可）：

```sql
INSERT INTO `parking_lot` (`name`, `code`, `address`, `contact_name`, `contact_phone`, `status`, `remark`) VALUES
('福田扣留场', 'PK-FT-01', '深圳市福田区示例路1号', '张管', '13900000001', 'ENABLED', '主场'),
('南山扣留场', 'PK-NS-01', '深圳市南山区示例路2号', '李管', '13900000002', 'ENABLED', NULL),
('罗湖备用场', 'PK-LH-00', '深圳市罗湖区示例路3号', NULL, NULL, 'DISABLED', '禁用样例');

INSERT INTO `detained_vehicle`
(`detain_no`, `plate_no`, `vehicle_type`, `parking_lot_id`, `dispatch_order_id`, `detain_dept`, `status`,
 `in_time`, `out_time`, `cleared_at`, `operator_in_id`, `operator_out_id`, `remark`) VALUES
('DV202609070001', '粤B·扣留01', '小型车', 1, NULL, '福田交警大队', 'IN_YARD',
 NOW(), NULL, NULL, 1, NULL, '在库样例'),
('DV202609070002', '粤B·扣留02', '货车', 1, NULL, '南山交警大队', 'OUT',
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, 1, '已出库样例');
```

- [ ] **Step 6: 编写增量脚本 `migrate_2026-09-07_parking_detain.sql`**

对已有库：`CREATE TABLE IF NOT EXISTS` 两表；`INSERT IGNORE` 权限 42–52；为 role 4/5 补授权；种子停车场/扣留车/用户用 `INSERT IGNORE` 或按 username/code 判重。

- [ ] **Step 7: 本地执行迁移（或重跑 init）并抽查**

```sql
SHOW TABLES LIKE 'parking%';
SHOW TABLES LIKE 'detain%';
SELECT id, permission_code FROM permission WHERE id BETWEEN 42 AND 52;
SELECT username FROM user WHERE username = 'parkingadmin';
```

Expected: 两表存在；权限 11 条；用户存在。

- [ ] **Step 8: Commit**

```bash
git add database/init.sql database/migrate_2026-09-07_parking_detain.sql
git commit -m "chore(db): add parking lot and detained vehicle schema"
```

---

### Task 2: ParkingLot 后端（TDD）

**Files:**
- Create: `backend/src/main/java/com/example/backend/entity/ParkingLot.java`
- Create: `backend/src/main/java/com/example/backend/dto/ParkingLotRequest.java`
- Create: `backend/src/main/java/com/example/backend/mapper/ParkingLotMapper.java`
- Create: `backend/src/main/java/com/example/backend/service/ParkingLotService.java`
- Create: `backend/src/main/java/com/example/backend/controller/ParkingLotController.java`
- Test: `backend/src/test/java/com/example/backend/service/ParkingLotServiceTest.java`

**Interfaces:**
- Consumes: `DetainedVehicleMapper.countInYardByParkingLotId(Long)`（Task 3 先在本 Task 用 Mockito stub；若尚未创建接口，本 Task 内先定义 Mapper 方法签名，Task 3 实现）
- Produces:
  - `ParkingLotService.list(String keyword, String status): List<ParkingLot>`
  - `findById(Long): ParkingLot`
  - `create(ParkingLotRequest): boolean`
  - `update(Long, ParkingLotRequest): boolean`
  - `delete(Long): boolean` — 有在库车抛 `RuntimeException("该停车场仍有在库扣留车辆，请先出库或改为禁用")`
  - `requireEnabled(Long id): ParkingLot` — 不存在或非 ENABLED 抛「停车场不存在或已禁用」
  - Controller base `/api/parking`，权限码见 Global Constraints

- [ ] **Step 1: 写失败单测 `ParkingLotServiceTest`**

```java
@ExtendWith(MockitoExtension.class)
class ParkingLotServiceTest {
    @Mock ParkingLotMapper parkingLotMapper;
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @InjectMocks ParkingLotService service;

    @Test
    void deleteBlockedWhenInYardVehiclesExist() {
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        when(parkingLotMapper.findById(1L)).thenReturn(lot);
        when(detainedVehicleMapper.countInYardByParkingLotId(1L)).thenReturn(2);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("在库"));
        verify(parkingLotMapper, never()).deleteById(any());
    }

    @Test
    void requireEnabledRejectsDisabled() {
        ParkingLot lot = new ParkingLot();
        lot.setId(2L);
        lot.setStatus("DISABLED");
        when(parkingLotMapper.findById(2L)).thenReturn(lot);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.requireEnabled(2L));
        assertTrue(ex.getMessage().contains("禁用") || ex.getMessage().contains("不存在"));
    }

    @Test
    void createRejectsDuplicateCode() {
        ParkingLotRequest req = new ParkingLotRequest();
        req.setName("A");
        req.setCode("PK-1");
        req.setStatus("ENABLED");
        ParkingLot existing = new ParkingLot();
        existing.setId(9L);
        existing.setCode("PK-1");
        when(parkingLotMapper.findByCode("PK-1")).thenReturn(existing);
        assertThrows(RuntimeException.class, () -> service.create(req));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cd backend && mvn -q -Dtest=ParkingLotServiceTest test
```

Expected: FAIL（类不存在或编译失败）

- [ ] **Step 3: 实现 Entity / Request / Mapper / Service / Controller**

`ParkingLot` 字段对齐表；`ParkingLotRequest`：`name, code, address, contactName, contactPhone, status, remark`。

`ParkingLotMapper`：

```java
@Mapper
public interface ParkingLotMapper {
    @Select("SELECT * FROM parking_lot") List<ParkingLot> findAll();
    @Select("SELECT * FROM parking_lot WHERE id = #{id}") ParkingLot findById(Long id);
    @Select("SELECT * FROM parking_lot WHERE code = #{code}") ParkingLot findByCode(String code);
    @Insert("INSERT INTO parking_lot (name, code, address, contact_name, contact_phone, status, remark) " +
            "VALUES (#{name}, #{code}, #{address}, #{contactName}, #{contactPhone}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ParkingLot lot);
    @Update("UPDATE parking_lot SET name=#{name}, code=#{code}, address=#{address}, " +
            "contact_name=#{contactName}, contact_phone=#{contactPhone}, status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(ParkingLot lot);
    @Delete("DELETE FROM parking_lot WHERE id = #{id}") int deleteById(Long id);
}
```

Service：`list` 按 keyword（name/code contains）与 status 内存过滤；`create`/`update` 校验 code 唯一、默认 status `ENABLED`；`delete` 调 `detainedVehicleMapper.countInYardByParkingLotId`；`requireEnabled` 给扣留入库用。

Controller 对齐 `DistrictController`：`GET /list`、`GET /{id}`、`POST /`、`PUT /{id}`、`DELETE /{id}`，`@PreAuthorize` 用 `parking:query|add|edit|delete`。

本 Task 可先创建空的 `DetainedVehicleMapper` 接口，仅含：

```java
@Select("SELECT COUNT(*) FROM detained_vehicle WHERE parking_lot_id = #{parkingLotId} AND status = 'IN_YARD'")
int countInYardByParkingLotId(Long parkingLotId);
```

其余方法在 Task 3 补全。

- [ ] **Step 4: 跑通单测**

```bash
cd backend && mvn -q -Dtest=ParkingLotServiceTest test
```

Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/ParkingLot.java \
  backend/src/main/java/com/example/backend/dto/ParkingLotRequest.java \
  backend/src/main/java/com/example/backend/mapper/ParkingLotMapper.java \
  backend/src/main/java/com/example/backend/mapper/DetainedVehicleMapper.java \
  backend/src/main/java/com/example/backend/service/ParkingLotService.java \
  backend/src/main/java/com/example/backend/controller/ParkingLotController.java \
  backend/src/test/java/com/example/backend/service/ParkingLotServiceTest.java
git commit -m "feat(backend): add parking lot CRUD"
```

---

### Task 3: DetainedVehicle 后端状态机（TDD）

**Files:**
- Create: `entity/DetainedVehicle.java`
- Create: `dto/DetainInRequest.java`、`dto/DetainUpdateRequest.java`
- Modify: `mapper/DetainedVehicleMapper.java`（补全）
- Create: `service/DetainedVehicleService.java`
- Create: `controller/DetainedVehicleController.java`
- Test: `service/DetainedVehicleServiceTest.java`

**Interfaces:**
- Consumes: `ParkingLotService.requireEnabled`；`DispatchOrderMapper.findById`（已有）
- Produces:
  - `list(plateNo, detainNo, status, parkingLotId, detainDept): List<DetainedVehicle>`（可 enrich 展示字段）
  - `findById(Long): DetainedVehicle`
  - `checkIn(DetainInRequest, Long operatorUserId): boolean`
  - `update(Long id, DetainUpdateRequest): boolean` — 仅 IN_YARD
  - `checkOut(Long id, Long operatorUserId): boolean`
  - `clear(Long id): boolean`
  - Controller `/api/detain`：list/get/post/put/`{id}/out`/`{id}/clear`

`DetainInRequest`：`plateNo, vehicleType, parkingLotId, dispatchOrderId, detainDept, remark`  
`DetainUpdateRequest`：同上（均可选字段按需；`plateNo`/`parkingLotId` 必填策略与入库一致：plateNo、parkingLotId 必填）

`DetainedVehicle` 除表字段外可加非持久化：`parkingLotName`、`orderNo`（enrich 用，MyBatis 不写回）。

- [ ] **Step 1: 写失败单测**

```java
@ExtendWith(MockitoExtension.class)
class DetainedVehicleServiceTest {
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @Mock ParkingLotService parkingLotService;
    @Mock DispatchOrderMapper dispatchOrderMapper;
    @InjectMocks DetainedVehicleService service;

    @Test
    void checkInRejectsWhenPlateAlreadyInYard() {
        DetainInRequest req = new DetainInRequest();
        req.setPlateNo(" 粤B12345 ");
        req.setParkingLotId(1L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(1);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 9L));
        assertTrue(ex.getMessage().contains("在库") || ex.getMessage().contains("车牌"));
        verify(detainedVehicleMapper, never()).insert(any());
    }

    @Test
    void checkOutThenClearHappyPath() {
        DetainedVehicle v = inYard(10L, "粤B1");
        when(detainedVehicleMapper.findById(10L)).thenReturn(v);
        when(detainedVehicleMapper.update(any())).thenReturn(1);
        assertTrue(service.checkOut(10L, 3L));
        assertEquals("OUT", v.getStatus());
        assertNotNull(v.getOutTime());
        assertEquals(3L, v.getOperatorOutId());

        assertTrue(service.clear(10L));
        assertEquals("CLEARED", v.getStatus());
        assertNotNull(v.getClearedAt());
    }

    @Test
    void clearRejectsWhenStillInYard() {
        when(detainedVehicleMapper.findById(1L)).thenReturn(inYard(1L, "粤B1"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.clear(1L));
        assertTrue(ex.getMessage().contains("出库") || ex.getMessage().contains("清理"));
    }

    @Test
    void updateRejectsPlateConflict() {
        DetainedVehicle v = inYard(5L, "粤B旧");
        when(detainedVehicleMapper.findById(5L)).thenReturn(v);
        DetainUpdateRequest req = new DetainUpdateRequest();
        req.setPlateNo("粤B新");
        req.setParkingLotId(1L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.countInYardByPlateNoExcludingId("粤B新", 5L)).thenReturn(1);
        assertThrows(RuntimeException.class, () -> service.update(5L, req));
    }

    private static DetainedVehicle inYard(Long id, String plate) {
        DetainedVehicle v = new DetainedVehicle();
        v.setId(id);
        v.setPlateNo(plate);
        v.setStatus("IN_YARD");
        v.setParkingLotId(1L);
        v.setInTime(java.time.LocalDateTime.now());
        return v;
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cd backend && mvn -q -Dtest=DetainedVehicleServiceTest test
```

Expected: FAIL

- [ ] **Step 3: 实现 Mapper 方法与 Service / Controller**

Mapper 追加：

```java
@Select("SELECT * FROM detained_vehicle") List<DetainedVehicle> findAll();
@Select("SELECT * FROM detained_vehicle WHERE id = #{id}") DetainedVehicle findById(Long id);
@Select("SELECT COUNT(*) FROM detained_vehicle WHERE plate_no = #{plateNo} AND status = 'IN_YARD'")
int countInYardByPlateNo(String plateNo);
@Select("SELECT COUNT(*) FROM detained_vehicle WHERE plate_no = #{plateNo} AND status = 'IN_YARD' AND id <> #{excludeId}")
int countInYardByPlateNoExcludingId(@Param("plateNo") String plateNo, @Param("excludeId") Long excludeId);
@Select("SELECT COUNT(*) FROM detained_vehicle WHERE detain_no LIKE CONCAT(#{prefix}, '%')")
int countByDetainNoPrefix(String prefix);
@Insert("INSERT INTO detained_vehicle (detain_no, plate_no, vehicle_type, parking_lot_id, dispatch_order_id, " +
        "detain_dept, status, in_time, operator_in_id, remark) VALUES (#{detainNo}, #{plateNo}, #{vehicleType}, " +
        "#{parkingLotId}, #{dispatchOrderId}, #{detainDept}, #{status}, #{inTime}, #{operatorInId}, #{remark})")
@Options(useGeneratedKeys = true, keyProperty = "id")
int insert(DetainedVehicle v);
@Update("UPDATE detained_vehicle SET plate_no=#{plateNo}, vehicle_type=#{vehicleType}, parking_lot_id=#{parkingLotId}, " +
        "dispatch_order_id=#{dispatchOrderId}, detain_dept=#{detainDept}, status=#{status}, in_time=#{inTime}, " +
        "out_time=#{outTime}, cleared_at=#{clearedAt}, operator_in_id=#{operatorInId}, operator_out_id=#{operatorOutId}, " +
        "remark=#{remark} WHERE id=#{id}")
int update(DetainedVehicle v);
```

`generateDetainNo()`：

```java
String prefix = "DV" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
int seq = detainedVehicleMapper.countByDetainNoPrefix(prefix) + 1;
return prefix + String.format("%04d", seq);
```

`checkIn`：trim plate → `requireEnabled` → 同牌 count==0 → 若 `dispatchOrderId!=null` 则 `dispatchOrderMapper.findById` 非空 → 生成编号 → insert `IN_YARD`。

`checkOut`：仅 `IN_YARD`；`clear`：仅 `OUT`。

Controller 取当前用户：与 `DispatchController` 相同方式（`SecurityContext` / `Authentication` 取 userId）。`POST /{id}/out`、`POST /{id}/clear` 无 body。

- [ ] **Step 4: 跑通本 Task + Parking 单测**

```bash
cd backend && mvn -q -Dtest=ParkingLotServiceTest,DetainedVehicleServiceTest test
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/DetainedVehicle.java \
  backend/src/main/java/com/example/backend/dto/DetainInRequest.java \
  backend/src/main/java/com/example/backend/dto/DetainUpdateRequest.java \
  backend/src/main/java/com/example/backend/mapper/DetainedVehicleMapper.java \
  backend/src/main/java/com/example/backend/service/DetainedVehicleService.java \
  backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java \
  backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java
git commit -m "feat(backend): add detained vehicle check-in checkout and clear"
```

---

### Task 4: 前端停车场列表

**Files:**
- Create: `frontend/src/api/parking.js`
- Create: `frontend/src/views/parking/ParkingList.vue`
- Modify: `frontend/src/router/index.js`、`frontend/src/App.vue`、`frontend/src/views/Home.vue`

**Interfaces:**
- Consumes: `/api/parking/*`
- Produces: 路由 `/parkings`，侧栏「停车场」，`v-auth="'parking:manage'"`

- [ ] **Step 1: API 封装 `parking.js`**

```js
import request from '../utils/request'
export function listParkings(params) { return request.get('/parking/list', { params }) }
export function getParking(id) { return request.get(`/parking/${id}`) }
export function createParking(data) { return request.post('/parking', data) }
export function updateParking(id, data) { return request.put(`/parking/${id}`, data) }
export function deleteParking(id) { return request.delete(`/parking/${id}`) }
```

- [ ] **Step 2: 实现 `ParkingList.vue`**

对齐 `DistrictList.vue` / `VehicleList.vue`：筛选 keyword/status、表格、新建/编辑弹窗（name、code、address、contactName、contactPhone、status、remark）、删除确认；错误用 `alert` 或现有 toast 模式展示后端 `message`。

- [ ] **Step 3: 路由与导航**

`router/index.js` 增加：

```js
{
  path: '/parkings',
  name: 'ParkingList',
  component: () => import('../views/parking/ParkingList.vue'),
  meta: { permissions: ['parking:manage'] }
}
```

`App.vue` 业务区增加「停车场」链接（`v-auth="'parking:manage'"`）；`Home.vue` 增加对应模块卡片。

- [ ] **Step 4: 手工冒烟**（后端已启、迁移已跑）

登录 `parkingadmin` / `admin123` → 打开停车场 → 新建一条 → 禁用罗湖场仍可见。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/parking.js frontend/src/views/parking/ParkingList.vue \
  frontend/src/router/index.js frontend/src/App.vue frontend/src/views/Home.vue
git commit -m "feat(frontend): add parking lot management page"
```

---

### Task 5: 前端扣留车辆列表 + 吊牌打印

**Files:**
- Create: `frontend/src/api/detain.js`
- Create: `frontend/src/views/detain/DetainedVehicleList.vue`
- Create: `frontend/src/views/detain/HangtagPrint.vue`
- Modify: `frontend/package.json`（`npm install qrcode`）
- Modify: `frontend/src/router/index.js`、`src/App.vue`、`src/views/Home.vue`

**Interfaces:**
- Consumes: `/api/detain/*`、`listParkings`（下拉）
- Produces: `/detained-vehicles`、`/detained-vehicles/:id/hangtag`

- [ ] **Step 1: 安装依赖**

```bash
cd frontend && npm install qrcode
```

- [ ] **Step 2: API `detain.js`**

```js
import request from '../utils/request'
export function listDetains(params) { return request.get('/detain/list', { params }) }
export function getDetain(id) { return request.get(`/detain/${id}`) }
export function checkInDetain(data) { return request.post('/detain', data) }
export function updateDetain(id, data) { return request.put(`/detain/${id}`, data) }
export function checkOutDetain(id) { return request.post(`/detain/${id}/out`) }
export function clearDetain(id) { return request.post(`/detain/${id}/clear`) }
```

- [ ] **Step 3: `DetainedVehicleList.vue`**

筛选：plateNo、detainNo、status、parkingLotId、detainDept。  
操作：`v-auth` — 入库 `detain:add`；编辑 `detain:edit`（仅 IN_YARD）；出库 `detain:out`；清理 `detain:clear`；打印链到 `/detained-vehicles/${id}/hangtag` 且 `detain:print`。  
入库表单：车牌、类型、停车场（仅 ENABLED）、可选工单 id、扣留部门、备注。

- [ ] **Step 4: `HangtagPrint.vue`**

- `onMounted`：`getDetain(route.params.id)`  
- 展示：detainNo、plateNo、parkingLotName、inTime、detainDept、vehicleType  
- 使用 `qrcode.toCanvas` 或 `toDataURL`，内容为 `detainNo`  
- 「打印」按钮：`window.print()`  
- 打印 CSS：`@media print` 隐藏侧栏/按钮（若仍包在 `App.vue` 壳内，用 class + 全局 print 样式，或路由 meta `hideShell: true` 并在 `App.vue` 判断——**优先**在 `App.vue` 对 hangtag 路径与 login 类似使用精简壳）

`App.vue`：当 `route.path` 匹配 `/detained-vehicles/*/hangtag` 时仅渲染 `<router-view />`（无侧栏）。

- [ ] **Step 5: 路由与导航**

```js
{
  path: '/detained-vehicles',
  name: 'DetainedVehicleList',
  component: () => import('../views/detain/DetainedVehicleList.vue'),
  meta: { permissions: ['detain:manage'] }
},
{
  path: '/detained-vehicles/:id/hangtag',
  name: 'HangtagPrint',
  component: () => import('../views/detain/HangtagPrint.vue'),
  meta: { permissions: ['detain:print'] }
}
```

侧栏 + Home 增加「扣留车辆」。

- [ ] **Step 6: 手工验收主路径**

| 步骤 | 期望 |
|------|------|
| 入库新车牌 | `IN_YARD`，生成 `DV...` |
| 同牌再入库 | 失败提示 |
| 出库 → 清理 | 状态正确；清理后不可编辑 |
| 打开吊牌 | 有 QR，打印预览可见关键字段 |
| 有在库车删停车场 | 失败 |
| dispatcher 账号 | 无停车场/扣留菜单 |

- [ ] **Step 7: Commit**

```bash
git add frontend/package.json frontend/package-lock.json \
  frontend/src/api/detain.js \
  frontend/src/views/detain/DetainedVehicleList.vue \
  frontend/src/views/detain/HangtagPrint.vue \
  frontend/src/router/index.js frontend/src/App.vue frontend/src/views/Home.vue
git commit -m "feat(frontend): add detained vehicle workflow and hangtag print"
```

---

### Task 6: 回归测试与收尾

**Files:** 无强制新文件

- [ ] **Step 1: 后端全量单测**

```bash
cd backend && mvn -q test
```

Expected: BUILD SUCCESS（含 ParkingLot / DetainedVehicle / 既有用例）

- [ ] **Step 2: 对照 spec 验收表 1–7 勾选**

- [ ] **Step 3: 仅当用户要求时** 可选 commit `docs: note parking-detain implementation complete`

---

## Spec coverage (self-review)

| Spec 项 | Task |
|---------|------|
| parking_lot / detained_vehicle DDL + init/migrate | 1 |
| 权限 42–52；ADMIN + PARKING_ADMIN | 1 |
| 种子场与在库/出库样例；parkingadmin | 1 |
| 停车场 CRUD + 禁用/删场规则 | 2 |
| 入库/编辑/出库/清理状态机；同牌；detain_no | 3 |
| 可选工单校验 | 3 |
| API `/api/parking` `/api/detain` | 2–3 |
| 前端停车场页 | 4 |
| 前端扣留列表 + 吊牌 QR 打印 | 5 |
| 验收与单测 | 2–3、6 |
| 明确不做（统计/移动/PDF） | 未排任务（YAGNI） |
