# 工单多事故车 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工单支持多辆事故车，每车独立车牌/车型/当事人/电话；子表存储，PENDING 可全量替换；主表同步首条快照；扣押按子表车牌匹配。

**Architecture:** 新表 `dispatch_accident_vehicle` + Entity/Mapper；`DispatchOrder.accidentVehicles`；`DispatchOrderService` 在 create/update 校验并 `replaceAccidentVehicles`；详情 enrich 加载子表；`DetainedVehicleService.listActiveOrdersByPlate` 改查子表；PC 创建/详情多行表单；移动/交警详情只读列表。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、Vue 3、uni-app、MySQL

**Spec:** `docs/superpowers/specs/2026-10-26-dispatch-multi-accident-vehicle-design.md`

## Global Constraints

- 至少 1 辆事故车；每辆 `plateNo` trim 后非空；同单车牌去重（`PlateNos.normalize`）
- 车型/当事人/电话选填；`vehicleTypeId` 非空须 ENABLED，并写 `vehicleTypeName` 快照
- 仅 `PENDING` 可改事故车列表（create 始终 PENDING；update 非 PENDING 拒绝改子表——update 本身已仅 PENDING）
- 全量替换：先 `deleteByOrderId` 再按 `sort_order` 插入
- 写完后将 `sort_order=0` 同步到 `dispatch_order.plate_no` / 车型 / party 列
- 列表继续用主表快照；详情返回 `accidentVehicles[]`
- 报表本期不改；现场采集不绑子表行
- 统一 `Result`；`RuntimeException` → `Result.error`
- TDD；每任务结束提交

## File Structure

### Database
- Create: `database/migrate_2026-10-26_dispatch_accident_vehicle.sql`
- Modify: `database/init.sql`

### Backend
- Create: `entity/DispatchAccidentVehicle.java`、`mapper/DispatchAccidentVehicleMapper.java`
- Modify: `entity/DispatchOrder.java`（`List<DispatchAccidentVehicle> accidentVehicles`）
- Modify: `service/DispatchOrderService.java`、`service/DetainedVehicleService.java`
- Modify: `mapper/DispatchOrderMapper.java`（`findActiveWithPlate` 可保留或改为 join；扣押改用子表查询）
- Test: `DispatchOrderServiceTest.java`、`DetainedVehicleServiceTest.java`（若有）

### Frontend
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`、`DispatchDetail.vue`
- 列表可不动（主表快照）

### Mobile
- Modify: `mobile-rescuer/src/pages/task/detail.vue`、`pages/police/detail.vue`（只读多车列表；scene 预填仍用首条/`order.plateNo`）

---

### Task 1: 数据库 + Entity + Mapper

**Files:**
- Create: `database/migrate_2026-10-26_dispatch_accident_vehicle.sql`
- Modify: `database/init.sql`
- Create: `backend/src/main/java/com/example/backend/entity/DispatchAccidentVehicle.java`
- Create: `backend/src/main/java/com/example/backend/mapper/DispatchAccidentVehicleMapper.java`

**Interfaces:**
- Produces: 表；实体字段与表一致；Mapper：`insert`、`deleteByOrderId`、`findByOrderId`（ORDER BY sort_order,id）、`findByNormalizedPlateActive`（或 Service 层滤）

- [ ] **Step 1: migrate SQL**

```sql
CREATE TABLE IF NOT EXISTS `dispatch_accident_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `plate_no` VARCHAR(20) NOT NULL,
  `vehicle_type_id` BIGINT DEFAULT NULL,
  `vehicle_type_name` VARCHAR(50) DEFAULT NULL,
  `party_name` VARCHAR(50) DEFAULT NULL,
  `party_phone` VARCHAR(20) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`dispatch_order_id`),
  KEY `idx_plate_no` (`plate_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单事故车辆';

