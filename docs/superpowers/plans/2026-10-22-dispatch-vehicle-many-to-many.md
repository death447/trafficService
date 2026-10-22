# 工单 ↔ 施救车多对多 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 `dispatch_vehicle_assignment` 实现工单与施救车多对多；每车独立进度；整单在全部赋值终态且至少一车完成后 `COMPLETED`；nearby 推荐纳入已有活跃单的车并垫底排序。

**Architecture:** 工单保留事故信息与汇总 `status`；车×单过程下沉到赋值表；`DispatchOrderService` 派单/接退签/完成改为写赋值并 `refreshOrderStatus`；车辆 `BUSY`/`IDLE` 按全库活跃赋值计数；`findNearby` 候选扩至非 OFFLINE 已绑司机，按片区 → `hasActiveAssignment` → 距离排序。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、Vue 3、uni-app、MySQL

**Spec:** `docs/superpowers/specs/2026-10-22-dispatch-vehicle-many-to-many-design.md`

## Global Constraints

- 真多对多：关联表 `dispatch_vehicle_assignment`；同单同车仅一行（`uk_order_vehicle`）
- 赋值状态：`DISPATCHED` → `ACCEPTED` → `COMPLETED`；可 `REJECTED` / `ABORTED`
- 活跃赋值：`status IN ('DISPATCHED','ACCEPTED')`；占车与 `BUSY` 以此为准
- 工单汇总：无赋值→`PENDING`；有 `DISPATCHED`→`DISPATCHED`；仅有 `ACCEPTED`（无 DISPATCHED）→`ACCEPTED`；全终态且≥1 `COMPLETED`→`COMPLETED`；全 `REJECTED`/`ABORTED` 无完成→`PENDING`；调度整单中止无完成→`ABORTED`
- 派单/加派：不要求 `IDLE`；须非 `OFFLINE`、已绑 `TOW_DRIVER`；`PENDING` 与进行中均可加派
- 预填车辆不建赋值、不占活跃
- nearby：候选非 `OFFLINE` 且已绑司机；排序 `inMatchedDistrict` desc → `hasActiveAssignment` asc → `distanceMeters` asc；新鲜/陈旧分桶可保留
- 移动端按「当前用户在该工单的赋值行」操作；无匹配 → 错误「无权操作该工单」
- PC 完成工单：仍有非终态赋值则拒绝
- 不做 `capacity`、不做小程序、交警仍一单一评
- 统一 `Result`；业务冲突 `RuntimeException` → Controller `Result.error`
- TDD：先改/写失败测试再实现；每任务结束提交

## File Structure

### Database
- Create: `database/migrate_2026-10-22_dispatch_vehicle_assignment.sql`
- Modify: `database/init.sql`（新表 + field/media 列 + 与 migrate 一致的约束）

### Backend（新建）
- `entity/DispatchVehicleAssignment.java`
- `mapper/DispatchVehicleAssignmentMapper.java`
- `dto/DispatchVehicleAssignmentVO.java`（详情展示：含车牌、司机名）
- `dto/AssignDispatchRequest.java` 扩展（保留 `vehicleId`，增加 `List<Long> vehicleIds`）

### Backend（修改）
- `entity/DispatchOrder.java`（非表字段 `List<DispatchVehicleAssignmentVO> assignments`；过程字段停止作为写路径）
- `entity/DispatchFieldRecord.java`、`entity/DispatchMedia.java`（`assignmentId`）
- `mapper/DispatchOrderMapper.java`（`countDispatchedByVehicleId` 改为查赋值表或委托；列表 enrich）
- `mapper/DispatchFieldRecordMapper.java`、`mapper/DispatchMediaMapper.java`
- `service/DispatchOrderService.java`（核心重构）
- `service/RescueVehicleService.java`（`requireAssignable`、`findNearby`、删车计数）
- `service/RescuerMobileService.java`（任务列表/详情按赋值）
- `controller/DispatchController.java`、`VehicleController.java`（若签名变）
- `dto/NearbyVehicleVO.java`（`hasActiveAssignment`）
- Tests: `DispatchOrderServiceTest.java`、`RescueVehicleServiceTest.java`；必要时 `RescuerMobileServiceTest.java`

### Frontend PC
- `frontend/src/views/dispatch/DispatchDetail.vue`
- `frontend/src/views/dispatch/DispatchCreate.vue`（多车意向可选；仍可不建赋值）
- `frontend/src/api/dispatch.js`（assign 传 `vehicleIds`）

