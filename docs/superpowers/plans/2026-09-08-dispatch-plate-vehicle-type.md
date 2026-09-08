# 工单车牌与事故车型字典 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建/编辑工单支持选填事故车牌与车型；系统管理维护事故车型字典（启停）；移动端现场采集预填工单值并可改，保存仅写现场记录。

**Architecture:** 新增 `accident_vehicle_type` 字典（Entity→Mapper→Service→Controller）；`dispatch_order` 增加 `plate_no` / `vehicle_type_id` / `vehicle_type_name` 快照；创建与更新时校验启用车型并写快照；管理端车型页 + 工单表单/列表/详情；移动端 scene 预填 + 启用字典下拉。

**Tech Stack:** Spring Boot 3.2、MyBatis 注解、Spring Security JWT、MySQL、JUnit5+Mockito、Vue 3、Vue Router、Axios、uni-app

**Spec:** `docs/superpowers/specs/2026-09-08-dispatch-plate-vehicle-type-design.md`

## Global Constraints

- 车牌、车型均为选填；空字符串规范为 `null`
- 车型状态仅：`ENABLED` / `DISABLED`；**无硬删除**
- 绑工单时 `vehicleTypeId` 必须存在且 `ENABLED`；同时写入 `vehicleTypeName` 快照
- 清空车型时 `vehicleTypeId` 与 `vehicleTypeName` 均置空
- 停用后下拉不可选；历史工单展示快照名
- 现场保存**不回写** `dispatch_order` 车牌/车型
- 统一 `Result`；业务冲突 `RuntimeException`，Controller `catch` → `Result.error(message)`
- 权限 id：`63–66`；授予 `ADMIN`（role_id=5）；`/enabled` 仅需已登录
- 种子 17 项含「厢式货车」（非厢式火车）
- 不引入新 UI 库；沿用 `enterprise.css`；YAGNI：不改造扣留/施救车类型字典

## File Structure

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-08_dispatch_plate_vehicle_type.sql`

### Backend（新建）
- `entity/AccidentVehicleType.java`
- `mapper/AccidentVehicleTypeMapper.java`
- `service/AccidentVehicleTypeService.java`
- `controller/VehicleTypeController.java`
- `dto/VehicleTypeRequest.java`
- `test/.../service/AccidentVehicleTypeServiceTest.java`

### Backend（修改）
- `entity/DispatchOrder.java` — 增加 `plateNo`、`vehicleTypeId`、`vehicleTypeName`
- `mapper/DispatchOrderMapper.java` — insert/update SQL 含新列
- `service/DispatchOrderService.java` — create/update 调用快照逻辑
- `test/.../service/DispatchOrderServiceTest.java` — 快照与校验用例

### Frontend（新建）
- `src/api/vehicleType.js`
- `src/views/vehicleType/VehicleTypeList.vue`

### Frontend（修改）
- `src/router/index.js`、`src/App.vue`、`src/views/Home.vue`
- `src/views/dispatch/DispatchCreate.vue`
- `src/views/dispatch/DispatchList.vue`
- `src/views/dispatch/DispatchDetail.vue`

### Mobile（修改）
- `mobile-rescuer/src/api/rescuer.js` 或新建 `vehicleType.js` — 调 `/vehicle-type/enabled`
- `mobile-rescuer/src/pages/task/scene.vue` — 预填 + 下拉

---

### Task 1: 数据库、权限与种子

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-08_dispatch_plate_vehicle_type.sql`

**Interfaces:**
- Produces: 表 `accident_vehicle_type`；`dispatch_order` 三列；权限 id `63–66`；17 条种子车型

- [ ] **Step 1: 在 `init.sql` DROP 段增加**

```sql
DROP TABLE IF EXISTS `accident_vehicle_type`;
```

（放在 `dispatch_order` DROP 附近；无物理 FK。）

- [ ] **Step 2: 扩展 `dispatch_order` DDL**

