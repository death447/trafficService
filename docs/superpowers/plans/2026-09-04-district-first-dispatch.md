# 片区优先派单推荐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 改造 `GET /api/vehicle/nearby` 为片区优先 + 距离排序，并更新派单详情页分段展示「本片区推荐 / 其它车辆」。

**Architecture:** `RescueVehicleService.findNearby` 调用既有 `DistrictService.resolve`；返回 `NearbyVehiclesResponse(matchedDistrict, vehicles)`；`NearbyVehicleVO` 增加 `inMatchedDistrict`。前端 `DispatchDetail.vue` 按布尔拆段。不改 assign 状态机。

**Tech Stack:** Spring Boot 3.2、MyBatis、JUnit5+Mockito、Vue 3、Axios

**Spec:** `docs/superpowers/specs/2026-09-04-district-first-dispatch-design.md`

## Global Constraints

- 排序：`inMatchedDistrict` 为 true 的在前，组内 `distanceMeters` 升序；其余按距离
- `inMatchedDistrict = matched != null && Objects.equals(vehicle.districtId, matched.id)`；`districtId` 空 → false
- `matchedDistrict` 无匹配时 `null`；字段至少 `id`/`name`/`code`
- `limit` 默认 20、最大 50，作用于合并后列表
- 权限仍 `dispatch:dispatch`；不新增权限
- 破坏性响应变更：旧 `data: NearbyVehicleVO[]` → 新 `data: { matchedDistrict, vehicles }`；前后端同轮改
- YAGNI：不写工单 `district_id`、不强制落区、不新 recommend 接口、不画围栏
- 无坐标工单：前端保持现有兜底列表，不强制片区分组

## File Structure

### Backend
- Create: `dto/MatchedDistrictVO.java`、`dto/NearbyVehiclesResponse.java`
- Modify: `dto/NearbyVehicleVO.java`、`service/RescueVehicleService.java`、`controller/VehicleController.java`
- Modify: `test/.../service/RescueVehicleServiceTest.java`

### Frontend
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`
- `frontend/src/api/vehicle.js` 可保持函数签名；调用方改读 `res.data.vehicles`

---

### Task 1: 后端 nearby 片区优先 + 新响应类型

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/MatchedDistrictVO.java`
- Create: `backend/src/main/java/com/example/backend/dto/NearbyVehiclesResponse.java`
- Modify: `backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java`
- Modify: `backend/src/main/java/com/example/backend/service/RescueVehicleService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/VehicleController.java`
- Test: `backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java`

**Interfaces:**
- Consumes: `DistrictService.resolve(BigDecimal lng, BigDecimal lat): District`（可 null）
- Produces:
  - `NearbyVehicleVO`: 既有字段 + `boolean inMatchedDistrict`
  - `MatchedDistrictVO`: `Long id`, `String name`, `String code`；静态工厂 `from(District d)`（d null → null）
  - `NearbyVehiclesResponse`: `MatchedDistrictVO matchedDistrict`, `List<NearbyVehicleVO> vehicles`
  - `RescueVehicleService.findNearby(...): NearbyVehiclesResponse`
  - Controller: `Result<NearbyVehiclesResponse>`

- [ ] **Step 1: 改写并扩展失败测试**

在 `RescueVehicleServiceTest`：

1. 为 `@InjectMocks RescueVehicleService` 增加 `@Mock DistrictService districtService`（若尚未注入；当前类已有 `DistrictMapper` mock——**新增 `DistrictService` mock**，不要误用 Mapper 做 resolve）。
2. 将 `nearbySortsIdleByDistanceAscending` 改为断言新类型：

```java
when(districtService.resolve(any(), any())).thenReturn(null);
NearbyVehiclesResponse resp = service.findNearby(
        new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);
assertNull(resp.getMatchedDistrict());
assertEquals(2, resp.getVehicles().size());
assertEquals(1L, resp.getVehicles().get(0).getVehicle().getId());
assertFalse(resp.getVehicles().get(0).isInMatchedDistrict());
assertTrue(resp.getVehicles().get(0).getDistanceMeters()
        < resp.getVehicles().get(1).getDistanceMeters());
```

