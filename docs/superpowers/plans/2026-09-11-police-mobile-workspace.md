# 交警移动端工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在同一 `mobile-rescuer` H5 中增加交警工作台：全部任务列表、只读详情、已完成工单四维评价；权限分流进入 `workspace=police`。

**Architecture:** 登录按 `accident:*` 判定交警权限。后端竖切 `/api/mobile/police`（列表/详情/评价），评价表 `dispatch_order_evaluation` 一单唯一。移动端第三套工作台组件，不复用施救员详情页，不授 `dispatch:*`。

**Tech Stack:** Spring Boot 3.2, MyBatis, JUnit5 + Mockito, uni-app Vue3 H5, JWT RBAC

**Spec:** `docs/superpowers/specs/2026-09-11-police-mobile-workspace-design.md`

## Global Constraints

- One app: `mobile-rescuer/` only; do not create a second mobile project
- `hasPolice = accident:manage` or any `accident:*`; do not grant `dispatch:*` to `TRAFFIC_POLICE`
- Multi-permission users pick a workspace; switch requires logout (no switch on 我的)
- Single-permission users skip the picker
- Police workspace: no GPS reporter, no bind-vehicle UI
- Evaluate only `COMPLETED`; one evaluation per order; cannot edit after submit
- Default 5 stars is UI-only; server does not fill missing scores
- No district/大队 filter this round
- No PC 评价分析; do not change dispatch state machine or `DispatchController`
- YAGNI: no 交警建单/派单/接单/签到/现场采集

## File Structure

### Database
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-11_police_mobile.sql`

### Backend
- Create: `backend/src/main/java/com/example/backend/entity/DispatchOrderEvaluation.java`
- Create: `backend/src/main/java/com/example/backend/mapper/DispatchOrderEvaluationMapper.java`
- Create: `backend/src/main/java/com/example/backend/dto/PoliceTaskItem.java`
- Create: `backend/src/main/java/com/example/backend/dto/PoliceTaskDetail.java`
- Create: `backend/src/main/java/com/example/backend/dto/RateTaskRequest.java`
- Create: `backend/src/main/java/com/example/backend/service/PoliceMobileService.java`
- Create: `backend/src/main/java/com/example/backend/controller/PoliceMobileController.java`
- Test: `backend/src/test/java/com/example/backend/service/PoliceMobileServiceTest.java`

### Mobile
- Modify: `mobile-rescuer/src/utils/workspace.js`
- Modify: `mobile-rescuer/src/utils/workspace.test.cjs`
- Modify: `mobile-rescuer/src/utils/guard.js`
- Create: `mobile-rescuer/src/api/police.js`
- Create: `mobile-rescuer/src/components/PoliceTaskList.vue`
- Create: `mobile-rescuer/src/pages/police/detail.vue`
- Create: `mobile-rescuer/src/pages/police/rate.vue`
- Modify: `mobile-rescuer/src/pages.json`
- Modify: `mobile-rescuer/src/pages/workbench/index.vue`
- Modify: `mobile-rescuer/src/pages/workspace/select.vue`
- Modify: `mobile-rescuer/src/pages/login/index.vue`
- Modify: `mobile-rescuer/README.md`

---

### Task 1: 评价表、权限、演示账号、Entity/Mapper

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-11_police_mobile.sql`
- Create: `backend/src/main/java/com/example/backend/entity/DispatchOrderEvaluation.java`
- Create: `backend/src/main/java/com/example/backend/mapper/DispatchOrderEvaluationMapper.java`

**Interfaces:**
- Produces: table `dispatch_order_evaluation`; permissions `accident:query` (id 68), `accident:rate` (id 69); user `trafficpolice`; mapper `findByOrderId`, `findOrderIds`, `insert`

- [ ] **Step 1: Write migrate SQL**

Create `database/migrate_2026-09-11_police_mobile.sql`:

```sql
CREATE TABLE IF NOT EXISTS `dispatch_order_evaluation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL COMMENT '工单 id',
  `rater_user_id` BIGINT NOT NULL COMMENT '评价人',
  `score_punctual` TINYINT NOT NULL COMMENT '到达及时 1-5',
  `score_standard` TINYINT NOT NULL COMMENT '处置规范 1-5',
  `score_safety` TINYINT NOT NULL COMMENT '操作安全 1-5',
  `score_attitude` TINYINT NOT NULL COMMENT '服务态度 1-5',
  `comment` VARCHAR(500) DEFAULT NULL COMMENT '反馈意见',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交警工单评价';

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 68 AS id, '事故查询' AS permission_name, 'accident:query' AS permission_code,
         'BUTTON' AS permission_type, 17 AS parent_id, 1 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 68 OR `permission_code` = 'accident:query');

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 69 AS id, '事故评价' AS permission_name, 'accident:rate' AS permission_code,
         'BUTTON' AS permission_type, 17 AS parent_id, 2 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 69 OR `permission_code` = 'accident:rate');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, 68 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 1 AND `permission_id` = 68
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, 69 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 1 AND `permission_id` = 69
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 17 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 17
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 68 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 68
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 69 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 69
);

INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`)
SELECT 'trafficpolice', 'police@example.com',
       '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
       '13800000003', '交警演示', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'trafficpolice');

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, 1 FROM `user` u
WHERE u.username = 'trafficpolice'
  AND NOT EXISTS (SELECT 1 FROM `user_role` ur WHERE ur.user_id = u.id AND ur.role_id = 1);
```

- [ ] **Step 2: Mirror seeds in `database/init.sql`**

After permission id 67, add:

```sql
(68, '事故查询', 'accident:query', 'BUTTON', 17, 1),
(69, '事故评价', 'accident:rate', 'BUTTON', 17, 2);
```

(Keep the previous 67 row ending with `;` — change it to `,` then append 68/69.)

Change ADMIN grant to also include accident:

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id IN (16, 17, 19)
   OR id BETWEEN 20 AND 66
   OR id IN (68, 69);
```

Replace TRAFFIC_POLICE grant:

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 17), (1, 68), (1, 69);
```

After `parkingadmin` user_role insert, add:

```sql
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('trafficpolice', 'police@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000003', '交警演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT id, 1 FROM `user` WHERE username = 'trafficpolice';
```

After `CREATE TABLE detain_media ...`, add the same `CREATE TABLE dispatch_order_evaluation` as in migrate (before role inserts is also fine; place it after `detain_media`).

- [ ] **Step 3: Entity + Mapper**

`DispatchOrderEvaluation.java`:

```java
package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchOrderEvaluation {
    private Long id;
    private Long dispatchOrderId;
    private Long raterUserId;
    private Integer scorePunctual;
    private Integer scoreStandard;
    private Integer scoreSafety;
    private Integer scoreAttitude;
    private String comment;
    private LocalDateTime createTime;
}
```

`DispatchOrderEvaluationMapper.java`:

```java
package com.example.backend.mapper;

import com.example.backend.entity.DispatchOrderEvaluation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DispatchOrderEvaluationMapper {

    @Select("SELECT * FROM dispatch_order_evaluation WHERE dispatch_order_id = #{orderId}")
    DispatchOrderEvaluation findByOrderId(Long orderId);

    @Select({
            "<script>",
            "SELECT dispatch_order_id FROM dispatch_order_evaluation",
            "WHERE dispatch_order_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<Long> findOrderIds(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO dispatch_order_evaluation (dispatch_order_id, rater_user_id, score_punctual, "
            + "score_standard, score_safety, score_attitude, comment) VALUES (#{dispatchOrderId}, "
            + "#{raterUserId}, #{scorePunctual}, #{scoreStandard}, #{scoreSafety}, #{scoreAttitude}, #{comment})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchOrderEvaluation row);
}
```

- [ ] **Step 4: Compile**

Run: `mvn -f backend/pom.xml -q -DskipTests compile`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add database/init.sql database/migrate_2026-09-11_police_mobile.sql backend/src/main/java/com/example/backend/entity/DispatchOrderEvaluation.java backend/src/main/java/com/example/backend/mapper/DispatchOrderEvaluationMapper.java
git commit -m "feat: add police evaluation table and accident query/rate permissions"
```

---

### Task 2: `PoliceMobileService` 列表与详情（TDD）

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/PoliceTaskItem.java`
- Create: `backend/src/main/java/com/example/backend/dto/PoliceTaskDetail.java`
- Create: `backend/src/main/java/com/example/backend/service/PoliceMobileService.java`
- Test: `backend/src/test/java/com/example/backend/service/PoliceMobileServiceTest.java`

**Interfaces:**
- Consumes: `DispatchOrderMapper.count/selectPage/findById`; `DispatchFieldRecordMapper.findByOrderId`; `DispatchMediaMapper.findByOrderId`; `DispatchOrderEvaluationMapper.findByOrderId/findOrderIds`
- Produces: `PoliceMobileService.list(PageParams) -> Map<String,Object>` (`list`/`total`/`page`/`size`); `getTask(Long id) -> PoliceTaskDetail`

- [ ] **Step 1: Write failing tests**

Create DTOs first so tests compile against types (empty service methods can wait — write tests that call `service.list` / `getTask`; they fail until the class exists). Prefer: create empty `PoliceMobileService` with the two methods throwing `UnsupportedOperationException`, then tests fail on assertions.

`PoliceTaskItem.java`:

```java
package com.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PoliceTaskItem {
    private Long id;
    private String orderNo;
    private String status;
    private String accidentAddress;
    private String plateNo;
    private String partyName;
    private String partyPhone;
    private LocalDateTime createTime;
    private boolean rated;
}
```

`PoliceTaskDetail.java`:

```java
package com.example.backend.dto;

import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchOrderEvaluation;
import lombok.Data;

import java.util.List;

@Data
public class PoliceTaskDetail {
    private DispatchOrder order;
    private DispatchFieldRecord fieldRecord;
    private List<DispatchMedia> medias;
    private DispatchOrderEvaluation evaluation;
}
```

`PoliceMobileServiceTest.java`:

```java
package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.PoliceTaskDetail;
import com.example.backend.dto.PoliceTaskItem;
import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchOrderEvaluation;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoliceMobileServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock DispatchFieldRecordMapper fieldRecordMapper;
    @Mock DispatchMediaMapper mediaMapper;
    @Mock DispatchOrderEvaluationMapper evaluationMapper;
    @InjectMocks PoliceMobileService service;

    @Test
    void listMarksRatedAndPaginates() {
        DispatchOrder a = new DispatchOrder();
        a.setId(1L);
        a.setOrderNo("RO1");
        a.setStatus("COMPLETED");
        a.setAccidentAddress("路A");
        a.setPlateNo("粤B1");
        a.setPartyName("张三");
        a.setCreateTime(LocalDateTime.parse("2026-09-01T10:00:00"));
        DispatchOrder b = new DispatchOrder();
        b.setId(2L);
        b.setOrderNo("RO2");
        b.setStatus("ACCEPTED");
        when(dispatchOrderMapper.count(null, null, null, null)).thenReturn(2L);
        when(dispatchOrderMapper.selectPage(null, null, null, null, 0, 10)).thenReturn(List.of(a, b));
        when(evaluationMapper.findOrderIds(List.of(1L, 2L))).thenReturn(List.of(1L));

        Map<String, Object> result = service.list(PageParams.normalize(1, 10));
        assertEquals(2L, result.get("total"));
        assertEquals(1, result.get("page"));
        @SuppressWarnings("unchecked")
        List<PoliceTaskItem> list = (List<PoliceTaskItem>) result.get("list");
        assertEquals(2, list.size());
        assertEquals("RO1", list.get(0).getOrderNo());
        assertTrue(list.get(0).isRated());
        assertFalse(list.get(1).isRated());
        assertEquals("张三", list.get(0).getPartyName());
    }

    @Test
    void listEmptyPageDoesNotQueryRatedIds() {
        when(dispatchOrderMapper.count(null, null, null, null)).thenReturn(0L);
        when(dispatchOrderMapper.selectPage(null, null, null, null, 0, 10)).thenReturn(List.of());
        Map<String, Object> result = service.list(PageParams.normalize(1, 10));
        assertEquals(0L, result.get("total"));
        assertEquals(List.of(), result.get("list"));
    }

    @Test
    void getTaskReturnsRecordMediaAndNullEvaluation() {
        DispatchOrder order = new DispatchOrder();
        order.setId(8L);
        order.setStatus("ACCEPTED");
        order.setCheckedInAt(LocalDateTime.parse("2026-09-01T11:00:00"));
        DispatchFieldRecord rec = new DispatchFieldRecord();
        rec.setPlateNo("粤B8");
        DispatchMedia m = new DispatchMedia();
        m.setBizType("DAMAGE");
        m.setFilePath("dispatch/8/a.jpg");
        when(dispatchOrderMapper.findById(8L)).thenReturn(order);
        when(fieldRecordMapper.findByOrderId(8L)).thenReturn(rec);
        when(mediaMapper.findByOrderId(8L)).thenReturn(List.of(m));
        when(evaluationMapper.findByOrderId(8L)).thenReturn(null);

        PoliceTaskDetail d = service.getTask(8L);
        assertEquals(8L, d.getOrder().getId());
        assertEquals("粤B8", d.getFieldRecord().getPlateNo());
        assertEquals(1, d.getMedias().size());
        assertNull(d.getEvaluation());
    }

    @Test
    void getTaskMissingOrderThrows() {
        when(dispatchOrderMapper.findById(9L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getTask(9L));
        assertEquals("工单不存在", ex.getMessage());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -f backend/pom.xml -Dtest=PoliceMobileServiceTest test`