在 `rescue_reason` 后增加：

```sql
  `plate_no` VARCHAR(20) DEFAULT NULL COMMENT '事故车辆车牌',
  `vehicle_type_id` BIGINT DEFAULT NULL COMMENT '事故车型字典 id',
  `vehicle_type_name` VARCHAR(50) DEFAULT NULL COMMENT '车型名称快照',
```

- [ ] **Step 3: 追加 `accident_vehicle_type` DDL + 种子**

```sql
CREATE TABLE `accident_vehicle_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '车型名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事故车型字典';

INSERT INTO `accident_vehicle_type` (`name`, `sort_order`, `status`) VALUES
('轿车', 1, 'ENABLED'),
('大客车', 2, 'ENABLED'),
('半挂货车', 3, 'ENABLED'),
('黄牌大货车', 4, 'ENABLED'),
('蓝牌大货车', 5, 'ENABLED'),
('厢式货车', 6, 'ENABLED'),
('面包车', 7, 'ENABLED'),
('房车', 8, 'ENABLED'),
('越野车', 9, 'ENABLED'),
('三轮机动车', 10, 'ENABLED'),
('三轮电动车', 11, 'ENABLED'),
('人力三轮车', 12, 'ENABLED'),
('二轮摩托车', 13, 'ENABLED'),
('二轮电动车', 14, 'ENABLED'),
('自行车', 15, 'ENABLED'),
('残疾车', 16, 'ENABLED'),
('其他', 17, 'ENABLED');
```

- [ ] **Step 4: 权限 63–66，并扩展 ADMIN 授权**

在 permission INSERT 末尾 `62` 后追加：

```sql
(63, '车型管理', 'vehicle-type:manage', 'MODULE', 0, 13),
(64, '车型查询', 'vehicle-type:query', 'BUTTON', 63, 1),
(65, '车型新增', 'vehicle-type:add', 'BUTTON', 63, 2),
(66, '车型编辑', 'vehicle-type:edit', 'BUTTON', 63, 3);
```

将 ADMIN 授权改为包含至 66：

```sql
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id = 16 OR id = 19 OR id BETWEEN 20 AND 66;
```

- [ ] **Step 5: 写迁移脚本**

`database/migrate_2026-09-08_dispatch_plate_vehicle_type.sql`：

```sql
-- 事故车型字典 + 工单车牌/车型快照
CREATE TABLE IF NOT EXISTS `accident_vehicle_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '车型名称',
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事故车型字典';

-- 幂等：仅当列不存在时由执行者按环境手工确认；推荐开发库直接 ALTER：
ALTER TABLE `dispatch_order`
  ADD COLUMN `plate_no` VARCHAR(20) DEFAULT NULL COMMENT '事故车辆车牌' AFTER `rescue_reason`,
  ADD COLUMN `vehicle_type_id` BIGINT DEFAULT NULL COMMENT '事故车型字典 id' AFTER `plate_no`,
  ADD COLUMN `vehicle_type_name` VARCHAR(50) DEFAULT NULL COMMENT '车型名称快照' AFTER `vehicle_type_id`;

INSERT INTO `accident_vehicle_type` (`name`, `sort_order`, `status`)
SELECT v.name, v.sort_order, 'ENABLED' FROM (
  SELECT '轿车' AS name, 1 AS sort_order UNION ALL
  SELECT '大客车', 2 UNION ALL SELECT '半挂货车', 3 UNION ALL
  SELECT '黄牌大货车', 4 UNION ALL SELECT '蓝牌大货车', 5 UNION ALL
  SELECT '厢式货车', 6 UNION ALL SELECT '面包车', 7 UNION ALL
  SELECT '房车', 8 UNION ALL SELECT '越野车', 9 UNION ALL
  SELECT '三轮机动车', 10 UNION ALL SELECT '三轮电动车', 11 UNION ALL
  SELECT '人力三轮车', 12 UNION ALL SELECT '二轮摩托车', 13 UNION ALL
  SELECT '二轮电动车', 14 UNION ALL SELECT '自行车', 15 UNION ALL
  SELECT '残疾车', 16 UNION ALL SELECT '其他', 17
) v
WHERE NOT EXISTS (SELECT 1 FROM `accident_vehicle_type` t WHERE t.name = v.name);

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 63 AS id, '车型管理' AS permission_name, 'vehicle-type:manage' AS permission_code, 'MODULE' AS permission_type, 0 AS parent_id, 13 AS sort_order
  UNION ALL SELECT 64, '车型查询', 'vehicle-type:query', 'BUTTON', 63, 1
  UNION ALL SELECT 65, '车型新增', 'vehicle-type:add', 'BUTTON', 63, 2
  UNION ALL SELECT 66, '车型编辑', 'vehicle-type:edit', 'BUTTON', 63, 3
) x WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.id = x.id);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 63 AND 66
AND NOT EXISTS (
  SELECT 1 FROM `role_permission` rp WHERE rp.role_id = 5 AND rp.permission_id = permission.id
);
```