### Mobile
- `mobile-rescuer/src/pages/task/detail.vue`（完成提示整单未结束）
- 列表/API 若响应形状变则同步 `api/rescuer.js` 与 `components/TaskList.vue`

---

### Task 1: 数据库迁移、实体与 Assignment Mapper

**Files:**
- Create: `database/migrate_2026-10-22_dispatch_vehicle_assignment.sql`
- Modify: `database/init.sql`
- Create: `backend/src/main/java/com/example/backend/entity/DispatchVehicleAssignment.java`
- Create: `backend/src/main/java/com/example/backend/mapper/DispatchVehicleAssignmentMapper.java`
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchFieldRecord.java`
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchMedia.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchFieldRecordMapper.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchMediaMapper.java`

**Interfaces:**
- Produces:
  - Table `dispatch_vehicle_assignment` 列见 Step 1
  - `DispatchVehicleAssignment` Java 字段与表一致；非表字段可加 `vehiclePlate`、`rescuerName`
  - Mapper 方法：`insert`、`update`、`findById`、`findByOrderId`、`findByOrderIdAndRescuerId`、`findActiveByVehicleId`、`countActiveByVehicleId`、`existsByOrderIdAndVehicleId`、`findActiveByRescuerId`（任务列表用）
  - `dispatch_field_record.assignment_id`、`dispatch_media.assignment_id`；field_record 去掉仅 `uk_dispatch_order_id`，改为 `UNIQUE KEY uk_assignment_id (assignment_id)`（允许历史 `assignment_id` NULL 多行时用应用层保证；新数据必须带 assignment_id）

- [ ] **Step 1: 编写 migrate SQL**

```sql
-- database/migrate_2026-10-22_dispatch_vehicle_assignment.sql
CREATE TABLE IF NOT EXISTS `dispatch_vehicle_assignment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `vehicle_id` BIGINT NOT NULL,
  `rescuer_id` BIGINT NOT NULL COMMENT '派单时车辆绑定司机',
  `status` VARCHAR(20) NOT NULL COMMENT 'DISPATCHED/ACCEPTED/COMPLETED/REJECTED/ABORTED',
  `dispatched_at` DATETIME DEFAULT NULL,
  `accepted_at` DATETIME DEFAULT NULL,
  `checked_in_at` DATETIME DEFAULT NULL,
  `checkin_lng` DECIMAL(10,7) DEFAULT NULL,
  `checkin_lat` DECIMAL(10,7) DEFAULT NULL,
  `checkin_mode` VARCHAR(20) DEFAULT NULL,
  `checkin_remark` VARCHAR(500) DEFAULT NULL,
  `reject_reason` VARCHAR(500) DEFAULT NULL,
  `completed_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_vehicle` (`dispatch_order_id`, `vehicle_id`),
  KEY `idx_rescuer_status` (`rescuer_id`, `status`),
  KEY `idx_vehicle_status` (`vehicle_id`, `status`),
  KEY `idx_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单-施救车赋值';

-- 历史：有 vehicle_id 的工单各插一行
INSERT INTO `dispatch_vehicle_assignment` (
  `dispatch_order_id`, `vehicle_id`, `rescuer_id`, `status`,
  `dispatched_at`, `accepted_at`, `checked_in_at`, `checkin_lng`, `checkin_lat`,
  `checkin_mode`, `checkin_remark`, `reject_reason`, `completed_at`
)
SELECT
  o.`id`, o.`vehicle_id`,
  COALESCE(o.`rescuer_id`, 0),
  CASE
    WHEN o.`status` IN ('DISPATCHED','ACCEPTED','COMPLETED','ABORTED') THEN o.`status`
    WHEN o.`status` = 'PENDING' THEN 'REJECTED'
    ELSE 'ABORTED'
  END,
  o.`dispatched_at`, o.`accepted_at`, o.`checked_in_at`, o.`checkin_lng`, o.`checkin_lat`,
  o.`checkin_mode`, o.`checkin_remark`, o.`reject_reason`, o.`completed_at`
FROM `dispatch_order` o
WHERE o.`vehicle_id` IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `dispatch_vehicle_assignment` a
    WHERE a.`dispatch_order_id` = o.`id` AND a.`vehicle_id` = o.`vehicle_id`
  );

-- 若 rescuer_id 为 0（历史脏数据），迁移后应用层应拒绝再操作；可人工修数

ALTER TABLE `dispatch_field_record`
  ADD COLUMN `assignment_id` BIGINT DEFAULT NULL COMMENT '所属赋值' AFTER `dispatch_order_id`;

ALTER TABLE `dispatch_media`
  ADD COLUMN `assignment_id` BIGINT DEFAULT NULL COMMENT '所属赋值' AFTER `dispatch_order_id`;

-- 回填 field_record / media 的 assignment_id（按 order 唯一赋值）
UPDATE `dispatch_field_record` fr
  INNER JOIN `dispatch_vehicle_assignment` a ON a.`dispatch_order_id` = fr.`dispatch_order_id`
SET fr.`assignment_id` = a.`id`
WHERE fr.`assignment_id` IS NULL;

UPDATE `dispatch_media` m
  INNER JOIN `dispatch_vehicle_assignment` a ON a.`dispatch_order_id` = m.`dispatch_order_id`
SET m.`assignment_id` = a.`id`
WHERE m.`assignment_id` IS NULL;

-- 调整 field_record 唯一键：先删旧 uk，再按 assignment 唯一（NULL 不进入 uk 冲突的旧行保留）
ALTER TABLE `dispatch_field_record` DROP INDEX `uk_dispatch_order_id`;
ALTER TABLE `dispatch_field_record`
  ADD UNIQUE KEY `uk_assignment_id` (`assignment_id`);
```