Expected: FAIL (class missing or methods not implemented)

- [ ] **Step 3: Implement list + getTask**

`PoliceMobileService.java`:

```java
package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.PoliceTaskDetail;
import com.example.backend.dto.PoliceTaskItem;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PoliceMobileService {

    @Autowired
    private DispatchOrderMapper dispatchOrderMapper;
    @Autowired
    private DispatchFieldRecordMapper fieldRecordMapper;
    @Autowired
    private DispatchMediaMapper mediaMapper;
    @Autowired
    private DispatchOrderEvaluationMapper evaluationMapper;

    public Map<String, Object> list(PageParams pp) {
        long total = dispatchOrderMapper.count(null, null, null, null);
        List<DispatchOrder> orders = dispatchOrderMapper.selectPage(
                null, null, null, null, pp.getOffset(), pp.getSize());
        Set<Long> rated = ratedIds(orders.stream().map(DispatchOrder::getId).collect(Collectors.toList()));
        List<PoliceTaskItem> items = orders.stream()
                .map(o -> toItem(o, rated.contains(o.getId())))
                .collect(Collectors.toList());
        return pp.toResult(items, total);
    }

    public PoliceTaskDetail getTask(Long id) {
        DispatchOrder order = dispatchOrderMapper.findById(id);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        PoliceTaskDetail detail = new PoliceTaskDetail();
        detail.setOrder(order);
        detail.setFieldRecord(fieldRecordMapper.findByOrderId(id));
        List<DispatchMedia> medias = mediaMapper.findByOrderId(id);
        detail.setMedias(medias == null ? List.of() : medias);
        detail.setEvaluation(evaluationMapper.findByOrderId(id));
        return detail;
    }

    private Set<Long> ratedIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> found = evaluationMapper.findOrderIds(ids);
        return found == null ? Collections.emptySet() : new HashSet<>(found);
    }

    private PoliceTaskItem toItem(DispatchOrder o, boolean rated) {
        PoliceTaskItem item = new PoliceTaskItem();
        item.setId(o.getId());
        item.setOrderNo(o.getOrderNo());
        item.setStatus(o.getStatus());
        item.setAccidentAddress(o.getAccidentAddress());
        item.setPlateNo(o.getPlateNo());
        item.setPartyName(o.getPartyName());
        item.setPartyPhone(o.getPartyPhone());
        item.setCreateTime(o.getCreateTime());
        item.setRated(rated);
        return item;
    }
}
```

Do not add `rate()` yet (Task 3).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -f backend/pom.xml -Dtest=PoliceMobileServiceTest test`

Expected: Tests run: 4, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/PoliceTaskItem.java backend/src/main/java/com/example/backend/dto/PoliceTaskDetail.java backend/src/main/java/com/example/backend/service/PoliceMobileService.java backend/src/test/java/com/example/backend/service/PoliceMobileServiceTest.java
git commit -m "feat: list and detail police dispatch tasks for mobile"
```

---

### Task 3: 提交评价 + Controller（TDD）

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/RateTaskRequest.java`
- Modify: `backend/src/main/java/com/example/backend/service/PoliceMobileService.java`
- Create: `backend/src/main/java/com/example/backend/controller/PoliceMobileController.java`
- Test: `backend/src/test/java/com/example/backend/service/PoliceMobileServiceTest.java`

**Interfaces:**
- Consumes: `getTask` from Task 2; `evaluationMapper.insert/findByOrderId`
- Produces: `PoliceMobileService.rate(Long userId, Long orderId, RateTaskRequest) -> DispatchOrderEvaluation`
- Produces: `GET /api/mobile/police/tasks`, `GET /api/mobile/police/tasks/{id}`, `POST /api/mobile/police/tasks/{id}/rate`

- [ ] **Step 1: Add failing rate tests**

`RateTaskRequest.java`:

```java
package com.example.backend.dto;

import lombok.Data;

@Data
public class RateTaskRequest {
    private Integer scorePunctual;
    private Integer scoreStandard;
    private Integer scoreSafety;
    private Integer scoreAttitude;
    private String comment;
}
```

Append to `PoliceMobileServiceTest`:

```java
    @Test
    void rateCompletedUnratedInsertsRow() {
        DispatchOrder order = new DispatchOrder();
        order.setId(3L);
        order.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(3L)).thenReturn(order);
        when(evaluationMapper.findByOrderId(3L)).thenReturn(null);
        when(evaluationMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrderEvaluation row = inv.getArgument(0);
            row.setId(99L);
            return 1;
        });
        RateTaskRequest req = scores(5, 4, 5, 5, "  还行  ");
        DispatchOrderEvaluation saved = service.rate(7L, 3L, req);
        assertEquals(99L, saved.getId());
        assertEquals(7L, saved.getRaterUserId());
        assertEquals(4, saved.getScoreStandard());
        assertEquals("还行", saved.getComment());
    }

    @Test
    void rateRejectsNonCompleted() {
        DispatchOrder order = new DispatchOrder();
        order.setId(3L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(3L)).thenReturn(order);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.rate(7L, 3L, scores(5, 5, 5, 5, null)));
        assertEquals("仅已完成工单可评价", ex.getMessage());
    }

    @Test
    void rateRejectsAlreadyRated() {
        DispatchOrder order = new DispatchOrder();
        order.setId(3L);
        order.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(3L)).thenReturn(order);
        when(evaluationMapper.findByOrderId(3L)).thenReturn(new DispatchOrderEvaluation());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.rate(7L, 3L, scores(5, 5, 5, 5, null)));
        assertEquals("该工单已评价", ex.getMessage());
    }

    @Test
    void rateRejectsOutOfRangeScore() {
        DispatchOrder order = new DispatchOrder();
        order.setId(3L);
        order.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(3L)).thenReturn(order);
        when(evaluationMapper.findByOrderId(3L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.rate(7L, 3L, scores(5, 0, 5, 5, null)));
        assertEquals("每个维度须为 1 至 5 星", ex.getMessage());
    }

    @Test
    void rateRejectsMissingOrder() {
        when(dispatchOrderMapper.findById(3L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.rate(7L, 3L, scores(5, 5, 5, 5, null)));
        assertEquals("工单不存在", ex.getMessage());
    }

    @Test
    void rateDuplicateKeyBecomesAlreadyRated() {
        DispatchOrder order = new DispatchOrder();
        order.setId(3L);
        order.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(3L)).thenReturn(order);
        when(evaluationMapper.findByOrderId(3L)).thenReturn(null);
        when(evaluationMapper.insert(any())).thenThrow(new DuplicateKeyException("dup"));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.rate(7L, 3L, scores(5, 5, 5, 5, null)));
        assertEquals("该工单已评价", ex.getMessage());
    }

    @Test
    void rateRejectsCommentOver500() {
        DispatchOrder order = new DispatchOrder();
        order.setId(3L);
        order.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(3L)).thenReturn(order);
        when(evaluationMapper.findByOrderId(3L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.rate(7L, 3L, scores(5, 5, 5, 5, "x".repeat(501))));
        assertEquals("反馈意见不能超过500字", ex.getMessage());
    }

    private RateTaskRequest scores(int p, int st, int sa, int at, String comment) {
        RateTaskRequest req = new RateTaskRequest();
        req.setScorePunctual(p);
        req.setScoreStandard(st);
        req.setScoreSafety(sa);
        req.setScoreAttitude(at);
        req.setComment(comment);
        return req;
    }