（若环境已有列，跳过对应 `ALTER`；重复执行种子/权限用 `WHERE NOT EXISTS` 防重。）

- [ ] **Step 6: 在开发库执行迁移（或重建 init）并校验**

Run:

```bash
mysql -u root -p vue_springboot_system < database/migrate_2026-09-08_dispatch_plate_vehicle_type.sql
```

Expected: `SELECT COUNT(*) FROM accident_vehicle_type;` → `17`；`SHOW COLUMNS FROM dispatch_order LIKE 'plate_no';` 有行；`SELECT permission_code FROM permission WHERE id BETWEEN 63 AND 66;` 四行。

- [ ] **Step 7: Commit**

```bash
git add database/init.sql database/migrate_2026-09-08_dispatch_plate_vehicle_type.sql
git commit -m "db: add accident vehicle type dict and dispatch plate fields"
```

---

### Task 2: 车型字典后端 CRUD

**Files:**
- Create: `backend/src/main/java/com/example/backend/entity/AccidentVehicleType.java`
- Create: `backend/src/main/java/com/example/backend/mapper/AccidentVehicleTypeMapper.java`
- Create: `backend/src/main/java/com/example/backend/dto/VehicleTypeRequest.java`
- Create: `backend/src/main/java/com/example/backend/service/AccidentVehicleTypeService.java`
- Create: `backend/src/main/java/com/example/backend/controller/VehicleTypeController.java`
- Test: `backend/src/test/java/com/example/backend/service/AccidentVehicleTypeServiceTest.java`

**Interfaces:**
- Produces:
  - `AccidentVehicleTypeService.list(String status)` → `List<AccidentVehicleType>`
  - `AccidentVehicleTypeService.listEnabled()` → 仅 ENABLED，按 `sortOrder` 升序
  - `AccidentVehicleTypeService.requireEnabled(Long id)` → 实体或抛 `RuntimeException("车型不存在或已停用")`
  - `AccidentVehicleTypeService.create(VehicleTypeRequest)` / `update(Long, VehicleTypeRequest)`
  - REST `/api/vehicle-type/list|enabled|{id}` POST `/` PUT `/{id}`

- [ ] **Step 1: 写失败测试 `AccidentVehicleTypeServiceTest`**

