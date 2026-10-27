# 现场采集支持多辆事故车 Implementation Plan

**Goal:** 救援员移动端「现场采集」支持一个工单的多辆事故车逐车填写车型/受损描述/照片，PC 工单详情、救援员任务详情、交警详情同步逐车展示。

**Architecture:** 新增 `dispatch_scene_vehicle` 表存每车现场记录，`dispatch_media` 加 `accident_vehicle_id`；`saveScene` 改为按车删除重插并回写 `field_record` 摘要；新增 `SceneVehicleAssembler` 供三处详情接口复用；移动端 `scene.vue` 改为卡片列表。

**Tech Stack:** Spring Boot 3.2 / Java 17 / MyBatis / JUnit 5 + Mockito、Uni-App Vue 3、Vue 3 + Vite。

## Global Constraints

- 控制器统一返回 `com.example.backend.common.Result`，业务异常 `catch (RuntimeException e) { return Result.error(e.getMessage()); }`。
- 提示文案中文，风格与现有代码一致。
- 移动端接口前缀 `/mobile/rescuer`。
- 数据库名 `vue_springboot_system`，本机 root 密码 `minth@888`，端口 3306。
- 后端测试：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q "-Dtest=<Class>" test`
- 移动端构建：在 `mobile-rescuer` 执行 `npm run build:h5`；PC 构建：在 `frontend` 执行 `npm run build`
- 每个 Task 结束提交一次。

---

### Task 1: 数据库脚本

**Files:**
- Create: `database/migrate_2026-10-27_scene_multi_vehicle.sql`
- Modify: `database/init.sql`

- [ ] Step 1: 迁移脚本建 `dispatch_scene_vehicle` 表，并给 `dispatch_media` 加 `accident_vehicle_id` 列与索引（用 `IF NOT EXISTS` / 检查 `information_schema` 保证可重复执行）
- [ ] Step 2: `init.sql` 同步新表与列定义
- [ ] Step 3: 本地执行迁移脚本，确认表与列存在

### Task 2: 实体与 Mapper

**Files:**
- Create: `backend/src/main/java/com/example/backend/entity/DispatchSceneVehicle.java`
- Create: `backend/src/main/java/com/example/backend/mapper/DispatchSceneVehicleMapper.java`
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchMedia.java`
- Modify: `backend/src/main/java/com/example/backend/mapper/DispatchMediaMapper.java`

- [ ] Step 1: 新增实体（`id/dispatchOrderId/assignmentId/accidentVehicleId/plateNo/vehicleType/damageDesc/sortOrder`）
- [ ] Step 2: Mapper 提供 `findByOrderId`、`deleteByOrderId`、`insert`
- [ ] Step 3: `DispatchMedia` 加 `accidentVehicleId`，insert 语句带上该列

### Task 3: DTO 与组装器

**Files:**
- Create: `backend/src/main/java/com/example/backend/dto/SceneVehicleItem.java`
- Create: `backend/src/main/java/com/example/backend/dto/SceneVehicleVO.java`
- Create: `backend/src/main/java/com/example/backend/support/SceneVehicleAssembler.java`
- Modify: `backend/src/main/java/com/example/backend/dto/SceneRequest.java`
- Modify: `backend/src/main/java/com/example/backend/dto/RescuerTaskDetail.java`
- Modify: `backend/src/main/java/com/example/backend/dto/PoliceTaskDetail.java`
- Modify: `backend/src/main/java/com/example/backend/entity/DispatchOrder.java`

- [ ] Step 1: 三个 DTO/实体加 `sceneVehicles` 字段
- [ ] Step 2: `SceneVehicleAssembler.assemble(orderId)` 读表 + 按 `accident_vehicle_id` 挂 DAMAGE 照片
- [ ] Step 3: 编译通过

### Task 4: `saveScene` 逐车写入

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Test: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

- [ ] Step 1: 写失败测试（多车保存、车牌不属于工单、摘要回写）
- [ ] Step 2: 实现 `saveScene`：校验车牌 → 删除旧行 → 逐条插入 → 回写摘要
- [ ] Step 3: 保留旧字段兼容路径（`vehicles` 为空时用 `plateNo/vehicleType/damageDesc`）
- [ ] Step 4: 测试通过

### Task 5: 详情接口返回 `sceneVehicles`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Modify: `backend/src/main/java/com/example/backend/service/PoliceMobileService.java`
- Modify: `backend/src/main/java/com/example/backend/service/DispatchOrderService.java`
- Test: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

- [ ] Step 1: 三处调用 `SceneVehicleAssembler` 填充 `sceneVehicles`
- [ ] Step 2: 补 `getTask` 分组测试
- [ ] Step 3: 测试通过

### Task 6: 媒体按车归属

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/RescuerMobileController.java`
- Test: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

- [ ] Step 1: `addMedia` 增加 `accidentVehicleId` 参数并校验归属与 bizType
- [ ] Step 2: 控制器加可选 `@RequestParam Long accidentVehicleId`
- [ ] Step 3: 补测试（合法、他单车辆、PARK 带车 ID）
- [ ] Step 4: 测试通过

### Task 7: 移动端现场采集页

**Files:**
- Modify: `mobile-rescuer/src/api/rescuer.js`
- Modify: `mobile-rescuer/src/pages/task/scene.vue`

- [ ] Step 1: `uploadMedia` 支持 `accidentVehicleId`
- [ ] Step 2: `scene.vue` 改为逐车卡片列表（车牌只读、车型 picker、受损描述、照片网格）
- [ ] Step 3: 回显优先 `sceneVehicles`，为空回退 `fieldRecord`
- [ ] Step 4: 保存提交 `{ sceneRemark, vehicles }`
- [ ] Step 5: `npm run build:h5` 通过

### Task 8: 移动端任务详情与交警详情逐车展示

**Files:**
- Modify: `mobile-rescuer/src/pages/task/detail.vue`
- Modify: `mobile-rescuer/src/pages/police/detail.vue`

- [ ] Step 1: 现场摘要改为逐车列表，照片按车分组
- [ ] Step 2: `sceneVehicles` 为空时回退旧摘要
- [ ] Step 3: `npm run build:h5` 通过

### Task 9: PC 工单详情逐车展示

**Files:**
- Modify: `frontend/src/views/dispatch/DispatchDetail.vue`

- [ ] Step 1: 现场采集面板逐车渲染车牌/车型/受损描述/照片
- [ ] Step 2: 保留采集时间与现场备注，空数据回退旧布局
- [ ] Step 3: `npm run build` 通过

### Task 10: 端到端验证

- [ ] 重启后端，用两辆事故车的工单在移动端逐车填写保存
- [ ] 重新进入回显正确
- [ ] PC 工单详情、移动端任务详情、交警详情逐车展示
- [ ] 历史单车工单三处仍正常