```

Add imports: `RateTaskRequest`, `DuplicateKeyException`, `any` from Mockito.

- [ ] **Step 2: Run tests — rate cases fail**

Run: `mvn -f backend/pom.xml -Dtest=PoliceMobileServiceTest test`

Expected: FAIL (`rate` missing)

- [ ] **Step 3: Implement `rate`**

Add imports `RateTaskRequest`, `DispatchOrderEvaluation`, `DuplicateKeyException`, and `Transactional` to the service class. Then add:

```java
    @Transactional
    public DispatchOrderEvaluation rate(Long userId, Long orderId, RateTaskRequest request) {
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("仅已完成工单可评价");
        }
        if (evaluationMapper.findByOrderId(orderId) != null) {
            throw new RuntimeException("该工单已评价");
        }
        if (request == null
                || !validScore(request.getScorePunctual())
                || !validScore(request.getScoreStandard())
                || !validScore(request.getScoreSafety())
                || !validScore(request.getScoreAttitude())) {
            throw new RuntimeException("每个维度须为 1 至 5 星");
        }
        String comment = request.getComment();
        if (comment != null) {
            comment = comment.trim();
            if (comment.isEmpty()) {
                comment = null;
            } else if (comment.length() > 500) {
                throw new RuntimeException("反馈意见不能超过500字");
            }
        }
        DispatchOrderEvaluation row = new DispatchOrderEvaluation();
        row.setDispatchOrderId(orderId);
        row.setRaterUserId(userId);
        row.setScorePunctual(request.getScorePunctual());
        row.setScoreStandard(request.getScoreStandard());
        row.setScoreSafety(request.getScoreSafety());
        row.setScoreAttitude(request.getScoreAttitude());
        row.setComment(comment);
        try {
            evaluationMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("该工单已评价");
        }
        return row;
    }

    private boolean validScore(Integer score) {
        return score != null && score >= 1 && score <= 5;
    }
```

- [ ] **Step 4: Controller**

```java
package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.common.Result;
import com.example.backend.dto.PoliceTaskDetail;
import com.example.backend.dto.RateTaskRequest;
import com.example.backend.entity.DispatchOrderEvaluation;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.PoliceMobileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mobile/police")
@CrossOrigin(origins = "*")
public class PoliceMobileController {