同步把 `CREATE TABLE` 片段写入 `database/init.sql`（新库直接带 assignment 表；field/media 含 `assignment_id`；seed 数据若有工单需带赋值行）。

- [ ] **Step 2: 实体与 Mapper**

`DispatchVehicleAssignment.java` 使用 Lombok `@Data`，字段与表一一对应。

`DispatchVehicleAssignmentMapper.java` 注解 SQL 示例：

```java
@Select("SELECT * FROM dispatch_vehicle_assignment WHERE dispatch_order_id = #{orderId} ORDER BY id")
List<DispatchVehicleAssignment> findByOrderId(Long orderId);

@Select("SELECT * FROM dispatch_vehicle_assignment WHERE dispatch_order_id = #{orderId} AND rescuer_id = #{rescuerId} LIMIT 1")
DispatchVehicleAssignment findByOrderIdAndRescuerId(@Param("orderId") Long orderId, @Param("rescuerId") Long rescuerId);

@Select("SELECT COUNT(1) FROM dispatch_vehicle_assignment WHERE vehicle_id = #{vehicleId} AND status IN ('DISPATCHED','ACCEPTED')")
int countActiveByVehicleId(Long vehicleId);

@Select("SELECT COUNT(1) FROM dispatch_vehicle_assignment WHERE dispatch_order_id = #{orderId} AND vehicle_id = #{vehicleId}")
int countByOrderIdAndVehicleId(@Param("orderId") Long orderId, @Param("vehicleId") Long vehicleId);
```

`insert`/`update` 写全字段（与现有 Mapper 风格一致）。

Field/Media 实体与 insert/update/select 增加 `assignmentId`。

- [ ] **Step 3: 在开发库执行 migrate 并冒烟**

```bash
mysql -u root -p vue_springboot_system < database/migrate_2026-10-22_dispatch_vehicle_assignment.sql
```

Expected: 表存在；`DESC dispatch_vehicle_assignment` 列齐全；有历史车的工单对应至少一行赋值。

- [ ] **Step 4: Commit**

```bash
git add database/migrate_2026-10-22_dispatch_vehicle_assignment.sql database/init.sql \
  backend/src/main/java/com/example/backend/entity/DispatchVehicleAssignment.java \
  backend/src/main/java/com/example/backend/mapper/DispatchVehicleAssignmentMapper.java \
  backend/src/main/java/com/example/backend/entity/DispatchFieldRecord.java \
  backend/src/main/java/com/example/backend/entity/DispatchMedia.java \
  backend/src/main/java/com/example/backend/mapper/DispatchFieldRecordMapper.java \
  backend/src/main/java/com/example/backend/mapper/DispatchMediaMapper.java
git commit -m "feat: add dispatch_vehicle_assignment schema and mappers"
```

---

