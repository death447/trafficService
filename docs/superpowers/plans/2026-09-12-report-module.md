# 运营报表模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 PC 端落地独立「报表」页：按自定义日期汇总调度运营、服务质量、停车场扣留（卡片 + 图表 + 表格），并导出 Excel。

**Architecture:** 新增 `ReportMapper` 做时间窗聚合，`ReportService` 校验日期、补全日趋势/五态、四舍五入均分并写 POI；`ReportController` 提供 `GET /api/report/summary` 与 `/export`。前端 `/reports` 一页三段，ECharts 画趋势与分布。权限 `report:manage` / `report:query` 只授管理员与调度员。

**Tech Stack:** Spring Boot 3.2、MyBatis 注解 SQL、JUnit5+Mockito、Apache POI 5.2.5、Vue 3、Axios、ECharts、原生 `input[type=date]`

**Spec:** `docs/superpowers/specs/2026-09-12-report-module-design.md`

## Global Constraints

- 时区 `Asia/Shanghai`；SQL 时间窗 `[from 00:00, to+1day 00:00)`
- 跨度含起止日，上限 366 天
- 调度全集：`dispatch_order.create_time` 落在时间窗
- 处置中：`PENDING` / `DISPATCHED` / `ACCEPTED`；已完成 `COMPLETED`；中止 `ABORTED`
- 评价只统计全集中已有 `dispatch_order_evaluation` 的工单；均分 1 位小数，无评价 JSON `null`
- 扣留：入库 `in_time`、出库 `out_time`、在场 `in_time` 在窗且 `status=IN_YARD`
- 校验失败：Service 抛 `RuntimeException`（中文文案见下），Controller `Result.error(400, message)`
- 校验文案精确：`请选择开始和结束日期` / `开始日期不能晚于结束日期` / `查询区间不能超过 366 天`
- 权限 id `70`=`report:manage`，`71`=`report:query`；角色 `DISPATCHER=2`、`ADMIN=5`
- Mapper 用 `@Select` 注解（本仓库无 XML mapper 文件）
- 不改首页概览；不做移动端；不引入 Element Plus / vue-echarts
- Windows 跑测试：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'`，Maven 用 `d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd`

## File Structure

### Create
- `database/migrate_2026-09-12_report.sql`
- `backend/src/main/java/com/example/backend/dto/ReportSummary.java`
- `backend/src/main/java/com/example/backend/dto/ReportDateCount.java`
- `backend/src/main/java/com/example/backend/dto/ReportStatusCount.java`
- `backend/src/main/java/com/example/backend/dto/ReportOrderRow.java`
- `backend/src/main/java/com/example/backend/dto/ReportQualityAvg.java`
- `backend/src/main/java/com/example/backend/dto/ReportRescuerQuality.java`
- `backend/src/main/java/com/example/backend/dto/ReportLotDetain.java`
- `backend/src/main/java/com/example/backend/mapper/ReportMapper.java`
- `backend/src/main/java/com/example/backend/service/ReportService.java`
- `backend/src/main/java/com/example/backend/controller/ReportController.java`
- `backend/src/test/java/com/example/backend/service/ReportServiceTest.java`
- `frontend/src/api/report.js`
- `frontend/src/views/report/Report.vue`

### Modify
- `database/init.sql`（permission 70/71 与角色授权）
- `backend/pom.xml`（`poi-ooxml` 5.2.5）
- `frontend/src/utils/request.js`（`responseType: 'blob'` 旁路 JSON code 判断）
- `frontend/src/router/index.js`
- `frontend/src/App.vue`
- `frontend/package.json`（`echarts`，实现时 `npm install echarts`）

---

### Task 1: 报表权限种子

**Files:**
- Modify: `database/init.sql`
- Create: `database/migrate_2026-09-12_report.sql`

**Interfaces:**
- Produces: permission id 70 `report:manage`、71 `report:query`；`role_permission` 授予 role 2 与 5

- [ ] **Step 1: 在 `init.sql` 的 permission INSERT 末尾（id 69 那一行后、分号前）追加**

把现有：

```sql
(69, '事故评价', 'accident:rate', 'BUTTON', 17, 2);
```

改为：

```sql
(69, '事故评价', 'accident:rate', 'BUTTON', 17, 2),
(70, '报表管理', 'report:manage', 'MODULE', 0, 14),
(71, '报表查询', 'report:query', 'BUTTON', 70, 1);
```