3. 新增 `nearbyPrefersMatchedDistrictThenDistance`：

```java
District matched = new District();
matched.setId(1L);
matched.setName("福田中心片区");
matched.setCode("FT-CENTER");
matched.setStatus("ENABLED");
when(districtService.resolve(any(), any())).thenReturn(matched);

RescueVehicle inDistrictFar = vehicle(10L, "粤B远本区", "114.100", "22.600");
inDistrictFar.setDistrictId(1L);
RescueVehicle otherNear = vehicle(11L, "粤B近外区", "114.058", "22.543");
otherNear.setDistrictId(2L);
RescueVehicle inDistrictNear = vehicle(12L, "粤B近本区", "114.058", "22.544");
inDistrictNear.setDistrictId(1L);
when(vehicleMapper.findByStatus("IDLE"))
        .thenReturn(List.of(inDistrictFar, otherNear, inDistrictNear));

NearbyVehiclesResponse resp = service.findNearby(
        new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

assertEquals(1L, resp.getMatchedDistrict().getId());
assertEquals(List.of(12L, 10L, 11L),
        resp.getVehicles().stream().map(v -> v.getVehicle().getId()).toList());
assertTrue(resp.getVehicles().get(0).isInMatchedDistrict());
assertTrue(resp.getVehicles().get(1).isInMatchedDistrict());
assertFalse(resp.getVehicles().get(2).isInMatchedDistrict());
```

4. 新增 `nearbyUnmatchedMarksAllFalse`：resolve null，带 `districtId` 的车仍 `inMatchedDistrict=false`。

- [ ] **Step 2: 跑测确认失败**

```bash
cd backend && mvn -q -Dtest=RescueVehicleServiceTest#nearbySortsIdleByDistanceAscending,RescueVehicleServiceTest#nearbyPrefersMatchedDistrictThenDistance,RescueVehicleServiceTest#nearbyUnmatchedMarksAllFalse test
```

Expected: FAIL（返回类型/方法签名仍为 List，或缺 DistrictService）

- [ ] **Step 3: 实现 DTO**

`MatchedDistrictVO.java`：

```java
@Data
public class MatchedDistrictVO {
    private Long id;
    private String name;
    private String code;
    public static MatchedDistrictVO from(District d) {
        if (d == null) return null;
        MatchedDistrictVO vo = new MatchedDistrictVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setCode(d.getCode());
        return vo;
    }
}
```

`NearbyVehiclesResponse.java`：`matchedDistrict` + `vehicles`（Lombok `@Data`）。

`NearbyVehicleVO`：增加 `private boolean inMatchedDistrict;`

- [ ] **Step 4: 改 `RescueVehicleService.findNearby`**

- 注入 `@Autowired DistrictService districtService`
- 方法返回 `NearbyVehiclesResponse`
- 逻辑：

```java
District matched = districtService.resolve(lng, lat);
Long matchedId = matched == null ? null : matched.getId();
// ... map IDLE with coords to VO ...
vo.setInMatchedDistrict(matchedId != null && matchedId.equals(v.getDistrictId()));
// sort:
.sorted(Comparator
    .comparing((NearbyVehicleVO x) -> !x.isInMatchedDistrict())
    .thenComparingDouble(NearbyVehicleVO::getDistanceMeters))
.limit(effectiveLimit)
```

注意：`comparing(x -> !x.isInMatchedDistrict())` 使 `true`（本片区）排前。

组装：

```java
NearbyVehiclesResponse resp = new NearbyVehiclesResponse();
resp.setMatchedDistrict(MatchedDistrictVO.from(matched));
resp.setVehicles(list);
return resp;
```

- [ ] **Step 5: 改 `VehicleController.findNearby` 返回 `Result<NearbyVehiclesResponse>`**

- [ ] **Step 6: 跑 `RescueVehicleServiceTest` 全绿**