### Task 2: 赋值派单 + 工单状态汇总（后端核心）

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Modify: `backend/src/main/java/com/example/backend/service/RescueVehicleService.java`
- Modify: `backend/src/main/java/com/example/backend/dto/AssignDispatchRequest.java`
- Modify: `backend/src/main/java/com/example/backend/controller/DispatchController.java`
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`（`List<DispatchVehicleAssignment> assignments` 或 VO）
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`（`countDispatchedByVehicleId` 改为查 assignment 或删除改用 assignmentMapper）
- Test: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`

**Interfaces:**
- Consumes: `DispatchVehicleAssignmentMapper`；`RescueVehicleService.requireAssignable(Long vehicleId)`（非 OFFLINE、已绑司机；**不**要求 IDLE）
- Produces:
  - `void assign(Long orderId, Long vehicleId)` / `void assign(Long orderId, List<Long> vehicleIds)`
  - `void refreshOrderStatus(Long orderId)` — 按 Global Constraints 写回工单 status / completedAt
  - `void releaseVehicleIfUnused(Long vehicleId)` — `assignmentMapper.countActiveByVehicleId == 0` 则 `markIdle`
  - `findById` enrich：填充 `assignments`；兼容字段 `vehicleId`/`rescuerId` = 首个活跃赋值或第一行
  - create/update 预填：调用 `requireAssignable` 替代 `requireIdle`；**不** insert assignment、**不** markBusy

- [ ] **Step 1: 写失败测试（多车完成汇总 + 加派）**

在 `DispatchOrderServiceTest` 增加 mocks：`DispatchVehicleAssignmentMapper`。

```java
@Test
void assignCreatesAssignmentAndMarksBusyWithoutRequiringIdle() {
    DispatchOrder order = pendingOrder(1L);
    when(dispatchOrderMapper.findById(1L)).thenReturn(order);
    RescueVehicle v = busyVehicleWithDriver(10L, 100L); // status BUSY 也可派
    when(rescueVehicleMapper.findById(10L)).thenReturn(v);
    // requireAssignable 路径：mock rescueVehicleService 或 mapper
    when(assignmentMapper.countByOrderIdAndVehicleId(1L, 10L)).thenReturn(0);
    when(assignmentMapper.findByOrderId(1L)).thenReturn(List.of(dispatchedAssignment(1L, 10L, 100L)));

    service.assign(1L, 10L, null);

    verify(assignmentMapper).insert(argThat(a ->
            a.getVehicleId().equals(10L) && "DISPATCHED".equals(a.getStatus())
                    && a.getRescuerId().equals(100L)));
    verify(rescueVehicleService).markBusy(10L);
}

@Test
void orderCompletesOnlyWhenAllAssignmentsCompleted() {
    // 两车：一车 COMPLETED，一车 ACCEPTED → 工单仍 ACCEPTED
    // 两车都 COMPLETED → 工单 COMPLETED
}
```

再补：`rejectLastAssignmentReturnsPending`；`completeOrderRejectedWhileActiveAssignmentsRemain`（PC complete）。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
mvn -q -Dtest=DispatchOrderServiceTest test
```

Expected: 新用例 FAIL（旧 assign 仍 requireIdle / 写 order.vehicleId）。

- [ ] **Step 3: 实现 `requireAssignable` + 重构 assign/accept/reject/checkin/complete/abort**

`RescueVehicleService`:

```java
public RescueVehicle requireAssignable(Long vehicleId) {
    RescueVehicle v = requireExists(vehicleId);
    if ("OFFLINE".equals(v.getStatus())) {
        throw new RuntimeException("车辆已离线，无法派单");
    }
    if (v.getDriverUserId() == null) {
        throw new RuntimeException("该车辆未绑定施救员");
    }
    return v;
}
```

`DispatchOrderService.assign` 核心逻辑：

1. 订单存在；status 为 `PENDING`/`DISPATCHED`/`ACCEPTED` 可派（`COMPLETED`/`ABORTED` 不可）
2. `requireAssignable`；`assertHasRole(driver, TOW_DRIVER)`
3. `countByOrderIdAndVehicleId > 0` →「该车辆已派到本工单」
4. insert assignment `DISPATCHED`；`markBusy(vehicleId)`
5. 若工单 `dispatchedAt == null` 则设；`refreshOrderStatus(orderId)`
6. **不再**要求原 `PENDING` only；**不再** `requireIdle`