```java
package com.example.backend.service;

import com.example.backend.dto.VehicleTypeRequest;
import com.example.backend.entity.AccidentVehicleType;
import com.example.backend.mapper.AccidentVehicleTypeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccidentVehicleTypeServiceTest {

    @Mock AccidentVehicleTypeMapper mapper;
    @InjectMocks AccidentVehicleTypeService service;

    @Test
    void createRejectsDuplicateName() {
        VehicleTypeRequest req = new VehicleTypeRequest();
        req.setName("轿车");
        when(mapper.findByName("轿车")).thenReturn(new AccidentVehicleType());
        assertThrows(RuntimeException.class, () -> service.create(req));
        verify(mapper, never()).insert(any());
    }

    @Test
    void listEnabledFiltersAndSorts() {
        AccidentVehicleType a = new AccidentVehicleType();
        a.setName("越野车"); a.setStatus("ENABLED"); a.setSortOrder(9);
        AccidentVehicleType b = new AccidentVehicleType();
        b.setName("轿车"); b.setStatus("ENABLED"); b.setSortOrder(1);
        AccidentVehicleType c = new AccidentVehicleType();
        c.setName("旧型"); c.setStatus("DISABLED"); c.setSortOrder(0);
        when(mapper.findAll()).thenReturn(List.of(a, b, c));
        List<AccidentVehicleType> list = service.listEnabled();
        assertEquals(2, list.size());
        assertEquals("轿车", list.get(0).getName());
        assertEquals("越野车", list.get(1).getName());
    }

    @Test
    void requireEnabledRejectsDisabled() {
        AccidentVehicleType t = new AccidentVehicleType();
        t.setId(1L); t.setStatus("DISABLED");
        when(mapper.findById(1L)).thenReturn(t);
        assertThrows(RuntimeException.class, () -> service.requireEnabled(1L));
    }
}
```

- [ ] **Step 2: Run test — expect compile/fail**

Run: `cd backend && mvn -q -Dtest=AccidentVehicleTypeServiceTest test`  
（需 `JAVA_HOME` 指向 JDK 17）  
Expected: 编译失败（类不存在）或测试失败。

- [ ] **Step 3: Entity + Request + Mapper**

`AccidentVehicleType.java`：字段 `id, name, sortOrder, status, remark, createTime, updateTime`（Lombok `@Data`）。

`VehicleTypeRequest.java`：`name, sortOrder, status, remark`。

`AccidentVehicleTypeMapper.java`：

```java
@Mapper
public interface AccidentVehicleTypeMapper {
    @Select("SELECT * FROM accident_vehicle_type")
    List<AccidentVehicleType> findAll();

    @Select("SELECT * FROM accident_vehicle_type WHERE id = #{id}")
    AccidentVehicleType findById(Long id);

    @Select("SELECT * FROM accident_vehicle_type WHERE name = #{name}")
    AccidentVehicleType findByName(String name);

    @Insert("INSERT INTO accident_vehicle_type (name, sort_order, status, remark) " +
            "VALUES (#{name}, #{sortOrder}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AccidentVehicleType row);

    @Update("UPDATE accident_vehicle_type SET name=#{name}, sort_order=#{sortOrder}, " +
            "status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(AccidentVehicleType row);
}
```

- [ ] **Step 4: Service + Controller**

`AccidentVehicleTypeService` 要点：
- `create`：`name` trim 非空；默认 `status=ENABLED`、`sortOrder=0`；`findByName` 冲突抛「车型名称已存在」
- `update`：不存在抛「车型不存在」；改名唯一性排除自身；`status` 仅允许 `ENABLED`/`DISABLED`
- `listEnabled`：filter ENABLED，按 `sortOrder` 再 `id`
- `requireEnabled`：null 或非 ENABLED → 「车型不存在或已停用」

`VehicleTypeController`：
- `GET /list` + `@PreAuthorize("hasAuthority('vehicle-type:query')")`，可选 `status`
- `GET /enabled` — **无** `@PreAuthorize` 方法级额外权限（类级不设），依赖已登录即可
- `GET /{id}` query；`POST` add；`PUT /{id}` edit  
注意：`/enabled` 路由必须写在 `/{id}` **之前**，避免被 path variable 吃掉。

- [ ] **Step 5: Run tests — expect PASS**