- [ ] **Step 2: 改 ADMIN 授权，把 `OR id IN (68, 69)` 扩成包含 70、71**

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id IN (16, 17, 19)
   OR id BETWEEN 20 AND 66
   OR id IN (68, 69, 70, 71);
```

- [ ] **Step 3: 改 DISPATCHER 授权，在现有 SELECT 条件后加 `OR id IN (70, 71)`**

```sql
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission` WHERE id = 2 OR id = 16 OR id BETWEEN 20 AND 41
   OR id IN (70, 71);
```

- [ ] **Step 4: 新建 migrate 脚本（已有库只跑这个）**

`database/migrate_2026-09-12_report.sql` 全文：

```sql
INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 70 AS id, '报表管理' AS permission_name, 'report:manage' AS permission_code,
         'MODULE' AS permission_type, 0 AS parent_id, 14 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 70 OR `permission_code` = 'report:manage');

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 71 AS id, '报表查询' AS permission_name, 'report:query' AS permission_code,
         'BUTTON' AS permission_type, 70 AS parent_id, 1 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 71 OR `permission_code` = 'report:query');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, 70 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 2 AND `permission_id` = 70
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, 71 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 2 AND `permission_id` = 71
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 70 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 70
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 71 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 71
);
```

- [ ] **Step 5: 对现有库执行 migrate**

在 `backend` 或仓库根目录用本机 MySQL 客户端执行该 SQL（库名 `vue_springboot_system`）。然后：

```sql
SELECT id, permission_code FROM permission WHERE id IN (70, 71);
SELECT role_id, permission_id FROM role_permission WHERE permission_id IN (70, 71);
```

Expected: 两行权限；role_id 2 与 5 各两行。

- [ ] **Step 6: Commit**

```bash
git add database/init.sql database/migrate_2026-09-12_report.sql
git commit -m "feat: add report:manage and report:query permissions"
```

---

### Task 2: 日期校验（TDD）

**Files:**
- Create: 全部 `dto/Report*.java`（可先空壳字段，Task 3 填满）
- Create: `mapper/ReportMapper.java`（空接口 + `@Mapper`）
- Create: `service/ReportService.java`
- Test: `backend/src/test/java/com/example/backend/service/ReportServiceTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `ReportService.summary(String from, String to): ReportSummary`
  - `ReportService.export(String from, String to): byte[]`（本任务可先 `throw new UnsupportedOperationException()`）
  - 非法日期抛 `RuntimeException`，message 必须与 Global Constraints 三句完全一致
  - 时间窗：`start = from.atStartOfDay()`，`endExclusive = to.plusDays(1).atStartOfDay()`，`ZoneId.of("Asia/Shanghai")` 只用于「今天」的默认，解析 `yyyy-MM-dd` 用 `LocalDate.parse`

- [ ] **Step 1: 写失败测试（尚无实现）**

`ReportServiceTest.java`：

```java
package com.example.backend.service;

import com.example.backend.mapper.ReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportMapper reportMapper;
    @InjectMocks ReportService service;

    @Test
    void summaryRejectsBlankDates() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> service.summary(null, "2026-09-01"));
        assertEquals("请选择开始和结束日期", e.getMessage());
        e = assertThrows(RuntimeException.class, () -> service.summary("2026-09-01", " "));
        assertEquals("请选择开始和结束日期", e.getMessage());
        e = assertThrows(RuntimeException.class, () -> service.summary("2026-13-01", "2026-09-01"));
        assertEquals("请选择开始和结束日期", e.getMessage());
    }

    @Test
    void summaryRejectsFromAfterTo() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.summary("2026-09-12", "2026-09-01"));
        assertEquals("开始日期不能晚于结束日期", e.getMessage());
    }

    @Test
    void summaryRejectsSpanOver366Days() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.summary("2025-09-01", "2026-09-02"));
        assertEquals("查询区间不能超过 366 天", e.getMessage());
    }
}
```

含起止日：`2025-09-01`～`2026-09-01` 为 366 天合法（本任务不测）；`2025-09-01`～`2026-09-02` 为 367 天必须拒绝。

- [ ] **Step 2: 跑测试确认失败**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd -q -Dtest=ReportServiceTest test
```

Working directory: `d:\cursorworkspace\backend`

Expected: FAIL（`ReportService` 不存在或方法不存在）

- [ ] **Step 3: 最小实现校验 + 空 Mapper + DTO 外壳**

`ReportMapper.java`：

```java
package com.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportMapper {
}
```