`accept`/`reject`/`checkin`：通过 `findByOrderIdAndRescuerId` 取赋值；改赋值行；`refreshOrderStatus`；reject 时 `releaseVehicleIfUnused`。

移动端 `complete(orderId, rescuerId)`：完成本人赋值（须已签到若 ACCEPTED）；`refreshOrderStatus`；`releaseVehicleIfUnused`。

PC `complete(orderId)`：若存在活跃赋值 →「仍有未完成的施救车辆」；否则若汇总已是 COMPLETED 可幂等；或仅当全部终态且有 COMPLETED 时允许（与自动汇总一致，按钮主要用于兼容——首期有活跃则拒绝）。

`abort(orderId)`：所有活跃赋值 → `ABORTED`；工单 `ABORTED`；对各 `vehicleId` `releaseVehicleIfUnused`。

`refreshOrderStatus` 实现 Global Constraints 表。

`releaseVehicleIfUnused`：改用 `assignmentMapper.countActiveByVehicleId`。

`RescueVehicleService.deleteVehicle` / `updateVehicle` 中「进行中工单」计数改为 `assignmentMapper.countActiveByVehicleId`（或 order mapper 委托）。

- [ ] **Step 4: 跑通 DispatchOrderServiceTest**

```bash
mvn -q -Dtest=DispatchOrderServiceTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/DispatchOrderService.java \
  backend/src/main/java/com/example/backend/service/RescueVehicleService.java \
  backend/src/main/java/com/example/backend/dto/AssignDispatchRequest.java \
  backend/src/main/java/com/example/backend/controller/DispatchController.java \
  backend/src/main/java/com/example/backend/entity/DispatchOrder.java \
  backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java \
  backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java
git commit -m "feat: assign multi vehicles via dispatch_vehicle_assignment"
```

---

### Task 3: nearby 推荐含 BUSY + hasActiveAssignment 垫底

**Files:**
- Modify: `backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java`
- Modify: `backend/src/main/java/com/example/backend/service/RescueVehicleService.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java`（若需 `findAssignable`：`status <> 'OFFLINE' AND driver_user_id IS NOT NULL`）
- Test: `backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java`

**Interfaces:**
- Produces: `NearbyVehicleVO.hasActiveAssignment`（boolean）；`findNearby` 排序见 Global Constraints

- [ ] **Step 1: 失败测试**

```java
@Test
void nearbyRanksVehiclesWithoutActiveAssignmentBeforeBusyOnes() {
    // 同片区两车：A 无活跃赋值，B 有活跃；A 应排在 B 前（即使 B 更近）
}
```

- [ ] **Step 2: 运行确认 FAIL**

```bash
mvn -q -Dtest=RescueVehicleServiceTest#nearbyRanksVehiclesWithoutActiveAssignmentBeforeBusyOnes test
```

- [ ] **Step 3: 实现**

候选改为 `findAssignable`（非 OFFLINE + 有 driver）。对每车：

```java
boolean active = assignmentMapper.countActiveByVehicleId(v.getId()) > 0;
vo.setHasActiveAssignment(active);
```

排序比较器（新鲜桶内）：

```java
Comparator
  .comparing(NearbyVehicleVO::isInMatchedDistrict).reversed()
  .thenComparing(NearbyVehicleVO::isHasActiveAssignment) // false 在前
  .thenComparing(NearbyVehicleVO::getDistanceMeters, Comparator.nullsLast(Double::compareTo));
```

- [ ] **Step 4: 测试 PASS + Commit**

```bash
mvn -q -Dtest=RescueVehicleServiceTest test
git add backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java \
  backend/src/main/java/com/example/backend/service/RescueVehicleService.java \
  backend/src/main/java/com/example/backend/mapper/RescueVehicleMapper.java \
  backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java
git commit -m "feat: rank nearby vehicles with active assignments last"
```

---

### Task 4: 移动端任务按赋值操作 + 现场媒体挂 assignment

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`（scene/park/media 若在此或 RescuerMobileService）
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/RescuerMobileController.java`（complete 传当前用户）
- Modify: `mobile-rescuer/src/pages/task/detail.vue`
- Modify: `mobile-rescuer/src/components/TaskList.vue`（若列表项需展示本车状态）
- Test: 扩展 `DispatchOrderServiceTest` 或 `RescuerMobileServiceTest`