```bash
cd backend
# PowerShell:
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
.\..\ .tools\apache-maven\bin\mvn.cmd -q -Dtest=AccidentVehicleTypeServiceTest test
```

Expected: `BUILD SUCCESS`，测试通过。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/AccidentVehicleType.java \
  backend/src/main/java/com/example/backend/mapper/AccidentVehicleTypeMapper.java \
  backend/src/main/java/com/example/backend/dto/VehicleTypeRequest.java \
  backend/src/main/java/com/example/backend/service/AccidentVehicleTypeService.java \
  backend/src/main/java/com/example/backend/controller/VehicleTypeController.java \
  backend/src/test/java/com/example/backend/service/AccidentVehicleTypeServiceTest.java
git commit -m "feat: add accident vehicle type dictionary API"
```

---

### Task 3: 工单车牌/车型后端

**Files:**
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`

**Interfaces:**
- Consumes: `AccidentVehicleTypeService.requireEnabled(Long)`
- Produces: create/update 写入 `plateNo`、`vehicleTypeId`、`vehicleTypeName`；非法 id 抛异常

- [ ] **Step 1: 扩展 `DispatchOrderServiceTest`（先写失败用例）**

在现有测试类增加 `@Mock AccidentVehicleTypeService accidentVehicleTypeService;`（若 `@InjectMocks` 需字段注入匹配）。

```java
@Test
void createWritesVehicleTypeSnapshot() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    order.setPlateNo(" 粤B12345 ");
    order.setVehicleTypeId(6L);
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    AccidentVehicleType t = new AccidentVehicleType();
    t.setId(6L); t.setName("厢式货车"); t.setStatus("ENABLED");
    when(accidentVehicleTypeService.requireEnabled(6L)).thenReturn(t);
    when(dispatchOrderMapper.insert(any())).thenReturn(1);

    assertTrue(service.create(order, 7L));
    assertEquals("粤B12345", order.getPlateNo());
    assertEquals(6L, order.getVehicleTypeId());
    assertEquals("厢式货车", order.getVehicleTypeName());
}

@Test
void createRejectsDisabledVehicleType() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    order.setVehicleTypeId(99L);
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(accidentVehicleTypeService.requireEnabled(99L))
            .thenThrow(new RuntimeException("车型不存在或已停用"));
    assertThrows(RuntimeException.class, () -> service.create(order, 7L));
}

@Test
void createClearsTypeWhenIdNull() {
    DispatchOrder order = new DispatchOrder();
    order.setAccidentAddress("A");
    order.setRescueReason("B");
    order.setVehicleTypeId(null);
    order.setVehicleTypeName("应被清空");
    Role d = new Role(); d.setRoleCode("DISPATCHER");
    when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
    when(dispatchOrderMapper.insert(any())).thenReturn(1);
    assertTrue(service.create(order, 7L));
    assertNull(order.getVehicleTypeId());
    assertNull(order.getVehicleTypeName());
}
```

- [ ] **Step 2: Run — expect FAIL**（快照未写入）

- [ ] **Step 3: Entity 增加字段**

```java
private String plateNo;
private Long vehicleTypeId;
private String vehicleTypeName;
```

- [ ] **Step 4: Mapper insert/update 含新列**

Insert 列清单在 `rescue_reason` 后加入 `plate_no, vehicle_type_id, vehicle_type_name`，VALUES 对应 `#{plateNo}, #{vehicleTypeId}, #{vehicleTypeName}`。  
Update SET 同步三列。

- [ ] **Step 5: Service 应用逻辑**

注入 `AccidentVehicleTypeService`。新增私有方法：

```java
void applyPlateAndVehicleType(DispatchOrder order) {
    String plate = order.getPlateNo();
    if (plate != null) {
        plate = plate.trim();
        order.setPlateNo(plate.isEmpty() ? null : plate);
    }
    Long typeId = order.getVehicleTypeId();
    if (typeId == null) {
        order.setVehicleTypeId(null);
        order.setVehicleTypeName(null);
        return;
    }
    AccidentVehicleType type = accidentVehicleTypeService.requireEnabled(typeId);
    order.setVehicleTypeId(type.getId());
    order.setVehicleTypeName(type.getName());
}
```

