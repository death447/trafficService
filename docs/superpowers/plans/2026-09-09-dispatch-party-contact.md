# 工单事故当事人与联系方式 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工单增加选填事故当事人、手机号码；PENDING 可编辑；列表在施救车辆后展示；移动端任务详情只读。

**Architecture:** `dispatch_order` 增加 `party_name` / `party_phone`；Service create/update trim 空串为 null；管理端 Create/List/Detail 与移动端 detail 只读展示。无新权限。

**Tech Stack:** Spring Boot 3.2、MyBatis 注解、MySQL、JUnit5+Mockito、Vue 3、uni-app

**Spec:** `docs/superpowers/specs/2026-09-09-dispatch-party-contact-design.md`

## Global Constraints

- 当事人、手机号均为**选填**；空串 → `null`
- `party_name` 最长 50；`party_phone` 最长 20；前端 `maxlength`；不做手机号强正则
- 仅 **PENDING** 可编辑（沿用现有 `update` 门禁）
- 列表列序：施救车辆 → 事故当事人 → 联系方式 → 车牌 → 车型
- 移动端任务详情**只读**；不改 scene/park
- 无新权限码；YAGNI：无筛选、无独立表

## File Structure

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-09_dispatch_party_contact.sql`

### Backend
- Modify: `entity/DispatchOrder.java`
- Modify: `mapper/DispatchOrderMapper.java`
- Modify: `service/DispatchOrderService.java` — `normalizePartyContact`
- Modify: `test/.../DispatchOrderServiceTest.java`

### Frontend
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`
- Modify: `frontend/src/views/dispatch/DispatchList.vue`
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

### Mobile
- Modify: `mobile-rescuer/src/pages/task/detail.vue`

---

### Task 1: 数据库

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-09_dispatch_party_contact.sql`

**Interfaces:**
- Produces: columns `party_name` VARCHAR(50), `party_phone` VARCHAR(20) after `vehicle_type_name`

- [ ] **Step 1: 改 `init.sql` 中 `dispatch_order` DDL**

在 `vehicle_type_name` 后增加：

```sql
  `party_name` VARCHAR(50) DEFAULT NULL COMMENT '事故当事人',
  `party_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式/手机号码',
```

- [ ] **Step 2: 写 migrate**

`database/migrate_2026-09-09_dispatch_party_contact.sql`：

```sql
ALTER TABLE `dispatch_order`
  ADD COLUMN `party_name` VARCHAR(50) DEFAULT NULL COMMENT '事故当事人' AFTER `vehicle_type_name`,
  ADD COLUMN `party_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式/手机号码' AFTER `party_name`;
```

- [ ] **Step 3: Commit**

```bash
git add database/init.sql database/migrate_2026-09-09_dispatch_party_contact.sql
git commit -m "chore(db): add dispatch party_name and party_phone columns"
```

---

### Task 2: 后端 Entity / Mapper / Service

**Files:**
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java`
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Modify: `backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java`

**Interfaces:**
- Produces: `DispatchOrder.partyName` / `partyPhone`；`normalizePartyContact(order)`；create/update 调用

- [ ] **Step 1: 写失败测试**

在 `DispatchOrderServiceTest` 增加（参照现有 create mock 风格；若已有 `createWritesVehicleTypeSnapshot` 等，复用其 mapper stub 模式）：

```java
@Test
void createNormalizesBlankPartyFieldsToNull() {
    // Arrange: capture inserted order; partyName="  ", partyPhone="" → both null after create
    // Assert insert captors partyName/partyPhone are null
}

@Test
void createPersistsTrimmedPartyFields() {
    // partyName=" 张三 ", partyPhone=" 13800138000 " → "张三", "13800138000"
}

@Test
void updateCopiesPartyFieldsWhenPending() {
    // existing PENDING; incoming partyName/phone set on existing before update
}
```

实现时可对照文件中已有 `create`/`update` 测试的 Mockito 脚手架复制精简。

- [ ] **Step 2: Run — expect FAIL/compile error**

```bash
cd backend && mvn test -Dtest=DispatchOrderServiceTest#createNormalizesBlankPartyFieldsToNull
```

（Windows 可用 `.tools/apache-maven/bin/mvn.cmd` + `JAVA_HOME` jdk-17）

- [ ] **Step 3: Entity 加字段**

在 `vehicleTypeName` 后：

```java
private String partyName;
private String partyPhone;
```

- [ ] **Step 4: Mapper insert/update**

Insert 列清单在 `vehicle_type_name` 后加 `party_name, party_phone`，VALUES 加 `#{partyName}, #{partyPhone}`。