`ReportSummary.java` 先：

```java
package com.example.backend.dto;

import lombok.Data;

@Data
public class ReportSummary {
    private String from;
    private String to;
}
```

`ReportService.java` 校验逻辑：

```java
package com.example.backend.service;

import com.example.backend.dto.ReportSummary;
import com.example.backend.mapper.ReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Service
public class ReportService {

    @Autowired
    private ReportMapper reportMapper;

    public ReportSummary summary(String from, String to) {
        LocalDate[] range = parseRange(from, to);
        ReportSummary out = new ReportSummary();
        out.setFrom(range[0].toString());
        out.setTo(range[1].toString());
        return out;
    }

    public byte[] export(String from, String to) {
        throw new UnsupportedOperationException("export");
    }

    LocalDate[] parseRange(String from, String to) {
        LocalDate start;
        LocalDate end;
        try {
            if (from == null || to == null || from.isBlank() || to.isBlank()) {
                throw new DateTimeParseException("blank", "", 0);
            }
            start = LocalDate.parse(from.trim());
            end = LocalDate.parse(to.trim());
        } catch (DateTimeParseException e) {
            throw new RuntimeException("请选择开始和结束日期");
        }
        if (start.isAfter(end)) {
            throw new RuntimeException("开始日期不能晚于结束日期");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (inclusiveDays > 366) {
            throw new RuntimeException("查询区间不能超过 366 天");
        }
        return new LocalDate[] { start, end };
    }
}
```

- [ ] **Step 4: 再跑 `ReportServiceTest`**

Expected: PASS（三个校验测试）

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/ReportSummary.java \
  backend/src/main/java/com/example/backend/mapper/ReportMapper.java \
  backend/src/main/java/com/example/backend/service/ReportService.java \
  backend/src/test/java/com/example/backend/service/ReportServiceTest.java