```bash
mvn -q -Dtest=RescueVehicleServiceTest test
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/MatchedDistrictVO.java \
  backend/src/main/java/com/example/backend/dto/NearbyVehiclesResponse.java \
  backend/src/main/java/com/example/backend/dto/NearbyVehicleVO.java \
  backend/src/main/java/com/example/backend/service/RescueVehicleService.java \
  backend/src/main/java/com/example/backend/controller/VehicleController.java \
  backend/src/test/java/com/example/backend/service/RescueVehicleServiceTest.java
git commit -m "feat(backend): prefer in-district idle vehicles in nearby API"
```

---

### Task 2: 前端派单详情分段展示

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`
- Verify: `frontend/src/api/vehicle.js`（无需改导出，除非有封装解析）

**Interfaces:**
- Consumes: `res.data.matchedDistrict`, `res.data.vehicles`
- Produces: UI 两段列表 + 片区提示文案

- [ ] **Step 1: 调整 `loadNearby` 状态**

在 script setup 增加：

```js
const matchedDistrict = ref(null)
const nearbyInDistrict = computed(() =>
  nearby.value.filter((i) => i.inMatchedDistrict))
const nearbyOthers = computed(() =>
  nearby.value.filter((i) => !i.inMatchedDistrict))
```

在成功解析 nearby 响应处：

```js
matchedDistrict.value = res.data?.matchedDistrict ?? null
nearby.value = res.data?.vehicles || []
```

无坐标兜底路径：`matchedDistrict.value = null`；列表项补 `inMatchedDistrict: false`。

- [ ] **Step 2: 改模板「附近空闲车辆」面板**

在 section 标题下增加：

```html
<p v-if="matchedDistrict" class="hint-inline">
  所属片区：{{ matchedDistrict.name }}（{{ matchedDistrict.code }}）
</p>
<p v-else-if="hasCoords && !nearbyLoading" class="hint-inline">未匹配到片区</p>
```

有 `matchedDistrict` 时渲染两段：

```html
<template v-if="matchedDistrict">
  <h3 class="subsection-title">本片区推荐</h3>
  <ul class="vehicle-list">...</ul> <!-- nearbyInDistrict；空则 empty「本片区暂无空闲车辆」 -->
  <h3 class="subsection-title">其它车辆</h3>
  <ul class="vehicle-list">...</ul> <!-- nearbyOthers -->
</template>
<template v-else>
  <h3 class="subsection-title">其它车辆</h3>
  <ul>...</ul> <!-- nearby（全部） -->
</template>
```

抽取列表项为复用（同一 `li` 结构），避免复制粘贴出错：可用小组件或 `v-for` 函数不变。

选中态 / 确认派单按钮逻辑不变（仍用 `selectedVehicleId`）。

- [ ] **Step 3: 样式**

`.subsection-title`：略小于 `.section-title`，与现有 `enterprise` / DispatchDetail 风格一致（无新 UI 库）。

- [ ] **Step 4: 构建检查**

```bash
cd frontend && npm run build
```

Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/dispatch/DispatchDetail.vue
git commit -m "feat(frontend): show in-district vs other vehicles on dispatch detail"
```

---

### Task 3: 回归与手工验收清单

**Files:** 无强制代码变更

- [ ] **Step 1: 后端全量单测**

```bash
cd backend && mvn -q test
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 对照验收表（可手工或记录 deferred）**

| # | 检查 |
|---|------|
| 1 | 福田样例点 + 本片区 IDLE → 本片区段有车且排前 |
| 2 | 本片区无 IDLE → 空提示 + 其它可派 |
| 3 | 围栏外点 → 未匹配 + 仅其它车辆 |
| 4 | 确认派单 → BUSY |
| 5 | 单测已覆盖 |

- [ ] **Step 3: 若无需代码则不强制 commit**；在报告中写明测试结果

---

## Spec coverage (self-review)

| Spec 项 | Task |
|---------|------|
| nearby 新响应 + inMatchedDistrict | 1 |
| 本片区优先再距离 | 1 |
| 未匹配 null + UI 提示 | 1–2 |
| 两段标题文案 | 2 |
| assign 不变 | 约束 + Task 2 不改 assign |
| 单测 | 1、3 |
| 不做工单 district_id / 强制落区 | YAGNI |

无 TBD；类型名前后一致：`NearbyVehiclesResponse` / `MatchedDistrictVO`。