在 `create` 的 `applyDispatcherAndPrefill` 之后、`insert` 之前调用 `applyPlateAndVehicleType(order)`。  
在 `update` 中，在写回 `rescueReason` 等字段后：

```java
existing.setPlateNo(order.getPlateNo());
existing.setVehicleTypeId(order.getVehicleTypeId());
applyPlateAndVehicleType(existing);
```

（先拷贝客户端 plate/typeId，再统一规范化与快照。）

- [ ] **Step 6: Run `DispatchOrderServiceTest` — PASS**

```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd -q -Dtest=DispatchOrderServiceTest,AccidentVehicleTypeServiceTest test
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/DispatchOrder.java \
  backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java \
  backend/src/main/java/com/example/backend/service/DispatchOrderService.java \
  backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java
git commit -m "feat: persist optional plate and vehicle type on dispatch orders"
```

---

### Task 4: 管理端车型维护页

**Files:**
- Create: `frontend/src/api/vehicleType.js`
- Create: `frontend/src/views/vehicleType/VehicleTypeList.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/views/Home.vue`

**Interfaces:**
- Consumes: `/api/vehicle-type/list|/{id}` POST PUT
- Produces: 路由 `/vehicle-types`；侧栏与工作台入口

- [ ] **Step 1: API 模块**

```js
import request from '../utils/request'

export function listVehicleTypes(params) {
  return request.get('/vehicle-type/list', { params })
}

export function listEnabledVehicleTypes() {
  return request.get('/vehicle-type/enabled')
}

export function createVehicleType(data) {
  return request.post('/vehicle-type', data)
}

export function updateVehicleType(id, data) {
  return request.put(`/vehicle-type/${id}`, data)
}
```

- [ ] **Step 2: `VehicleTypeList.vue`**

以 `ParkingList.vue` 为模板精简：
- 筛选：关键词（名称）、状态
- 表列：名称、排序、状态、操作（编辑；启停切换按钮调用 update 改 status）
- 弹窗：名称（必填）、排序、备注、状态；**无删除**
- 权限：`v-auth="'vehicle-type:add'"` / `edit`

启停示例：

```js
async function toggleStatus(row) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await updateVehicleType(row.id, { ...row, status: next })
  await loadList()
}
```

- [ ] **Step 3: 路由**

```js
{
  path: '/vehicle-types',
  name: 'VehicleTypeList',
  component: () => import('../views/vehicleType/VehicleTypeList.vue'),
  meta: { permissions: ['vehicle-type:manage'] }
}
```

- [ ] **Step 4: `App.vue` 系统管理区增加导航**

放在权限管理附近：

```html
<router-link
  v-auth="'vehicle-type:manage'"
  to="/vehicle-types"
  class="nav-item"
  active-class="active"
>
  <span class="nav-ico">型</span>
  车型管理
</router-link>
```

- [ ] **Step 5: `Home.vue` module-grid 增加卡片**

```html
<router-link
  v-auth="'vehicle-type:manage'"
  to="/vehicle-types"
  class="module-item"
>
  <span class="module-tag">系统</span>
  <h3>车型管理</h3>
  <p>维护事故车辆车型字典，支持启用与停用。</p>
</router-link>
```

- [ ] **Step 6: 手工验收**

登录 `admin` / `admin123` → 工作台可见车型管理 → 列表 17 条 → 停用「房车」→ 状态 DISABLED。  
（改角色后需重新登录刷新 JWT。）

- [ ] **Step 7: Commit**

```bash
git add frontend/src/api/vehicleType.js frontend/src/views/vehicleType/VehicleTypeList.vue \
  frontend/src/router/index.js frontend/src/App.vue frontend/src/views/Home.vue
git commit -m "feat(frontend): add vehicle type admin page under system management"
```