git commit -m "feat: validate report date range"
```

---

### Task 3: 汇总组装（TDD）

**Files:**
- Create/fill: 其余 Report DTO
- Modify: `ReportMapper.java`、`ReportService.java`、`ReportServiceTest.java`

**Interfaces:**
- Consumes: `parseRange`；Mapper 下列方法（参数均为 `@Param("start") LocalDateTime start, @Param("end") LocalDateTime end`，`end` 为开区间上界）
- Produces: 完整 `ReportSummary`，结构与 spec JSON 一致

Mapper 方法签名（必须按此命名，Task 4/5 依赖）：

```java
long countOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
List<ReportStatusCount> countOrdersByStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
List<ReportDateCount> countOrdersByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
List<ReportOrderRow> listOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
ReportQualityAvg selectQualityAvg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
List<ReportRescuerQuality> listQualityByRescuer(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
long countDetainInbound(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
long countDetainInYard(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
long countDetainOutbound(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
List<ReportLotDetain> listDetainByLot(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
```

DTO 字段（Lombok `@Data`）：

`ReportDateCount`: `String date`, `long count`  
`ReportStatusCount`: `String status`, `long count`  
`ReportOrderRow`: `Long id`, `String orderNo`, `String accidentAddress`, `String status`, `String plateNo`, `String rescuerName`, `LocalDateTime createTime`, `LocalDateTime dispatchedAt`, `LocalDateTime completedAt`  
`ReportQualityAvg`: `long ratedCount`, `Double avgPunctual`, `Double avgStandard`, `Double avgSafety`, `Double avgAttitude`  
`ReportRescuerQuality`: `Long rescuerId`, `String rescuerName`, `long ratedCount`, `Double avgPunctual`, `Double avgStandard`, `Double avgSafety`, `Double avgAttitude`, `Double avgOverall`  
`ReportLotDetain`: `Long parkingLotId`, `String parkingLotName`, `long inbound`, `long inYard`, `long outbound`

`ReportSummary` 增加：

```java
private DispatchBlock dispatch = new DispatchBlock();
private QualityBlock quality = new QualityBlock();
private DetainBlock detain = new DetainBlock();

@Data
public static class DispatchBlock {
    private long total;
    private long completed;
    private long inProgress;
    private long aborted;
    private List<ReportDateCount> trend = new ArrayList<>();
    private List<ReportStatusCount> statusDist = new ArrayList<>();
    private List<ReportOrderRow> orders = new ArrayList<>();
}
@Data
public static class QualityBlock {
    private long ratedCount;
    private Double avgPunctual;
    private Double avgStandard;
    private Double avgSafety;
    private Double avgAttitude;
    private List<ReportRescuerQuality> byRescuer = new ArrayList<>();
}
@Data
public static class DetainBlock {
    private long inbound;
    private long inYard;
    private long outbound;
    private List<ReportLotDetain> byLot = new ArrayList<>();
}
```

Service 规则：

- `startDt = from.atStartOfDay()`, `endDt = to.plusDays(1).atStartOfDay()`
- `total = countOrders`；从 `countOrdersByStatus` 填 completed/inProgress/aborted（inProgress = PENDING+DISPATCHED+ACCEPTED）
- `statusDist` 固定顺序五态，缺的 count=0
- `trend`：从 `from` 到 `to` 每一天；用 mapper 的 `date`（`yyyy-MM-dd`）对齐，缺日 0
- 均分：`round1(Double)` → `BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue()`；`ratedCount==0` 或 avg 为 null 则保持 null
- `avgOverall` = 该行四个 round1 后再平均再 round1；任一维 null 则 overall null
- `rescuerName` 空则设为 `未指定`

- [ ] **Step 1: 追加失败测试**

在 `ReportServiceTest` 增加（保留 Task 2 三个测试）：

```java
import com.example.backend.dto.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Test
void summaryFillsCountsTrendStatusAndRoundsScores() {
    LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
    LocalDateTime end = LocalDateTime.of(2026, 9, 3, 0, 0);
    when(reportMapper.countOrders(start, end)).thenReturn(4L);
    when(reportMapper.countOrdersByStatus(start, end)).thenReturn(List.of(
            status("COMPLETED", 1),
            status("PENDING", 1),
            status("ABORTED", 1),
            status("ACCEPTED", 1)
    ));
    ReportDateCount day = new ReportDateCount();
    day.setDate("2026-09-01");
    day.setCount(3);
    when(reportMapper.countOrdersByDay(start, end)).thenReturn(List.of(day));
    when(reportMapper.listOrders(start, end)).thenReturn(List.of());
    ReportQualityAvg avg = new ReportQualityAvg();
    avg.setRatedCount(1);
    avg.setAvgPunctual(4.14);
    avg.setAvgStandard(5.0);
    avg.setAvgSafety(4.85);
    avg.setAvgAttitude(3.0);
    when(reportMapper.selectQualityAvg(start, end)).thenReturn(avg);
    ReportRescuerQuality row = new ReportRescuerQuality();
    row.setRescuerId(3L);
    row.setRescuerName("张三");
    row.setRatedCount(1);
    row.setAvgPunctual(4.14);
    row.setAvgStandard(5.0);
    row.setAvgSafety(4.85);
    row.setAvgAttitude(3.0);
    when(reportMapper.listQualityByRescuer(start, end)).thenReturn(List.of(row));
    when(reportMapper.countDetainInbound(start, end)).thenReturn(2L);
    when(reportMapper.countDetainInYard(start, end)).thenReturn(1L);
    when(reportMapper.countDetainOutbound(start, end)).thenReturn(3L);
    when(reportMapper.listDetainByLot(start, end)).thenReturn(List.of());

    ReportSummary s = service.summary("2026-09-01", "2026-09-02");
    assertEquals(4, s.getDispatch().getTotal());
    assertEquals(1, s.getDispatch().getCompleted());
    assertEquals(2, s.getDispatch().getInProgress());
    assertEquals(1, s.getDispatch().getAborted());
    assertEquals(2, s.getDispatch().getTrend().size());
    assertEquals("2026-09-01", s.getDispatch().getTrend().get(0).getDate());
    assertEquals(3, s.getDispatch().getTrend().get(0).getCount());
    assertEquals("2026-09-02", s.getDispatch().getTrend().get(1).getDate());
    assertEquals(0, s.getDispatch().getTrend().get(1).getCount());
    assertEquals(5, s.getDispatch().getStatusDist().size());
    assertEquals("PENDING", s.getDispatch().getStatusDist().get(0).getStatus());
    assertEquals(1, s.getQuality().getRatedCount());
    assertEquals(4.1, s.getQuality().getAvgPunctual());
    assertEquals(5.0, s.getQuality().getAvgStandard());
    assertEquals(4.9, s.getQuality().getAvgSafety());
    assertEquals(3.0, s.getQuality().getAvgAttitude());
    assertEquals(4.3, s.getQuality().getByRescuer().get(0).getAvgOverall());
    assertEquals(2, s.getDetain().getInbound());
    assertEquals(1, s.getDetain().getInYard());
    assertEquals(3, s.getDetain().getOutbound());
}

@Test
void summaryNullAveragesWhenNoRatings() {
    LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
    LocalDateTime end = LocalDateTime.of(2026, 9, 2, 0, 0);
    when(reportMapper.countOrders(start, end)).thenReturn(0L);
    when(reportMapper.countOrdersByStatus(start, end)).thenReturn(List.of());
    when(reportMapper.countOrdersByDay(start, end)).thenReturn(List.of());
    when(reportMapper.listOrders(start, end)).thenReturn(List.of());
    ReportQualityAvg avg = new ReportQualityAvg();
    avg.setRatedCount(0);
    when(reportMapper.selectQualityAvg(start, end)).thenReturn(avg);
    when(reportMapper.listQualityByRescuer(start, end)).thenReturn(List.of());
    when(reportMapper.countDetainInbound(start, end)).thenReturn(0L);
    when(reportMapper.countDetainInYard(start, end)).thenReturn(0L);
    when(reportMapper.countDetainOutbound(start, end)).thenReturn(0L);
    when(reportMapper.listDetainByLot(start, end)).thenReturn(List.of());

    ReportSummary s = service.summary("2026-09-01", "2026-09-01");
    assertNull(s.getQuality().getAvgPunctual());
    assertEquals(1, s.getDispatch().getTrend().size());
    assertEquals(0, s.getDispatch().getTrend().get(0).getCount());
}

private static ReportStatusCount status(String st, long n) {
    ReportStatusCount c = new ReportStatusCount();
    c.setStatus(st);
    c.setCount(n);
    return c;
}
```

`4.14 → 4.1`，`4.85 → 4.9`；overall = (4.1+5.0+4.9+3.0)/4 = 4.25 → **4.3**。

- [ ] **Step 2: 跑测试确认新用例失败**

Same mvn command. Expected: FAIL（字段/方法缺失或数字不对）

- [ ] **Step 3: 实现 Mapper SQL + Service 组装**

`ReportMapper` 注解 SQL：

```java
@Select("SELECT COUNT(*) FROM dispatch_order WHERE create_time >= #{start} AND create_time < #{end}")
long countOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT status, COUNT(*) AS count FROM dispatch_order "
        + "WHERE create_time >= #{start} AND create_time < #{end} GROUP BY status")
List<ReportStatusCount> countOrdersByStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS date, COUNT(*) AS count FROM dispatch_order "
        + "WHERE create_time >= #{start} AND create_time < #{end} GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')")
List<ReportDateCount> countOrdersByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT o.id, o.order_no AS orderNo, o.accident_address AS accidentAddress, o.status, o.plate_no AS plateNo, "
        + "COALESCE(NULLIF(u.real_name, ''), u.username) AS rescuerName, "
        + "o.create_time AS createTime, o.dispatched_at AS dispatchedAt, o.completed_at AS completedAt "
        + "FROM dispatch_order o LEFT JOIN user u ON u.id = o.rescuer_id "
        + "WHERE o.create_time >= #{start} AND o.create_time < #{end} "
        + "ORDER BY o.create_time DESC, o.id DESC")
List<ReportOrderRow> listOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT COUNT(*) AS ratedCount, AVG(e.score_punctual) AS avgPunctual, AVG(e.score_standard) AS avgStandard, "
        + "AVG(e.score_safety) AS avgSafety, AVG(e.score_attitude) AS avgAttitude "
        + "FROM dispatch_order o INNER JOIN dispatch_order_evaluation e ON e.dispatch_order_id = o.id "
        + "WHERE o.create_time >= #{start} AND o.create_time < #{end}")
ReportQualityAvg selectQualityAvg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT o.rescuer_id AS rescuerId, "
        + "COALESCE(NULLIF(u.real_name, ''), u.username, '未指定') AS rescuerName, "
        + "COUNT(*) AS ratedCount, AVG(e.score_punctual) AS avgPunctual, AVG(e.score_standard) AS avgStandard, "
        + "AVG(e.score_safety) AS avgSafety, AVG(e.score_attitude) AS avgAttitude "
        + "FROM dispatch_order o INNER JOIN dispatch_order_evaluation e ON e.dispatch_order_id = o.id "
        + "LEFT JOIN user u ON u.id = o.rescuer_id "
        + "WHERE o.create_time >= #{start} AND o.create_time < #{end} "
        + "GROUP BY o.rescuer_id, COALESCE(NULLIF(u.real_name, ''), u.username, '未指定')")
