# 入库入场编码页面预生成 Implementation Plan

**Goal:** 移动端入库登记第三步在进入页面时展示后端预生成的入场编码（`yyyyMMddHH` + 工单ID 4 位 + 随机 5 位），提交时优先使用，冲突自动换码。

**Architecture:** 新增 `EntryNoGenerator` 收敛编码格式；`DetainedVehicleService.checkIn` 改为「优先采用传入编码、否则生成并重试至唯一」；新增 `GET /api/mobile/rescuer/tasks/{id}/entry-no` 供移动端预取；移动端 `park.vue` 展示并在提交时回传。

**Tech Stack:** Spring Boot 3.2 / Java 17 / MyBatis / JUnit 5 + Mockito、Uni-App Vue 3。

## Global Constraints

- 控制器统一返回 `com.example.backend.common.Result`，业务异常 `catch (RuntimeException e) { return Result.error(e.getMessage()); }`。
- 提示文案中文，风格与现有代码一致。
- 移动端接口前缀 `/mobile/rescuer`（`mobile-rescuer/src/api/rescuer.js` 的 `PREFIX`）。
- 新接口权限 `hasAuthority('rescuer:park')`。
- 后端测试：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=<TestClass> test`
- 移动端构建：在 `mobile-rescuer` 执行 `npm run build:h5`

---

### Task 1: `EntryNoGenerator` + 单元测试

**Files:**
- Create: `backend/src/main/java/com/example/backend/support/EntryNoGenerator.java`
- Test: `backend/src/test/java/com/example/backend/support/EntryNoGeneratorTest.java`

**Produces:** `String generate(Long orderId)`、`boolean matches(String code, Long orderId)`。

- [ ] Step 1: 写 `EntryNoGeneratorTest`（长度19/纯数字/小时前缀/工单因子/后4位/随机性/matches 负例），跑测试确认失败
- [ ] Step 2: 实现 `EntryNoGenerator`
- [ ] Step 3: 跑测试确认通过

### Task 2: `DetainedVehicleMapper.findByEntryNo` + `DetainInRequest.entryNo`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/mapper/DetainedVehicleMapper.java`
- Modify: `backend/src/main/java/com/example/backend/dto/DetainInRequest.java`

- [ ] Step 1: 加 `findByEntryNo`，删除已无用的 `countByEntryNoPrefix`
- [ ] Step 2: `DetainInRequest` 加 `private String entryNo;`

### Task 3: `checkIn` 采用传入编码并冲突重试

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java`
- Test: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

- [ ] Step 1: 新增 `resolveEntryNo`，`checkIn` 改用它，删除 `generateEntryNo`
- [ ] Step 2: 移除测试里所有 `countByEntryNoPrefix` stub（Mockito 严格模式会因未使用 stub 失败）
- [ ] Step 3: 新增三条用例（采用传入、非法回退、占用回退）
- [ ] Step 4: 跑 `DetainedVehicleServiceTest` 全绿

### Task 4: 预生成接口 + `RescuerMobileServiceTest`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/RescuerMobileController.java`
- Test: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

- [ ] Step 1: `RescuerMobileService.generateEntryNo(userId, orderId)`：`requireAssignment` 后 `entryNoGenerator.generate(orderId)`
- [ ] Step 2: 控制器加 `GET /tasks/{id}/entry-no`
- [ ] Step 3: 补测试（成功返回19位、非本人工单抛错）
- [ ] Step 4: 跑测试

### Task 5: 移动端展示与提交

**Files:**
- Modify: `mobile-rescuer/src/api/rescuer.js`
- Modify: `mobile-rescuer/src/pages/task/park.vue`

- [ ] Step 1: `getTaskEntryNo(id)` 封装
- [ ] Step 2: `onLoad` 预取（失败静默），第三步展示 `entryNo`
- [ ] Step 3: 提交 payload 带 `entryNo`
- [ ] Step 4: `npm run build:h5` 通过

### Task 6: 端到端验证

- [ ] 重启后端，登录救援员进入入库登记，确认编码前缀与当前小时一致
- [ ] 提交后 PC 扣留车辆列表编码与页面一致
- [ ] 构造重复编码提交 → 成功且编码被替换