---

### Task 5: 管理端工单新建 / 列表 / 详情

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`
- Modify: `frontend/src/views/dispatch/DispatchList.vue`
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

**Interfaces:**
- Consumes: `listEnabledVehicleTypes()`；`createDispatch` / `updateDispatch` 带 `plateNo`、`vehicleTypeId`

- [ ] **Step 1: `DispatchCreate.vue` 增加字段**

在「施救原因」后增加：

```html
<label>
  车牌号码
  <input v-model.trim="form.plateNo" placeholder="选填" maxlength="20" />
</label>
<label>
  车型
  <select v-model="form.vehicleTypeId">
    <option value="">不选择</option>
    <option v-for="t in vehicleTypes" :key="t.id" :value="String(t.id)">
      {{ t.name }}
    </option>
  </select>
</label>
```

```js
import { listEnabledVehicleTypes } from '../../api/vehicleType'

const vehicleTypes = ref([])
const form = reactive({
  rescueReason: '',
  accidentAddress: '',
  longitude: '',
  latitude: '',
  rescuerId: '',
  vehicleId: '',
  plateNo: '',
  vehicleTypeId: ''
})

// onMounted 中：
listEnabledVehicleTypes()
  .then((res) => { vehicleTypes.value = res.data || [] })
  .catch(() => { vehicleTypes.value = [] })

// onSubmit payload：
plateNo: form.plateNo || null,
vehicleTypeId: form.vehicleTypeId ? Number(form.vehicleTypeId) : null
```

- [ ] **Step 2: `DispatchList.vue` 增加列**

表头在「地点」后加「车牌」「车型」；单元格：

```html
<td>{{ row.plateNo || '—' }}</td>
<td>{{ row.vehicleTypeName || '—' }}</td>
```

空表 `colspan` 改为 `8`。

- [ ] **Step 3: `DispatchDetail.vue` 展示 + PENDING 可改**

信息区「施救原因」附近：

```html
<div>
  <span class="label">车牌号码</span>
  <strong>{{ order.plateNo || '—' }}</strong>
</div>
<div>
  <span class="label">车型</span>
  <strong>{{ order.vehicleTypeName || '—' }}</strong>
</div>
```

在 `PENDING` 区块增加简易编辑（车牌 + 车型下拉 + 保存），调用已有 `updateDispatch`：

```js
await updateDispatch(order.value.id, {
  accidentAddress: order.value.accidentAddress,
  longitude: order.value.longitude,
  latitude: order.value.latitude,
  rescueReason: order.value.rescueReason,
  dispatcherId: order.value.dispatcherId,
  vehicleId: order.value.vehicleId,
  rescuerId: order.value.rescuerId,
  plateNo: editForm.plateNo || null,
  vehicleTypeId: editForm.vehicleTypeId ? Number(editForm.vehicleTypeId) : null
})
```

加载启用车型列表同创建页；保存成功后 `getDispatch` 刷新。

- [ ] **Step 4: 手工验收**

新建工单选「厢式货车」+ 车牌 → 列表可见 → 详情可见 → 停用该车型后旧单仍显示「厢式货车」→ 新建下拉无该项 → 不填车牌车型仍可提交。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/dispatch/DispatchCreate.vue \
  frontend/src/views/dispatch/DispatchList.vue \
  frontend/src/views/dispatch/DispatchDetail.vue
git commit -m "feat(frontend): add plate and vehicle type on dispatch create and views"
```

---

### Task 6: 移动端现场采集预填与下拉

**Files:**
- Create: `mobile-rescuer/src/api/vehicleType.js`（或扩展 `rescuer.js`）
- Modify: `mobile-rescuer/src/pages/task/scene.vue`