**Interfaces:**
- 任务列表：按 `assignment.rescuer_id = 当前用户` 且赋值 status 过滤（todo: DISPATCHED/ACCEPTED；done: COMPLETED；aborted: REJECTED/ABORTED）
- 详情：返回工单 + **当前用户的 assignment** 过程字段（签到等从赋值读）；响应可把赋值过程映射到现有前端已用的 order 字段以减少 UI 改动，或显式 `assignment` 对象——**首期映射到 order 上同名字段** 以兼容现有 Vue
- scene/park/media：写入时带 `assignmentId`

- [ ] **Step 1: 测试「两车同单，司机甲只能操作自己的赋值」**

```java
@Test
void acceptFailsForOtherRescuerOnSameOrder() { ... }
```

- [ ] **Step 2: 实现列表/详情/scene/park/media/complete 走赋值；跑测**

- [ ] **Step 3: 移动端完成成功后若工单 status 非 COMPLETED，toast「本车已完成，整单尚有其他车辆未完成」**

在 `detail.vue` complete 成功回调根据返回的 order.status 判断。

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: rescuer mobile operates per vehicle assignment"
```

---

### Task 5: PC 派单详情多车列表与加派 UI

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`（附近列表展示「已有任务」角标；选车不限 IDLE）
- Modify: `frontend/src/api/dispatch.js`
- Modify: `frontend/src/api/vehicle.js`（若需）

**Interfaces:**
- 详情展示 `data.assignments[]`：车牌、司机、本车 status、派单时间
- 进行中也可点附近车辆「加派」→ `POST /api/dispatch/{id}/assign` body `{ vehicleId }` 或 `{ vehicleIds: [] }`
- 附近项：`hasActiveAssignment` 时显示「已有任务」；文案「附近空闲车辆」改为「附近可派车辆」
- 完成按钮：后端拒绝时展示错误信息

- [ ] **Step 1: 扩展 AssignDispatchRequest**

```java
@Data
public class AssignDispatchRequest {
    private Long vehicleId;
    private List<Long> vehicleIds;
    private Long rescuerId; // 忽略，以车辆绑定为准
}
```

Controller：合并 `vehicleId` 与 `vehicleIds` 去重后调用 service。

- [ ] **Step 2: 改 DispatchDetail.vue**

- 增加「已派车辆」表格绑定 `detail.assignments`
- `loadNearby` 使用返回顺序；badge `hasActiveAssignment`
- PENDING 与 DISPATCHED/ACCEPTED 均显示加派区（按权限）
- 去掉「必须 IDLE」前端过滤（若有）

- [ ] **Step 3: 手工验收清单**

1. 一单派两车，详情见两行
2. BUSY 车出现在附近列表靠后且可派
3. 两车都完成后工单 COMPLETED
4. 完成按钮在仅一车完成时失败提示

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: PC dispatch multi-vehicle assign UI and nearby badges"
```

---

### Task 6: 回归清理与文档

**Files:**
- Modify: 任何仍调用 `requireIdle` 于派单路径的代码改为 `requireAssignable`
- Modify: `mobile-rescuer/README.md` 或 `backend/README.md` 一句说明多车（可选，保持最短）
- 确认 `DispatchOrderMapper.countDispatchedByVehicleId` 实现改为 assignment 计数，避免双轨

- [ ] **Step 1: 全量相关单测**

```bash
cd backend
mvn -q -Dtest=DispatchOrderServiceTest,RescueVehicleServiceTest test
```

Expected: PASS

- [ ] **Step 2: 搜索残留**

```bash
rg "requireIdle" backend/src/main/java
rg "countDispatchedByVehicleId" backend
```

派单路径不应再 `requireIdle`；计数应指向 assignment。

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: finish multi-vehicle dispatch regression cleanup"
```

---

## Self-Review (plan vs spec)

| Spec 要求 | Task |
|-----------|------|
| assignment 表 + 迁移 | Task 1 |
| 工单汇总状态机 | Task 2 |
| 加派、不要求 IDLE | Task 2 |
| nearby 垫底 | Task 3 |
| 每车独立接签采完 + 媒体 assignment | Task 4 |
| PC 多车 UI | Task 5 |
| 不做 capacity / 小程序 | Global Constraints |
| 测试要点（两车、一车两单、退单回 PENDING） | Task 2/3/4 测试 |

无 TBD 占位；接口名与实体字段在 Task 1–2 已钉死。