Update SET 在 `vehicle_type_name = #{vehicleTypeName}` 后加：

```sql
party_name = #{partyName}, party_phone = #{partyPhone},
```

- [ ] **Step 5: Service**

增加：

```java
void normalizePartyContact(DispatchOrder order) {
    if (order.getPartyName() != null) {
        String n = order.getPartyName().trim();
        order.setPartyName(n.isEmpty() ? null : n);
    }
    if (order.getPartyPhone() != null) {
        String p = order.getPartyPhone().trim();
        order.setPartyPhone(p.isEmpty() ? null : p);
    }
}
```

`create`：在 `applyPlateAndVehicleType(order)` 之后（或之前）调用 `normalizePartyContact(order)`。

`update`：在设置 plate 附近增加：

```java
existing.setPartyName(order.getPartyName());
existing.setPartyPhone(order.getPartyPhone());
normalizePartyContact(existing);
```

- [ ] **Step 6: Run tests — PASS**

```bash
cd backend && mvn test -Dtest=DispatchOrderServiceTest
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/DispatchOrder.java \
  backend/src/main/java/com/example/backend/mapper/DispatchOrderMapper.java \
  backend/src/main/java/com/example/backend/service/DispatchOrderService.java \
  backend/src/test/java/com/example/backend/service/DispatchOrderServiceTest.java
git commit -m "feat(backend): persist optional party name and phone on dispatch orders"
```

---

### Task 3: 管理端 Create / List / Detail

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchCreate.vue`
- Modify: `frontend/src/views/dispatch/DispatchList.vue`
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

**Interfaces:**
- Consumes: API 已返回 `partyName` / `partyPhone`（camelCase）
- Produces: 表单提交与列表列

- [ ] **Step 1: Create 表单**

在车牌/车型附近（建议车型后）增加：

```html
<label>
  事故当事人
  <input v-model.trim="form.partyName" placeholder="选填" maxlength="50" />
</label>
<label>
  手机号码
  <input v-model.trim="form.partyPhone" placeholder="选填" maxlength="20" />
</label>
```

`form` 增加 `partyName: ''`, `partyPhone: ''`。

`createDispatch` body 增加：

```js
partyName: form.partyName || null,
partyPhone: form.partyPhone || null,
```

- [ ] **Step 2: List 列**

表头在「施救车辆」后插入「事故当事人」「联系方式」；行数据：

```html
<td>{{ row.partyName || '—' }}</td>
<td>{{ row.partyPhone || '—' }}</td>
```

完整序：… 施救车辆 | 事故当事人 | 联系方式 | 车牌 | 车型 | …

- [ ] **Step 3: Detail 只读 + PENDING 编辑**

只读信息区增加当事人、联系方式。

PENDING 编辑面板（与车牌车型同区）增加两输入；`editForm` 增加字段；`syncEditForm` / `onSaveEdit` 的 `updateDispatch` payload 带上 `partyName`/`partyPhone`（空串转 null）。

- [ ] **Step 4: 手工冒烟（可记入报告）**

新建填/不填 → 列表列序与值 → PENDING 改保存。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/dispatch/DispatchCreate.vue \
  frontend/src/views/dispatch/DispatchList.vue \
  frontend/src/views/dispatch/DispatchDetail.vue
git commit -m "feat(frontend): party name and phone on dispatch create list detail"
```

---

### Task 4: 移动端任务详情只读

**Files:**
- Modify: `mobile-rescuer/src/pages/task/detail.vue`

**Interfaces:**
- Consumes: `order.partyName` / `order.partyPhone`（任务详情已含 order）

- [ ] **Step 1: 模板**

在事故地址/救援事由附近增加：

```html
<view class="line">事故当事人：{{ order.partyName || '-' }}</view>
<view class="line">联系方式：{{ order.partyPhone || '-' }}</view>
```

- [ ] **Step 2: Commit**

```bash
git add mobile-rescuer/src/pages/task/detail.vue
git commit -m "feat(mobile): show party name and phone on rescuer task detail"
```

---

## Spec coverage

| Spec | Task |
|------|------|
| DB columns + migrate/init | 1 |
| Entity/Mapper/normalize + PENDING update | 2 |
| Create / List order / Detail edit+readonly | 3 |
| Mobile readonly | 4 |
| 不做筛选/正则/scene | 全局约束 |

## Placeholder / consistency

- 字段名统一：`partyName` / `partyPhone` ↔ `party_name` / `party_phone`
- 列表文案：「事故当事人」「联系方式」