    @Autowired
    private PoliceMobileService policeMobileService;

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('accident:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageParams pp = PageParams.normalize(page, size);
        return Result.success(policeMobileService.list(pp));
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('accident:query')")
    public Result<PoliceTaskDetail> getTask(@PathVariable Long id) {
        try {
            return Result.success(policeMobileService.getTask(id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/rate")
    @PreAuthorize("hasAuthority('accident:rate')")
    public Result<DispatchOrderEvaluation> rate(@PathVariable Long id, @RequestBody RateTaskRequest request) {
        try {
            return Result.success(policeMobileService.rate(currentUserId(), id, request));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private Long currentUserId() {
        CustomUserDetails principal =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getId();
    }
}
```

- [ ] **Step 5: Run service tests + a broader compile test**

Run: `mvn -f backend/pom.xml -Dtest=PoliceMobileServiceTest,DispatchOrderServiceTest,RescuerMobileServiceTest,DetainedVehicleServiceTest test`

Expected: BUILD SUCCESS, all listed tests pass

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/RateTaskRequest.java backend/src/main/java/com/example/backend/service/PoliceMobileService.java backend/src/main/java/com/example/backend/controller/PoliceMobileController.java backend/src/test/java/com/example/backend/service/PoliceMobileServiceTest.java
git commit -m "feat: add police task rating API"
```

---

### Task 4: 工作台判定与守卫（TDD）

**Files:**
- Modify: `mobile-rescuer/src/utils/workspace.js`
- Modify: `mobile-rescuer/src/utils/workspace.test.cjs`
- Modify: `mobile-rescuer/src/utils/guard.js`

**Interfaces:**
- Consumes: existing `hasRescuerAccess` / `hasParkingAccess`
- Produces: `hasPoliceAccess(permissions)`; `resolveLoginTarget` returns `police` | existing values; `workspaceMatchesKind(kind, workspace)`; `canRatePoliceTask(status, rated)`; `shouldStartGps('police') === false`

- [ ] **Step 1: Extend failing tests in `workspace.test.cjs`**

Keep `const { httpErrorMessage } = require('./httpError.js')`. Replace the workspace.js destructure and add tests:

```javascript
const {
  hasRescuerAccess,
  hasParkingAccess,
  hasPoliceAccess,
  resolveLoginTarget,
  shouldStartGps,
  workspaceMatchesKind,
  canRatePoliceTask,
  matchHangtag,
  confirmInputValue,
  normalizeHangtagText
} = require('./workspace.js')
```

Replace `resolveLoginTarget` test body with:

```javascript
test('resolveLoginTarget', () => {
  assert.equal(resolveLoginTarget([]), 'none')
  assert.equal(resolveLoginTarget(['rescuer:task']), 'rescuer')
  assert.equal(resolveLoginTarget(['detain:query']), 'parking')
  assert.equal(resolveLoginTarget(['accident:query']), 'police')
  assert.equal(resolveLoginTarget(['accident:manage']), 'police')
  assert.equal(resolveLoginTarget(['rescuer:task', 'detain:query']), 'select')
  assert.equal(resolveLoginTarget(['rescuer:task', 'accident:query']), 'select')
  assert.equal(resolveLoginTarget(['detain:query', 'accident:rate']), 'select')
  assert.equal(resolveLoginTarget(['rescuer:task', 'detain:query', 'accident:query']), 'select')
})

test('police permission via module or prefix', () => {
  assert.equal(hasPoliceAccess(['accident:manage']), true)
  assert.equal(hasPoliceAccess(['accident:query']), true)
  assert.equal(hasPoliceAccess(['rescuer:task']), false)
})

test('gps only in rescuer workspace', () => {
  assert.equal(shouldStartGps('rescuer'), true)
  assert.equal(shouldStartGps('parking'), false)
  assert.equal(shouldStartGps('police'), false)
  assert.equal(shouldStartGps(''), false)
})

test('workspaceMatchesKind', () => {
  assert.equal(workspaceMatchesKind('police', 'police'), true)
  assert.equal(workspaceMatchesKind('police', 'rescuer'), false)
  assert.equal(workspaceMatchesKind('rescuer', 'police'), false)
  assert.equal(workspaceMatchesKind('shared', 'police'), true)
})

test('canRatePoliceTask', () => {
  assert.equal(canRatePoliceTask('COMPLETED', false), true)
  assert.equal(canRatePoliceTask('COMPLETED', true), false)
  assert.equal(canRatePoliceTask('ACCEPTED', false), false)
  assert.equal(canRatePoliceTask('PENDING', false), false)
})
```

Remove the old duplicate `gps only in rescuer workspace` test when replacing.

- [ ] **Step 2: Run tests — they fail**

Run: `cd mobile-rescuer && npm run test:workspace`

Expected: FAIL (`hasPoliceAccess` not exported / resolveLoginTarget still `none` for accident)

- [ ] **Step 3: Implement workspace.js**

```javascript
'use strict'

function hasRescuerAccess(permissions) {
  const list = permissions || []
  return list.some(
    (p) => p === 'mobile:rescuer' || (typeof p === 'string' && p.startsWith('rescuer:'))
  )
}

function hasParkingAccess(permissions) {
  return (permissions || []).includes('detain:query')
}

function hasPoliceAccess(permissions) {
  const list = permissions || []
  return list.some(
    (p) => p === 'accident:manage' || (typeof p === 'string' && p.startsWith('accident:'))
  )
}

function resolveLoginTarget(permissions) {
  const kinds = []
  if (hasRescuerAccess(permissions)) kinds.push('rescuer')
  if (hasParkingAccess(permissions)) kinds.push('parking')
  if (hasPoliceAccess(permissions)) kinds.push('police')
  if (kinds.length === 0) return 'none'
  if (kinds.length === 1) return kinds[0]
  return 'select'
}

function shouldStartGps(workspace) {
  return workspace === 'rescuer'
}

function workspaceMatchesKind(kind, workspace) {
  if (kind === 'rescuer' || kind === 'parking' || kind === 'police') {
    return workspace === kind
  }
  return true
}

function canRatePoliceTask(status, rated) {
  return status === 'COMPLETED' && !rated
}

function confirmInputValue(event, fallback) {
  if (event && event.detail && Object.prototype.hasOwnProperty.call(event.detail, 'value')) {
    return String(event.detail.value)
  }
  return fallback == null ? '' : String(fallback)
}

function normalizeHangtagText(payload) {
  return String(payload || '')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .normalize('NFKC')
    .trim()
}

function matchHangtag(list, payload) {
  const text = normalizeHangtagText(payload)
  if (!text) return { kind: 'empty' }
  if (/^RV:\d+$/.test(text)) return { kind: 'bind-qr' }
  const record = (list || []).find((row) => row && normalizeHangtagText(row.detainNo) === text)
  if (!record) return { kind: 'not-found' }
  if (record.status === 'IN_YARD') return { kind: 'in-yard', record }
  return { kind: 'not-in-yard', record }
}

module.exports = {
  hasRescuerAccess,
  hasParkingAccess,
  hasPoliceAccess,
  resolveLoginTarget,
  shouldStartGps,
  workspaceMatchesKind,
  canRatePoliceTask,
  matchHangtag,
  confirmInputValue,
  normalizeHangtagText
}
```

Keep hangtag helpers unchanged.

- [ ] **Step 4: Guard uses `workspaceMatchesKind`**

In `guard.js` replace the two kind checks with:

```javascript
import { resolveLoginTarget, workspaceMatchesKind } from './workspace.js'
```

Replace:

```javascript
  if (kind === 'rescuer' && state.workspace !== 'rescuer') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
  if (kind === 'parking' && state.workspace !== 'parking') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
```

with:

```javascript
  if (!workspaceMatchesKind(kind, state.workspace)) {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
```

- [ ] **Step 5: Run tests**

Run: `cd mobile-rescuer && npm run test:workspace`

Expected: all tests pass (including hangtag / httpError)

- [ ] **Step 6: Commit**

```bash
git add mobile-rescuer/src/utils/workspace.js mobile-rescuer/src/utils/workspace.test.cjs mobile-rescuer/src/utils/guard.js
git commit -m "feat: route accident permissions into police workspace"
```

---

### Task 5: 交警任务列表与登录分流 UI

**Files:**
- Create: `mobile-rescuer/src/api/police.js`
- Create: `mobile-rescuer/src/components/PoliceTaskList.vue`
- Modify: `mobile-rescuer/src/pages/workbench/index.vue`
- Modify: `mobile-rescuer/src/pages/workspace/select.vue`
- Modify: `mobile-rescuer/src/pages/login/index.vue`
- Modify: `mobile-rescuer/src/pages.json`

**Interfaces:**
- Consumes: `GET /mobile/police/tasks`; `canRatePoliceTask`; `hasPoliceAccess`
- Produces: workbench renders police list; select shows 交警任务; login hint includes trafficpolice

- [ ] **Step 1: API client**

`mobile-rescuer/src/api/police.js`:

```javascript
import { request } from '../utils/request'

const PREFIX = '/mobile/police'

export function listPoliceTasks(params) {
  return request({ url: `${PREFIX}/tasks`, data: params })
}

export function getPoliceTask(id) {
  return request({ url: `${PREFIX}/tasks/${id}` })
}

export function ratePoliceTask(id, data) {
  return request({ url: `${PREFIX}/tasks/${id}/rate`, method: 'POST', data })
}
```

- [ ] **Step 2: `PoliceTaskList.vue`**

```vue
<template>
  <view>
    <view v-if="loading && !list.length" class="muted center">加载中…</view>
    <view v-else-if="loadFailed && !list.length" class="muted center">加载失败</view>
    <view v-else-if="!list.length" class="muted center">暂无任务</view>
    <view v-else>
      <view class="card item" v-for="item in list" :key="item.id" @click="goDetail(item.id)">
        <view class="row-between">
          <text class="order-no">{{ item.orderNo || ('#' + item.id) }}</text>
          <text class="status">{{ statusText(item.status) }}</text>
        </view>
        <view class="addr">{{ item.accidentAddress || '未填写地址' }}</view>
        <view class="line">事故车牌：{{ item.plateNo || '-' }}</view>
        <view class="line">事故联系人：{{ item.partyName || '-' }}</view>
        <view v-if="canRatePoliceTask(item.status, item.rated)" class="btn-ghost rate-btn" @click.stop="goRate(item.id)">评价</view>
        <view v-else-if="item.status === 'COMPLETED' && item.rated" class="muted">已评价</view>
      </view>
      <view class="muted load-tip">
        {{ loading ? '加载中…' : (list.length >= total ? '没有更多了' : '上滑加载更多') }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { listPoliceTasks } from '../api/police'
import { canRatePoliceTask } from '../utils/workspace.js'

const PAGE_SIZE = 10
const page = ref(1)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const loadFailed = ref(false)

function statusText(s) {
  const map = {
    PENDING: '待派单',
    DISPATCHED: '已派单',
    ACCEPTED: '已接单',
    COMPLETED: '已完成',
    ABORTED: '已中止'
  }
  return map[s] || s || '-'
}

async function load(append = false) {
  loading.value = true
  try {
    const res = await listPoliceTasks({ page: page.value, size: PAGE_SIZE })
    const rows = res.data?.list || []
    total.value = res.data?.total ?? 0
    list.value = append ? list.value.concat(rows) : rows
    loadFailed.value = false
  } catch (_) {
    if (append) {
      page.value = Math.max(1, page.value - 1)
      uni.showToast({ title: '加载失败', icon: 'none' })
    } else {
      list.value = []
      total.value = 0
      loadFailed.value = true
    }
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  return load(false)
}

async function loadMore() {
  if (loading.value || list.value.length >= total.value) return
  page.value += 1
  await load(true)
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/police/detail?id=${id}` })
}

function goRate(id) {
  uni.navigateTo({ url: `/pages/police/rate?id=${id}` })
}

defineExpose({ reload, loadMore })
</script>

<style scoped>
.item .order-no {
  font-weight: 600;
  font-size: 30rpx;
}
.status {
  color: #2979ff;
  font-size: 24rpx;
}
.addr {
  margin: 16rpx 0 8rpx;
  font-size: 28rpx;
}
.line {
  font-size: 26rpx;
  color: #444;
  margin-top: 8rpx;
}
.rate-btn {
  margin-top: 16rpx;
}
.load-tip {
  text-align: center;
  padding: 16rpx 0 8rpx;
}
</style>
```

- [ ] **Step 3: Workbench + select + login + pages.json**

`workbench/index.vue` template:

```vue
  <view class="page">
    <TaskList v-if="workspace === 'rescuer'" ref="taskRef" />
    <DetainList v-if="workspace === 'parking'" ref="detainRef" />
    <PoliceTaskList v-if="workspace === 'police'" ref="policeRef" />
  </view>
```

Import `PoliceTaskList`, add `policeRef`. In `onShow` `nextTick` also `policeRef.value?.reload()`. GPS: keep start only for rescuer; `if (state.workspace !== 'rescuer') stopLocationReporter()`.

`onReachBottom`:

```javascript
onReachBottom(() => {
  if (workspace.value === 'parking') detainRef.value?.loadMore?.()
  if (workspace.value === 'police') policeRef.value?.loadMore?.()
})
```

`select.vue`: import `hasPoliceAccess`, `showPolice` ref, template card:

```vue
    <view v-if="showPolice" class="card choice" @click="choose('police')">交警任务</view>
```

`onShow`: `showPolice.value = hasPoliceAccess(permissions)`

`login/index.vue` hint:

```vue
      <view class="hint muted">towdriver / parkingadmin / trafficpolice / admin123</view>
```

`pages.json` register before `pages/detain/checkin`:

```json
    {
      "path": "pages/police/detail",
      "style": { "navigationBarTitleText": "任务详情" }
    },
    {
      "path": "pages/police/rate",
      "style": { "navigationBarTitleText": "任务评价" }
    },
```

Stub the two pages if Task 6/7 have not run — **do not stub**. Implement empty pages that only call `requirePageAccess('police')` so `pages.json` does not 404:

`pages/police/detail.vue` (temporary, replaced in Task 6):

```vue
<template><view class="page muted">加载中…</view></template>
<script setup>
import { onShow } from '@dcloudio/uni-app'
import { requirePageAccess } from '../../utils/guard.js'
onShow(() => { requirePageAccess('police') })
</script>
```

Same for `rate.vue`. Prefer implementing full pages in Tasks 6–7 immediately after this commit.

- [ ] **Step 4: Run workspace tests (no UI runner)**

Run: `cd mobile-rescuer && npm run test:workspace`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add mobile-rescuer/src/api/police.js mobile-rescuer/src/components/PoliceTaskList.vue mobile-rescuer/src/pages/workbench/index.vue mobile-rescuer/src/pages/workspace/select.vue mobile-rescuer/src/pages/login/index.vue mobile-rescuer/src/pages.json mobile-rescuer/src/pages/police/detail.vue mobile-rescuer/src/pages/police/rate.vue
git commit -m "feat: add police workbench task list and login routing"
```

If Task 6 follows in the same session, skip stub pages and implement full detail/rate before this commit, then skip Task 6 Step 5 duplicate commit.

---

### Task 6: 交警任务详情

**Files:**
- Modify: `mobile-rescuer/src/pages/police/detail.vue`

**Interfaces:**
- Consumes: `getPoliceTask(id)`; `mediaUrl`; `loadAmap` / `hasAmapKey`; `canRatePoliceTask(order.status, !!evaluation)`
- Produces: read-only detail with times, photos, static accident map, 评价 button when eligible

- [ ] **Step 1: Implement `pages/police/detail.vue`**

```vue
<template>
  <view class="page" v-if="order">
    <view class="card">
      <view class="row-between">
        <text class="title">{{ order.orderNo || ('#' + order.id) }}</text>
        <text class="status">{{ statusText(order.status) }}</text>
      </view>
      <view class="line">事故地址：{{ order.accidentAddress || '-' }}</view>
      <view class="line">救援事由：{{ order.rescueReason || '-' }}</view>
      <view class="line">事故车牌：{{ order.plateNo || '-' }}</view>
      <view class="line">事故联系人：{{ order.partyName || '-' }}</view>
      <view class="line">联系方式：{{ order.partyPhone || '-' }}</view>
      <view class="line">派单时间：{{ formatTime(order.dispatchedAt) }}</view>
      <view class="line">接单时间：{{ formatTime(order.acceptedAt) }}</view>
      <view class="line" v-if="order.checkedInAt">
        签到（{{ order.checkinMode || '-' }}）：{{ formatTime(order.checkedInAt) }}
      </view>
      <view class="line">完成时间：{{ formatTime(order.completedAt) }}</view>
      <view class="line" v-if="order.rejectReason">退单原因：{{ order.rejectReason }}</view>
      <view class="line" v-if="order.abortReason">中止原因：{{ order.abortReason }}</view>
    </view>

    <view class="card map-card">
      <view class="section-title">位置</view>
      <div v-if="canShowMap" id="police-detail-map" class="map-box"></div>
      <view v-else class="map-placeholder">
        <text v-if="!hasAccidentCoords">暂无事故坐标，无法展示地图</text>
        <text v-else>未配置地图 Key</text>
      </view>
      <view v-if="mapError" class="line error">{{ mapError }}</view>
    </view>

    <view class="card" v-if="fieldRecord">
      <view class="section-title">现场摘要</view>
      <view class="line">车牌：{{ fieldRecord.plateNo || '-' }}</view>
      <view class="line">车型：{{ fieldRecord.vehicleType || '-' }}</view>
      <view class="line">受损：{{ fieldRecord.damageDesc || '-' }}</view>
      <view class="line">停放：{{ fieldRecord.parkAddress || '-' }}</view>
      <view v-if="damageMedias.length || parkMedias.length" class="media-block">
        <view v-if="damageMedias.length" class="media-section">
          <view class="section-title media-title">受损照片</view>
          <view class="media-grid">
            <image
              v-for="m in damageMedias"
              :key="m.id"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewMedias(damageMedias, m.filePath)"
            />
          </view>
        </view>
        <view v-if="parkMedias.length" class="media-section">
          <view class="section-title media-title">停放照片</view>
          <view class="media-grid">
            <image
              v-for="m in parkMedias"
              :key="m.id"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewMedias(parkMedias, m.filePath)"
            />
          </view>
        </view>
      </view>
    </view>

    <view class="card" v-if="evaluation">
      <view class="section-title">评价</view>
      <view class="line">到达及时：{{ evaluation.scorePunctual }} 星</view>
      <view class="line">处置规范：{{ evaluation.scoreStandard }} 星</view>
      <view class="line">操作安全：{{ evaluation.scoreSafety }} 星</view>
      <view class="line">服务态度：{{ evaluation.scoreAttitude }} 星</view>
      <view class="line">意见：{{ evaluation.comment || '无' }}</view>
    </view>

    <view class="actions" v-if="canRate">
      <view class="btn-primary" @click="goRate">评价</view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { getPoliceTask } from '../../api/police'
import { requirePageAccess } from '../../utils/guard.js'
import { canRatePoliceTask } from '../../utils/workspace.js'
import { hasAmapKey, loadAmap } from '../../utils/amap.js'
import { mediaUrl } from '../../utils/request.js'

const id = ref('')
const order = ref(null)
const fieldRecord = ref(null)
const evaluation = ref(null)
const damageMedias = ref([])
const parkMedias = ref([])
const mapError = ref('')
const amapReady = hasAmapKey()
let mapInstance = null
let accidentMarker = null

const hasAccidentCoords = computed(
  () => order.value?.longitude != null && order.value?.latitude != null
)
const canShowMap = computed(() => amapReady && hasAccidentCoords.value)
const canRate = computed(() => canRatePoliceTask(order.value?.status, !!evaluation.value))

function statusText(s) {
  const map = {
    PENDING: '待派单',
    DISPATCHED: '已派单',
    ACCEPTED: '已接单',
    COMPLETED: '已完成',
    ABORTED: '已中止'
  }
  return map[s] || s || '-'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function previewMedias(list, filePath) {
  const urls = list.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: mediaUrl(filePath) })
}

function destroyMap() {
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
  accidentMarker = null
}

async function ensureMap() {
  mapError.value = ''
  if (!canShowMap.value) {
    destroyMap()
    return
  }
  await nextTick()
  const el = typeof document !== 'undefined' ? document.getElementById('police-detail-map') : null
  if (!el) return
  try {
    const AMap = await loadAmap()
    const lng = Number(order.value.longitude)
    const lat = Number(order.value.latitude)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
      mapError.value = '事故坐标无效'
      return
    }
    destroyMap()
    mapInstance = new AMap.Map(el, { zoom: 14, center: [lng, lat], resizeEnable: true })
    accidentMarker = new AMap.Marker({ position: [lng, lat] })
    mapInstance.add([accidentMarker])
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
  }
}

async function load() {
  if (!id.value) return
  try {
    const res = await getPoliceTask(id.value)
    const data = res.data || {}
    order.value = data.order || null
    fieldRecord.value = data.fieldRecord || null
    evaluation.value = data.evaluation || null
    const medias = data.medias || []
    damageMedias.value = medias.filter((m) => m.bizType === 'DAMAGE')
    parkMedias.value = medias.filter((m) => m.bizType === 'PARK')
    await ensureMap()
  } catch (e) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}

function goRate() {
  uni.navigateTo({ url: `/pages/police/rate?id=${id.value}` })
}

onLoad((q) => {
  id.value = q.id
})

onShow(() => {
  if (!requirePageAccess('police')) return
  load()
})

onUnload(() => {
  destroyMap()
})
</script>

<style scoped>
.title { font-weight: 700; font-size: 32rpx; }
.status { color: #2979ff; }
.line { margin-top: 12rpx; font-size: 26rpx; }
.section-title { font-weight: 600; margin-bottom: 12rpx; }
.map-box { height: 360rpx; width: 100%; }
.map-placeholder { padding: 24rpx 0; color: #888; }
.media-grid { display: flex; flex-wrap: wrap; gap: 12rpx; }
.thumb { width: 160rpx; height: 160rpx; border-radius: 8rpx; }
.actions { margin-top: 24rpx; }
.error { color: #c62828; }
</style>
```

No 500m circle, no vehicle marker, no polling.

- [ ] **Step 2: Manual check against spec (no automated UI test)**

Confirm: no 接单/签到/完成 buttons in the template.

- [ ] **Step 3: Commit**

```bash
git add mobile-rescuer/src/pages/police/detail.vue
git commit -m "feat: add read-only police task detail with photos and map"
```

---

### Task 7: 评价页 + README

**Files:**
- Modify: `mobile-rescuer/src/pages/police/rate.vue`
- Modify: `mobile-rescuer/README.md`

**Interfaces:**
- Consumes: `getPoliceTask`, `ratePoliceTask`, `canRatePoliceTask`
- Produces: four default-5 star rows; confirm submit; `redirectTo` detail on success

- [ ] **Step 1: Implement `pages/police/rate.vue`**

```vue
<template>
  <view class="page" v-if="ready">
    <view class="card">
      <view class="dim" v-for="d in dimensions" :key="d.key">
        <view class="label">{{ d.label }}</view>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="n"
            class="star"
            :class="{ on: scores[d.key] >= n }"
            @click="scores[d.key] = n"
          >★</text>
        </view>
      </view>
      <view class="field">
        <view class="field-label">反馈意见</view>
        <textarea class="field-input area" v-model="comment" placeholder="选填" maxlength="500" />
      </view>
    </view>
    <view class="btn-primary" :class="{ 'btn-disabled': submitting }" @click="onSubmit">提交评价</view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getPoliceTask, ratePoliceTask } from '../../api/police'
import { requirePageAccess } from '../../utils/guard.js'
import { canRatePoliceTask } from '../../utils/workspace.js'

const dimensions = [
  { key: 'punctual', label: '到达现场是否及时' },
  { key: 'standard', label: '处置现场是否规范合理' },
  { key: 'safety', label: '操作过程是否确保安全' },
  { key: 'attitude', label: '服务态度是否热情耐心' }
]

const id = ref('')
const ready = ref(false)
const submitting = ref(false)
const comment = ref('')
const scores = reactive({
  punctual: 5,
  standard: 5,
  safety: 5,
  attitude: 5
})

function bounce(title) {
  uni.showToast({ title, icon: 'none' })
  setTimeout(() => uni.navigateBack(), 400)
}

async function load() {
  if (!requirePageAccess('police')) return
  if (!id.value) return
  try {
    const res = await getPoliceTask(id.value)
    const data = res.data || {}
    const order = data.order || {}
    if (!canRatePoliceTask(order.status, !!data.evaluation)) {
      bounce(data.evaluation ? '该工单已评价' : '仅已完成工单可评价')
      return
    }
    scores.punctual = 5
    scores.standard = 5
    scores.safety = 5
    scores.attitude = 5
    comment.value = ''
    ready.value = true
  } catch (e) {
    bounce(e.message || '加载失败')
  }
}

function onSubmit() {
  if (submitting.value) return
  uni.showModal({
    title: '提交评价？',
    success: async (r) => {
      if (!r.confirm) return
      submitting.value = true
      try {
        await ratePoliceTask(id.value, {
          scorePunctual: scores.punctual,
          scoreStandard: scores.standard,
          scoreSafety: scores.safety,
          scoreAttitude: scores.attitude,
          comment: comment.value
        })
        uni.showToast({ title: '评价成功', icon: 'none' })
        uni.redirectTo({ url: `/pages/police/detail?id=${id.value}` })
      } catch (e) {
        uni.showToast({ title: e.message || '提交失败', icon: 'none' })
      } finally {
        submitting.value = false
      }
    }
  })
}

onLoad((q) => {
  id.value = q.id
})

onShow(() => {
  load()
})
</script>

<style scoped>
.dim { margin-bottom: 28rpx; }
.label { font-size: 28rpx; margin-bottom: 8rpx; }
.stars { display: flex; gap: 16rpx; font-size: 44rpx; color: #ccc; }
.star.on { color: #f5a623; }
.area { min-height: 160rpx; }
.btn-primary { margin-top: 24rpx; }
</style>
```

- [ ] **Step 2: Update README**

Replace the login-permission sentence and demo table:

```markdown
登录后按权限进入工作台（无 `rescuer:*`/`mobile:rescuer`、无 `detain:query`、且无 `accident:*`/`accident:manage` 则提示「无移动端权限」）：

| 用户名 | 密码 | 权限 | 登录后 |
|--------|------|------|--------|
| towdriver | admin123 | 仅施救 | 直接进入施救任务工作台 |
| parkingadmin | admin123 | 仅扣车（`detain:query`） | 直接进入停车场在库列表 |
| trafficpolice | admin123 | 仅交警（`accident:*`） | 直接进入交警任务列表 |
| admin | admin123 | 施救+扣车+交警 | 先到「选择工作台」页，点选后进入 |
```

Add section:

```markdown
**交警工作台**（`workspace=police`）

- 全部任务列表（分页）；已完成未评可评价
- 只读详情（过程时间、现场/入库照片、事故点地图）
- 四维 5 星评价（默认好评，每单一次不可改）
- 我的：资料编辑；无绑车、不启动 GPS
```

Login hint line: `towdriver / parkingadmin / trafficpolice / admin123`.

- [ ] **Step 3: Run all automated tests**

Run:

```bash
mvn -f backend/pom.xml -Dtest=PoliceMobileServiceTest,DispatchOrderServiceTest,RescuerMobileServiceTest,DetainedVehicleServiceTest test
cd mobile-rescuer && npm run test:workspace
```

Expected: both PASS

- [ ] **Step 4: Commit**

```bash
git add mobile-rescuer/src/pages/police/rate.vue mobile-rescuer/README.md
git commit -m "feat: add police task rating page and demo account docs"
```

---

## Self-review vs spec

| Spec item | Task |
|-----------|------|
| Shared `mobile-rescuer`, `workspace=police` | 4, 5 |
| hasPolice / select / single skip | 4, 5 |
| No GPS / no bind | 4 (gps), 5 (mine already hides bind unless rescuer) |
| `/api/mobile/police` three endpoints, no `dispatch:*` | 1, 2, 3 |
| Evaluation table unique | 1, 3 |
| List all tasks paginated + rated + 评价 button | 2, 5 |
| Detail times + photos + static map | 6 |
| Rate COMPLETED only, default 5 UI, immutable | 3, 7 |
| `trafficpolice` + ADMIN accident perms | 1, 7 README |
| Guard bounce wrong workspace | 4 |
| No district filter / no PC stats | (omitted) |

## Manual smoke (after migrate)

1. Apply `database/migrate_2026-09-11_police_mobile.sql` (or rebuild from `init.sql`).
2. `trafficpolice` / `admin123` → 全部任务列表, 我的无绑车.
3. Open non-completed order → no 评价.
4. Complete an order on PC if needed → 评价 default 5 stars, lower one, submit → detail read-only.
5. `towdriver` / `parkingadmin` unchanged; `admin` sees three choices.