List<ReportRescuerQuality> listQualityByRescuer(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT COUNT(*) FROM detained_vehicle WHERE in_time >= #{start} AND in_time < #{end}")
long countDetainInbound(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT COUNT(*) FROM detained_vehicle WHERE in_time >= #{start} AND in_time < #{end} AND status = 'IN_YARD'")
long countDetainInYard(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT COUNT(*) FROM detained_vehicle WHERE out_time >= #{start} AND out_time < #{end}")
long countDetainOutbound(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Select("SELECT p.id AS parkingLotId, p.name AS parkingLotName, "
        + "SUM(CASE WHEN d.in_time >= #{start} AND d.in_time < #{end} THEN 1 ELSE 0 END) AS inbound, "
        + "SUM(CASE WHEN d.in_time >= #{start} AND d.in_time < #{end} AND d.status = 'IN_YARD' THEN 1 ELSE 0 END) AS inYard, "
        + "SUM(CASE WHEN d.out_time >= #{start} AND d.out_time < #{end} THEN 1 ELSE 0 END) AS outbound "
        + "FROM detained_vehicle d INNER JOIN parking_lot p ON p.id = d.parking_lot_id "
        + "WHERE (d.in_time >= #{start} AND d.in_time < #{end}) OR (d.out_time >= #{start} AND d.out_time < #{end}) "
        + "GROUP BY p.id, p.name")
List<ReportLotDetain> listDetainByLot(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
```

`summary` 在 `parseRange` 之后调用上述 mapper，按规则填充。`selectQualityAvg` 无行时 Mockito 可返回 ratedCount=0 的对象；真实 SQL 的 `COUNT(*)` 总会返回一行，Service 对 `null` avg 对象按 ratedCount=0 处理：

```java
if (avg == null || avg.getRatedCount() == 0) { /* 四维 null */ }
```

- [ ] **Step 4: 跑 `ReportServiceTest` 应全部 PASS**

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto \
  backend/src/main/java/com/example/backend/mapper/ReportMapper.java \
  backend/src/main/java/com/example/backend/service/ReportService.java \
  backend/src/test/java/com/example/backend/service/ReportServiceTest.java
git commit -m "feat: assemble report summary from date-window queries"
```

---

### Task 4: Excel 导出（TDD）

**Files:**
- Modify: `backend/pom.xml`（jjwt-jackson 依赖后、`</dependencies>` 前）增加：

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

- Modify: `ReportService.export`、`ReportServiceTest`

**Interfaces:**
- Consumes: `summary(from, to)`（同一套数据）
- Produces: `byte[]` xlsx；sheet 名精确为 `汇总`、`工单明细`、`评价按施救员`、`扣留按停车场`
- 汇总表第 1 行表头：`指标`、`数值`
- 工单状态中文：`PENDING` 待派单，`DISPATCHED` 已派单，`ACCEPTED` 已接单，`COMPLETED` 已完成，`ABORTED` 已中止
- 时间格式 `yyyy-MM-dd HH:mm`；null 写空字符串
- 均分 null 写空单元格（不要写 `null` 文本）

- [ ] **Step 1: 写导出测试**

```java
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.io.ByteArrayInputStream;

@Test
void exportWritesFourSheetsAndSummaryHeader() throws Exception {
    stubEmptySummaryWindow();
    byte[] bytes = service.export("2026-09-01", "2026-09-01");
    try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
        assertEquals(4, wb.getNumberOfSheets());
        assertEquals("汇总", wb.getSheetName(0));
        assertEquals("工单明细", wb.getSheetName(1));
        assertEquals("评价按施救员", wb.getSheetName(2));
        assertEquals("扣留按停车场", wb.getSheetName(3));
        assertEquals("指标", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        assertEquals("数值", wb.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
        assertEquals("工单总数", wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
    }
}

private void stubEmptySummaryWindow() {
    LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
    LocalDateTime end = LocalDateTime.of(2026, 9, 2, 0, 0);
    when(reportMapper.countOrders(start, end)).thenReturn(0L);
    when(reportMapper.countOrdersByStatus(start, end)).thenReturn(List.of());
    when(reportMapper.countOrdersByDay(start, end)).thenReturn(List.of());
    when(reportMapper.listOrders(start, end)).thenReturn(List.of());
    ReportQualityAvg avg = new ReportQualityAvg();
    avg.setRatedCount(0);
    when(reportMapper.selectQualityAvg(start, end)).thenReturn(avg);
    when(reportMapper.listQualityByRescuer(start, end)).thenReturn(List.of());
    when(reportMapper.countDetainInbound(start, end)).thenReturn(0L);
    when(reportMapper.countDetainInYard(start, end)).thenReturn(0L);
    when(reportMapper.countDetainOutbound(start, end)).thenReturn(0L);
    when(reportMapper.listDetainByLot(start, end)).thenReturn(List.of());
}
```

工单明细 / 评价 / 扣留表头行也要写上（即使无数据）：

工单：`单号,事故地址,状态,车牌,施救员,创建时间,派单时间,完成时间`  
评价：`施救员,评价单数,到达及时,处置规范,操作安全,服务态度,综合均分`  
扣留：`停车场,入库,在场,出库`

汇总行顺序：工单总数、已完成、处置中、中止、已评价数、到达及时均分、处置规范均分、操作安全均分、服务态度均分、入库、在场、出库。

- [ ] **Step 2: 跑测试确认失败**（缺 POI 或 `UnsupportedOperationException`）

- [ ] **Step 3: 实现 `export`**

使用 `XSSFWorkbook`。`export` 先 `ReportSummary s = summary(from, to)` 再写 workbook 到 `ByteArrayOutputStream`。IO 异常包装 `RuntimeException("导出失败")`。

- [ ] **Step 4: `ReportServiceTest` PASS**

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/example/backend/service/ReportService.java \
  backend/src/test/java/com/example/backend/service/ReportServiceTest.java
git commit -m "feat: export report workbook with four sheets"
```

---

### Task 5: ReportController

**Files:**
- Create: `backend/src/main/java/com/example/backend/controller/ReportController.java`

**Interfaces:**
- Consumes: `ReportService.summary` / `export`
- Produces:
  - `GET /api/report/summary?from=&to=` → `Result<ReportSummary>`，`@PreAuthorize("hasAuthority('report:query')")`
  - `GET /api/report/export?from=&to=` → xlsx 字节；校验失败 JSON `Result.error(400, message)`
- `SecurityConfig` 已 `anyRequest().authenticated()`，不必改

- [ ] **Step 1: 实现 Controller**

```java
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.ReportSummary;
import com.example.backend.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/report")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('report:query')")
    public Result<ReportSummary> summary(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        try {
            return Result.success(reportService.summary(from, to));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('report:query')")
    public void export(@RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       HttpServletResponse response) throws Exception {
        try {
            byte[] bytes = reportService.export(from, to);
            String filename = "report_" + from + "_" + to + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        } catch (RuntimeException e) {
            response.setStatus(200);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":400,\"message\":\"" +
                    e.getMessage().replace("\"", "'") + "\",\"data\":null}");
        }
    }
}
```

不要把 `export` 的异常交给全局 500。若 `e.getMessage()` 为 null，message 用 `导出失败`。

- [ ] **Step 2: 编译**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd -q -DskipTests compile
```

Expected: BUILD SUCCESS。然后重跑 `ReportServiceTest` 仍 PASS。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/backend/controller/ReportController.java
git commit -m "feat: add report summary and export endpoints"
```

---

### Task 6: PC 报表页

**Files:**
- Modify: `frontend/src/utils/request.js`
- Create: `frontend/src/api/report.js`
- Create: `frontend/src/views/report/Report.vue`
- Modify: `frontend/src/router/index.js`、`frontend/src/App.vue`
- Run: 在 `frontend/` 执行 `npm install echarts`

**Interfaces:**
- `getReportSummary({ from, to })` → `request.get('/report/summary', { params })`（沿用拦截器，返回 `{ code, data }` 解包后的 `data` 即 axios 拦截器 `return data` 整包，页面用 `res.data`）
- `exportReport({ from, to })` → `request.get('/report/export', { params, responseType: 'blob' })`，拦截器对 blob **原样返回 axios response**
- 路由 `/reports`，`name: 'Report'`，`meta.permissions: ['report:manage']`
- 菜单在概览 `router-link` 之后、`业务调度` 之前

- [ ] **Step 1: 安装 echarts**

```powershell
cd d:\cursorworkspace\frontend
npm install echarts
```

- [ ] **Step 2: 改 `request.js` 响应拦截器，blob 直接返回 `res`**

把 success 回调开头改成：

```javascript
(res) => {
  if (res.config.responseType === 'blob') {
    return res
  }
  const data = res.data
  if (data.code !== 200) {
    return Promise.reject(new Error(data.message || '请求失败'))
  }
  return data
}
```

否则导出的 Blob 没有 `.code`，会被误判失败。

- [ ] **Step 3: 新建 `frontend/src/api/report.js`**

```javascript
import request from '../utils/request'

export function getReportSummary(params) {
  return request.get('/report/summary', { params })
}

export function exportReport(params) {
  return request.get('/report/export', { params, responseType: 'blob' })
}
```

- [ ] **Step 4: 路由**

在 `vehicle-types` 路由对象之前（或其后）插入：

```javascript
{
  path: '/reports',
  name: 'Report',
  component: () => import('../views/report/Report.vue'),
  meta: { permissions: ['report:manage'] }
}
```

- [ ] **Step 5: `App.vue` 菜单 + 标题**

概览链接后插入：

```html
        <router-link
          v-auth="'report:manage'"
          to="/reports"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-ico">表</span>
          报表
        </router-link>
```

`pageTitle` map 增加 `'/reports': '报表'`。

- [ ] **Step 6: 实现 `Report.vue`**

要求（必须全部做到）：

- 默认 `from` = 当月 1 日、`to` = 今天（本地日期 `YYYY-MM-DD`）
- `onMounted` 自动查一次
- 查询前校验：缺日期 / `from > to` / 跨度 `Math.round((b-a)/86400000)+1 > 366`，文案与后端三句相同，**不发请求**
- `queryError` 显示在筛选条下；失败时 **不要** 把已有 `summary` 置空
- 卡片用与 `Home.vue` 类似的 `stat-row` / `stat-card`
- 工单表 `summary.dispatch.orders.slice(0, 50)`；单号 `<router-link :to="'/dispatches/' + row.id">`
- 状态中文映射与任务列表相同
- 均分 `v == null ? '—' : Number(v).toFixed(1)`
- 图表：`import * as echarts from 'echarts'`；`trendEl` 折线（日期 / count）；`statusEl` 柱状（五态中文 / count）；`scoreEl` 柱状（四维名称 / 均分）。`dispatch.total===0` 时趋势与状态图改为「暂无数据」；`quality.ratedCount===0` 时四维图「暂无数据」。`onBeforeUnmount` `chart.dispose()`
- 导出：`exportReport` 后检查 `content-type` 是否含 `json` 或 blob `type` 含 json；是则 `JSON.parse(await blob.text()).message` 提示；否则 `URL.createObjectURL` 下载 `report_${from}_${to}.xlsx`
- 表格空：一行 `colspan`「暂无数据」
- 不改 `Home.vue`

时间函数：

```javascript
function formatTime(v) {
  if (!v) return '—'
  const s = String(v).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}
function toYmd(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function inclusiveDays(from, to) {
  const a = new Date(from + 'T00:00:00')
  const b = new Date(to + 'T00:00:00')
  return Math.round((b - a) / 86400000) + 1
}
```

- [ ] **Step 7: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/api/report.js \
  frontend/src/utils/request.js frontend/src/router/index.js frontend/src/App.vue \
  frontend/src/views/report/Report.vue
git commit -m "feat: add PC operations report page with charts and export"
```

---

### Task 7: 浏览器验收

**Files:** 无新文件（只修验收中发现的 bug）

- [ ] **Step 1: 重启后端使权限与接口生效**（`spring-boot:run` 不热加载 Java）。`JAVA_HOME` 指向 JDK 17。前端 Vite 已在跑则刷新即可。

- [ ] **Step 2: admin 登录 `http://localhost:5173/reports`**

- 侧边栏工作台有「报表」
- 默认本月 1 号到今天，自动查出卡片/图/表
- 把日期改到没有工单的区间再查询，卡片为 0、图表或表空状态
- 开始日期晚于结束日期：出现「开始日期不能晚于结束日期」，网络面板无 summary 请求
- 点「导出 Excel」，下载 `report_*.xlsx`，用 Excel/WPS 打开四张表
- 若有工单行，点单号进入详情

- [ ] **Step 3: 调度员 `dispatcher` / `admin123` 登录**

能看见并打开报表。

- [ ] **Step 4: 交警或施救员登录**

侧边栏无「报表」；直接访问 `/reports` 到 403。

- [ ] **Step 5: 打开 `/` 概览**

统计卡 + 地图仍在，没有内部工作台、没有底部模块入口。

若有 bug，当场修并追加 commit（`fix: ...`），不要把无关文件打进提交。