INSERT INTO `dispatch_accident_vehicle` (
  `dispatch_order_id`, `sort_order`, `plate_no`, `vehicle_type_id`, `vehicle_type_name`,
  `party_name`, `party_phone`
)
SELECT o.`id`, 0,
  COALESCE(NULLIF(TRIM(o.`plate_no`), ''), '未知'),
  o.`vehicle_type_id`, o.`vehicle_type_name`, o.`party_name`, o.`party_phone`
FROM `dispatch_order` o
WHERE (
  (o.`plate_no` IS NOT NULL AND TRIM(o.`plate_no`) <> '')
  OR (o.`party_name` IS NOT NULL AND TRIM(o.`party_name`) <> '')
  OR (o.`party_phone` IS NOT NULL AND TRIM(o.`party_phone`) <> '')
  OR o.`vehicle_type_id` IS NOT NULL
)
AND NOT EXISTS (
  SELECT 1 FROM `dispatch_accident_vehicle` a WHERE a.`dispatch_order_id` = o.`id`
);
```

注意：无车牌但有当事人的历史行用占位牌 `'未知'` 以满足 NOT NULL——若产品不愿占位，可改为仅迁移有 `plate_no` 的行（**采用：仅 `plate_no` 非空才迁移**；纯当事人无牌的旧数据不迁子表，create 新规则强制有牌）。

修订迁移 WHERE：仅 `plate_no IS NOT NULL AND TRIM(plate_no) <> ''`，`plate_no` 用 trim 后值。

同步 `init.sql`：DROP + CREATE；无种子工单则可不插种子行。

- [ ] **Step 2: Entity + Mapper**

```java
@Data
public class DispatchAccidentVehicle {
    private Long id;
    private Long dispatchOrderId;
    private Integer sortOrder;
    private String plateNo;
    private Long vehicleTypeId;
    private String vehicleTypeName;
    private String partyName;
    private String partyPhone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

Mapper 注解 SQL：`findByOrderId`、`deleteByOrderId`、`insert`；另：

```java
@Select("SELECT a.* FROM dispatch_accident_vehicle a "
      + "INNER JOIN dispatch_order o ON o.id = a.dispatch_order_id "
      + "WHERE o.status IN ('PENDING','DISPATCHED','ACCEPTED')")
List<DispatchAccidentVehicle> findAllOnActiveOrders();
```

- [ ] **Step 3: 执行 migrate（开发库）并 DESC 冒烟**

```bash
# mysql source .../migrate_2026-10-26_dispatch_accident_vehicle.sql
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add dispatch_accident_vehicle schema and mapper"
```

---

### Task 2: DispatchOrderService 校验、替换与详情 enrich

**Files:**
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Test: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`

**Interfaces:**
- Consumes: `DispatchAccidentVehicleMapper`；既有 `applyPlateAndVehicleType` / `PlateNos` / `accidentVehicleTypeService`
- Produces:
  - `void replaceAccidentVehicles(Long orderId, List<DispatchAccidentVehicle> vehicles)`
  - create/update 调用之；`findById` 设置 `order.accidentVehicles`
  - 同步首条到主表字段后再 `dispatchOrderMapper.update`

- [ ] **Step 1: 失败测试**

```java
@Test
void createRequiresAtLeastOneAccidentVehicleWithPlate() {
    DispatchOrder order = basePendingOrder();
    order.setAccidentVehicles(List.of());
    assertThrows(RuntimeException.class, () -> service.create(order, 7L));
}

@Test
void createRejectsDuplicatePlates() { /* 粤B1 两次 */ }

@Test
void createPersistsTwoAccidentVehiclesAndSyncsFirstToOrder() {
    // verify accidentVehicleMapper.insert x2; order.plateNo == first
}
```

Mock `@Mock DispatchAccidentVehicleMapper accidentVehicleMapper`。

- [ ] **Step 2: 跑测确认 FAIL**

```bash
cd backend
mvn -q -Dtest=DispatchOrderServiceTest#createRequiresAtLeastOneAccidentVehicleWithPlate test
```

- [ ] **Step 3: 实现**

`normalizeAndValidateAccidentVehicles(List<...>)`：
1. null/empty →「请至少添加一辆事故车」
2. 每条 trim plate；空 →「事故车牌不能为空」
3. `PlateNos.normalize` 去重失败 →「事故车牌重复」
4. party 空串→null；车型走 `requireEnabled` 写快照（无 id 则清空 name）

`replaceAccidentVehicles`：deleteByOrderId；for i insert sortOrder=i；取 first 写回 order 主字段。

`create`：在 insert order 之后调用 replace（需先有 order.id）。若前端仍只传旧单字段无 `accidentVehicles`：兼容——若 list 空且 `order.plateNo` 非空，合成单元素列表。

`update`：仅 PENDING（已有）；replace + update 主表。

`findById` / list enrich：`setAccidentVehicles(findByOrderId)`。

- [ ] **Step 4: 测试 PASS + Commit**

```bash
git commit -m "feat: persist multiple accident vehicles on dispatch create/update"
```

---

### Task 3: 扣押按子表车牌匹配

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Test: `DetainedVehicleServiceTest`（有则扩；无则在 Dispatch 测或新建最小测）

**Interfaces:**
- 使用 `accidentVehicleMapper.findAllOnActiveOrders()`，按 normalize 匹配；`ActiveDispatchSummary` 填匹配行的 plate/type

- [ ] **Step 1: 测试「第二辆车牌能命中」**（mock 子表两行不同牌，needle=第二牌）

- [ ] **Step 2: 改 `listActiveOrdersByPlate` 实现**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: match detain active orders by accident vehicle plates"
```

---

### Task 4: PC 新建/详情多行表单

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

**Interfaces:**
- 提交/保存 payload：`accidentVehicles: [{ plateNo, vehicleTypeId, partyName, partyPhone }]`（vehicleTypeId 空串不传或 null）
- 详情只读：`order.accidentVehicles` 表格；PENDING 编辑同创建

- [ ] **Step 1: DispatchCreate** — 用 `accidentVehicles: reactive([{ plateNo:'', vehicleTypeId:'', partyName:'', partyPhone:'' }])` 替换单字段；UI 循环 + 添加/删除；submit 校验至少一行有车牌；组装 body

- [ ] **Step 2: DispatchDetail** — 信息区展示列表；PENDING 编辑区多行；`onSaveEdit` 传 `accidentVehicles`

- [ ] **Step 3: 浏览器冒烟** — 建 2 车工单；详情可见；PENDING 改删行保存

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: PC multi accident-vehicle form on create and detail"
```

---

### Task 5: 移动端/交警详情只读多车 + 现场预填

**Files:**
- Modify: `mobile-rescuer/src/pages/task/detail.vue`
- Modify: `mobile-rescuer/src/pages/police/detail.vue`（若展示 party/plate）
- Modify: `mobile-rescuer/src/pages/task/scene.vue` — 预填：`order.accidentVehicles?.[0] || order`

- [ ] **Step 1: 详情增加事故车区块（v-for）**

- [ ] **Step 2: scene 预填首条**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: show multi accident vehicles on mobile task details"
```

---

### Task 6: 回归

- [ ] **Step 1:** `mvn -q -Dtest=DispatchOrderServiceTest,DetainedVehicleServiceTest test`

- [ ] **Step 2:** `rg "setPlateNo|accidentVehicles" backend/src/main/java/com/example/backend/service/DispatchOrderService.java` 确认写路径走 replace

- [ ] **Step 3:** Commit 若有清理

```bash
git commit -m "chore: finish multi accident-vehicle regression"
```

---

## Self-Review (plan vs spec)

| Spec | Task |
|------|------|
| 子表 + 迁移 | Task 1 |
| 校验至少 1 / 车牌必填 / 去重 | Task 2 |
| PENDING 全量替换 + 首条同步主表 | Task 2 |
| PC 多行 | Task 4 |
| 扣押子表匹配 | Task 3 |
| 移动详情/预填 | Task 5 |
| 报表不改、现场不绑子表 | Global / 非目标 |