**Interfaces:**
- Consumes: `GET /api/vehicle-type/enabled`；`getTask` 返回的 `order.plateNo` / `order.vehicleTypeName` 与 `fieldRecord`
- Produces: 表单预填规则；保存仍 `saveScene` → `dispatch_field_record`

- [ ] **Step 1: API**

```js
import { request } from '../utils/request'

export function listEnabledVehicleTypes() {
  return request({ url: '/vehicle-type/enabled' })
}
```

确认 `request` 的 base 已含 `/api` 前缀（与现有 `rescuer.js` 一致）。

- [ ] **Step 2: 改 `scene.vue` 加载逻辑**

```js
import { listEnabledVehicleTypes } from '../../api/vehicleType'

const vehicleTypes = ref([])

async function load() {
  try {
    const [detail, mediaRes, typeRes] = await Promise.all([
      getTask(id.value),
      listMedia(id.value),
      listEnabledVehicleTypes().catch(() => ({ data: [] }))
    ])
    vehicleTypes.value = typeRes.data || []
    const fr = detail.data?.fieldRecord
    const order = detail.data?.order
    if (fr && (fr.plateNo || fr.vehicleType || fr.damageDesc || fr.sceneRemark)) {
      form.plateNo = fr.plateNo || ''
      form.vehicleType = fr.vehicleType || ''
      form.damageDesc = fr.damageDesc || ''
      form.sceneRemark = fr.sceneRemark || ''
    } else {
      form.plateNo = order?.plateNo || ''
      form.vehicleType = order?.vehicleTypeName || ''
      form.damageDesc = fr?.damageDesc || ''
      form.sceneRemark = fr?.sceneRemark || ''
    }
    medias.value = (mediaRes.data || []).filter((m) => m.bizType === 'DAMAGE')
  } catch (_) {}
}
```

预填判定：若存在现场记录且关键业务字段任一有值则用现场记录；否则用工单车牌/车型名预填。（与 spec「优先现场记录」一致。）

- [ ] **Step 3: 车型改为 picker/select**

将车型 `input` 改为：

```html
<picker mode="selector" :range="typeNames" @change="onTypePick">
  <view class="field-input">{{ form.vehicleType || '请选择车型' }}</view>
</picker>
```

```js
const typeNames = computed(() => vehicleTypes.value.map((t) => t.name))
function onTypePick(e) {
  const i = Number(e.detail.value)
  form.vehicleType = typeNames.value[i] || ''
}
```

`onSave` 仍提交 `{ plateNo, vehicleType, damageDesc, sceneRemark }` 字符串，**不**调用工单 update。

- [ ] **Step 4: 手工验收**

用 `towdriver` 打开已填车牌/车型的任务现场页 → 看到预填 → 改车型保存 → 再进页仍为现场值；管理端工单快照未变。

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/api/vehicleType.js mobile-rescuer/src/pages/task/scene.vue
git commit -m "feat(mobile): prefill scene plate/type from order with dict picker"
```

---

## Spec Coverage Checklist

| Spec 要求 | Task |
|-----------|------|
| 表 `accident_vehicle_type` + 17 种子（厢式货车） | 1 |
| `dispatch_order` 三列 | 1、3 |
| 权限 63–66、ADMIN、无 DELETE | 1、2、4 |
| `/api/vehicle-type` CRUD + `/enabled` | 2 |
| 工单 create/update 快照与校验 | 3 |
| 系统管理车型页 | 4 |
| 新建工单选填、列表/详情、PENDING 可改 | 5 |
| 移动端预填、下拉、不回写工单 | 6 |
| 选填可空建单、停用历史快照 | 1、3、5 验收 |

## Plan Self-Review

- 无 TBD/「类似 Task N」占位
- 类型名统一：`AccidentVehicleType`、`vehicleTypeId`、`vehicleTypeName`、`plateNo`
- `/enabled` 置于 `/{id}` 之前已写明
- 迁移脚本对已有库与 init 双路径覆盖
