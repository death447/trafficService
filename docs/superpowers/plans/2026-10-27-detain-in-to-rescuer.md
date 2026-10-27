# 入库改由救援员填写 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把扣留车辆入库唯一入口移到救援员移动端的任务详情「入库登记」，救援员完成三步表单入库，停车场管理员与 PC 不再提供入库入口。

**Architecture:** 新增 `/api/mobile/rescuer` 下 4 组接口（停车场下拉、工单入库、入库回显、入库照片），复用 `DetainedVehicleService.checkIn` 的校验与 DAMAGE→SCENE 复制，并在成功后回写 `dispatch_field_record` 的停放信息。移动端将 `pages/task/park.vue` 重写为三步向导，删除停车场管理员入库页与 PC 入库弹窗，最后收紧 `PARKING_ADMIN` 的 `detain:add` 权限。

**Tech Stack:** Spring Boot 3.2 / Java 17 / MyBatis / Spring Security（`@PreAuthorize`）、Vue 3 + Vite（PC）、Uni-App Vue 3（移动端 H5）、MySQL 8.4、JUnit 5 + Mockito。

## Global Constraints

- 所有后端控制器返回 `com.example.backend.common.Result`，业务异常在控制器内 `catch (RuntimeException e) { return Result.error(e.getMessage()); }`。
- 所有面向用户的提示文案使用中文，与现有代码风格一致（如「停车场不能为空」「仅已接单状态可操作」）。
- 移动端接口前缀固定 `/mobile/rescuer`（`mobile-rescuer/src/api/rescuer.js` 中的 `PREFIX`）。
- 新接口权限固定为 `hasAuthority('rescuer:park')`；`rescuer:park` 的 permission id 为 61，`detain:add` 为 48，`PARKING_ADMIN` role_id = 4，`TOW_DRIVER` role_id = 3。
- 数据库名 `vue_springboot_system`，本机 root 密码 `minth@888`，端口 3306。
- 后端测试命令：
  `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=<TestClass> test`
- 移动端构建校验命令（在 `mobile-rescuer`）：`npm run build:h5`
- PC 构建校验命令（在 `frontend`）：`npm run build`
- 每个 Task 结束都要 `git add` 该 Task 涉及的文件并提交，提交信息用 Task 中给出的文案。
- 不要改动 `backend/src/main/java/com/example/backend/controller/DetainedVehicleController.java` 的既有接口签名。

---

### Task 1: 后端 — 入库车牌按工单事故车校验

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/DetainedVehicleService.java:136-152`（`checkIn` 内的关联工单校验段）
- Test: `backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java`

**Interfaces:**
- Consumes: `DispatchAccidentVehicleMapper.findByOrderId(Long)`（已存在）、`PlateNos.normalize(String)`（已存在）。
- Produces: `DetainedVehicleService.checkIn(DetainInRequest, Long)` 行为变更 —— 当关联工单存在事故车时，入库车牌匹配任一事故车即可通过；无事故车时仍回退到 `dispatch_order.plate_no`。方法签名不变。

**背景（为什么必须改）：** 当前 `checkIn` 用 `PlateNos.normalize(plate).equals(PlateNos.normalize(order.getPlateNo()))` 校验，而 `DispatchOrderService` 在创建工单时把 `order.plate_no` 设为**第一辆**事故车（`DispatchOrderService.java:264`）。多车工单里第二辆车入库必然报「工单车牌与入库车牌不一致」。

- [ ] **Step 1: 写失败测试**

在 `DetainedVehicleServiceTest` 的 `checkInAcceptsLinkedOrderWhenNormalizedPlateMatches` 方法之后插入两个用例：

```java
    @Test
    void checkInAcceptsPlateOfSecondAccidentVehicle() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-MULTI");
        req.setPlateNo("粤B22222");
        req.setParkingLotId(1L);
        req.setDispatchOrderId(11L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-MULTI")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B22222")).thenReturn(0);
        when(dispatchOrderMapper.findById(11L)).thenReturn(order(11L, "ACCEPTED", "粤B11111"));
        when(accidentVehicleMapper.findByOrderId(11L)).thenReturn(List.of(
                accidentVehicle(11L, "粤B11111", "小型汽车"),
                accidentVehicle(11L, "粤B22222", "小型汽车")));
        when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
        when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
            DetainedVehicle v = inv.getArgument(0);
            v.setId(102L);
            return 1;
        });
        when(dispatchMediaMapper.findByOrderIdAndBizType(11L, "DAMAGE")).thenReturn(List.of());

        DetainedVehicle created = service.checkIn(req, 4L);

        assertEquals("粤B22222", created.getPlateNo());
        assertEquals(11L, created.getDispatchOrderId());
    }

    @Test
    void checkInRejectsPlateMissingFromOrderAccidentVehicles() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-NONE");
        req.setPlateNo("粤B99999");
        req.setParkingLotId(1L);
        req.setDispatchOrderId(12L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-NONE")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B99999")).thenReturn(0);
        when(dispatchOrderMapper.findById(12L)).thenReturn(order(12L, "ACCEPTED", "粤B11111"));
        when(accidentVehicleMapper.findByOrderId(12L)).thenReturn(List.of(
                accidentVehicle(12L, "粤B11111", "小型汽车"),
                accidentVehicle(12L, "粤B22222", "小型汽车")));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 4L));

        assertTrue(ex.getMessage().contains("车牌"));
        verify(detainedVehicleMapper, never()).insert(any());
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=DetainedVehicleServiceTest test`
Expected: FAIL —— `checkInAcceptsPlateOfSecondAccidentVehicle` 抛出「工单车牌与入库车牌不一致」。

- [ ] **Step 3: 实现校验**

把 `DetainedVehicleService.checkIn` 里的这段：

```java
        if (req.getDispatchOrderId() != null) {
            DispatchOrder order = dispatchOrderMapper.findById(req.getDispatchOrderId());
            if (order == null) {
                throw new RuntimeException("关联救援工单不存在");
            }
            String st = order.getStatus();
            if (!"PENDING".equals(st) && !"DISPATCHED".equals(st) && !"ACCEPTED".equals(st)) {
                throw new RuntimeException("工单已结束，无法关联");
            }
            if (!PlateNos.normalize(plate).equals(PlateNos.normalize(order.getPlateNo()))) {
                throw new RuntimeException("工单车牌与入库车牌不一致");
            }
        }
```

替换为：

```java
        if (req.getDispatchOrderId() != null) {
            validateOrderLink(req.getDispatchOrderId(), plate);
        }
```

并在 `validateEnums` 方法之前新增私有方法：

```java
    private void validateOrderLink(Long dispatchOrderId, String plate) {
        DispatchOrder order = dispatchOrderMapper.findById(dispatchOrderId);
        if (order == null) {
            throw new RuntimeException("关联救援工单不存在");
        }
        String st = order.getStatus();
        if (!"PENDING".equals(st) && !"DISPATCHED".equals(st) && !"ACCEPTED".equals(st)) {
            throw new RuntimeException("工单已结束，无法关联");
        }
        String needle = PlateNos.normalize(plate);
        List<DispatchAccidentVehicle> vehicles = accidentVehicleMapper.findByOrderId(dispatchOrderId);
        if (vehicles != null && !vehicles.isEmpty()) {
            for (DispatchAccidentVehicle av : vehicles) {
                if (av != null && needle.equals(PlateNos.normalize(av.getPlateNo()))) {
                    return;
                }
            }
            throw new RuntimeException("工单车牌与入库车牌不一致");
        }
        if (!needle.equals(PlateNos.normalize(order.getPlateNo()))) {
            throw new RuntimeException("工单车牌与入库车牌不一致");
        }
    }
```

- [ ] **Step 4: 运行整个测试类确认通过**

Run: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=DetainedVehicleServiceTest test`
Expected: BUILD SUCCESS（既有的 `checkInRejectsFinishedLinkedOrder`、`checkInRejectsLinkedOrderPlateMismatch`、`checkInAcceptsLinkedOrderWhenNormalizedPlateMatches` 仍通过，因为它们的 `accidentVehicleMapper.findByOrderId` 返回 `null` 时回退到 order 车牌）。

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/example/backend/service/DetainedVehicleService.java backend/src/test/java/com/example/backend/service/DetainedVehicleServiceTest.java
git commit -m "feat(detain): allow checking in any accident vehicle of an order"
```

---

### Task 2: 后端 — RescuerMobileService 入库 / 回显 / 照片 / 停车场

**Files:**
- Modify: `backend/src/main/java/com/example/backend/mapper/DetainedVehicleMapper.java`（文件末尾新增方法）
- Modify: `backend/src/main/java/com/example/backend/service/RescuerMobileService.java`
- Test: `backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java`

**Interfaces:**
- Consumes:
  - `DetainedVehicleService.checkIn(DetainInRequest req, Long operatorUserId) : DetainedVehicle`（Task 1 已更新校验）
  - `ParkingLotService.list(String keyword, String status, PageParams pp) : Map<String,Object>`
  - `ParkingLotService.findById(Long) : ParkingLot`
  - `ParkingAreaService.list(Long lotId, String status) : List<ParkingArea>`
  - `ParkingAreaService.findById(Long) : ParkingArea`
  - `LocalFileStorageService.storeDetainMedia(Long detainId, MultipartFile file) : String`
  - `DetainedVehicleMapper.findInYardByDispatchOrderId(Long orderId) : DetainedVehicle`（本 Task 新增）
- Produces（Task 3/5/6 依赖以下精确签名）:
  - `DetainedVehicle detainIn(Long userId, Long orderId, DetainInRequest req)`
  - `DetainedVehicle getTaskDetain(Long userId, Long orderId)`
  - `List<ParkingLot> listEnabledParkingLots()`
  - `List<ParkingArea> listParkingAreas(Long lotId)`
  - `DetainMedia addDetainMedia(Long userId, Long detainId, String bizType, MultipartFile file)`

- [ ] **Step 1: 新增 Mapper 方法**

在 `DetainedVehicleMapper` 接口末尾（`update` 方法之前）加入：

```java
    @Select("SELECT * FROM detained_vehicle WHERE dispatch_order_id = #{orderId} AND status = 'IN_YARD' ORDER BY id DESC LIMIT 1")
    DetainedVehicle findInYardByDispatchOrderId(Long orderId);
```

- [ ] **Step 2: 写失败测试**

在 `RescuerMobileServiceTest` 顶部增加 mock 字段（保留原有的 mock，不要删除）：

```java
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @Mock DetainMediaMapper detainMediaMapper;
    @Mock DetainedVehicleService detainedVehicleService;
    @Mock ParkingLotService parkingLotService;
    @Mock ParkingAreaService parkingAreaService;
```

补充 import：

```java
import com.example.backend.common.PageParams;
import com.example.backend.dto.DetainInRequest;
import com.example.backend.entity.DetainMedia;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.DispatchAccidentVehicle;
import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
import com.example.backend.entity.User;
import com.example.backend.mapper.DetainMediaMapper;
import com.example.backend.mapper.DetainedVehicleMapper;
import java.util.List;
import java.util.Map;
```

在类的最后一个 `@Test` 之后、类结尾 `}` 之前加入测试方法：

```java
    @Test
    void detainInForcesOrderAndRescuerNameAndWritesParkAddress() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));
        DispatchAccidentVehicle first = new DispatchAccidentVehicle();
        first.setDispatchOrderId(1L);
        first.setPlateNo("粤B11111");
        DispatchAccidentVehicle second = new DispatchAccidentVehicle();
        second.setDispatchOrderId(1L);
        second.setPlateNo("粤B22222");
        when(accidentVehicleMapper.findByOrderId(1L)).thenReturn(List.of(first, second));

        User user = new User();
        user.setId(9L);
        user.setUsername("towdriver");
        user.setRealName("施救员演示");
        when(userMapper.findById(9L)).thenReturn(user);

        DetainedVehicle created = new DetainedVehicle();
        created.setId(33L);
        when(detainedVehicleService.checkIn(any(DetainInRequest.class), eq(9L))).thenReturn(created);
        when(fieldRecordMapper.findByAssignmentId(50L)).thenReturn(null);
        when(fieldRecordMapper.insert(any(DispatchFieldRecord.class))).thenReturn(1);

        ParkingLot lot = new ParkingLot();
        lot.setId(2L);
        lot.setName("福田停车场");
        when(parkingLotService.findById(2L)).thenReturn(lot);
        ParkingArea area = new ParkingArea();
        area.setId(3L);
        area.setName("A区");
        when(parkingAreaService.findById(3L)).thenReturn(area);

        DetainInRequest req = new DetainInRequest();
        req.setPlateNo("粤B22222");
        req.setParkingLotId(2L);
        req.setParkingAreaId(3L);
        req.setStallNo("A-01");
        req.setRemark("入库备注");
        req.setRescuerName("伪造姓名");

        DetainedVehicle result = service.detainIn(9L, 1L, req);

        assertEquals(33L, result.getId());
        ArgumentCaptor<DetainInRequest> reqCaptor = ArgumentCaptor.forClass(DetainInRequest.class);
        verify(detainedVehicleService).checkIn(reqCaptor.capture(), eq(9L));
        assertEquals(1L, reqCaptor.getValue().getDispatchOrderId());
        assertEquals("施救员演示", reqCaptor.getValue().getRescuerName());

        ArgumentCaptor<DispatchFieldRecord> frCaptor = ArgumentCaptor.forClass(DispatchFieldRecord.class);
        verify(fieldRecordMapper).insert(frCaptor.capture());
        assertEquals("福田停车场/A区 车位A-01", frCaptor.getValue().getParkAddress());
        assertEquals("入库备注", frCaptor.getValue().getParkRemark());
        assertEquals(1L, frCaptor.getValue().getDispatchOrderId());
        assertEquals(50L, frCaptor.getValue().getAssignmentId());
        assertNotNull(frCaptor.getValue().getParkSubmittedAt());
    }

    @Test
    void detainInRejectsPlateNotInOrderAccidentVehicles() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));
        DispatchAccidentVehicle only = new DispatchAccidentVehicle();
        only.setDispatchOrderId(1L);
        only.setPlateNo("粤B11111");
        when(accidentVehicleMapper.findByOrderId(1L)).thenReturn(List.of(only));

        DetainInRequest req = new DetainInRequest();
        req.setPlateNo("粤B99999");
        req.setParkingLotId(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.detainIn(9L, 1L, req));

        assertTrue(ex.getMessage().contains("事故车"));
        verifyNoInteractions(detainedVehicleService);
    }

    @Test
    void detainInFailsWhenAssignmentNotAccepted() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("DISPATCHED"));

        DetainInRequest req = new DetainInRequest();
        req.setPlateNo("粤B11111");
        req.setParkingLotId(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.detainIn(9L, 1L, req));

        assertTrue(ex.getMessage().contains("接单"));
        verifyNoInteractions(detainedVehicleService);
    }

    @Test
    void getTaskDetainReturnsInYardRecord() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));
        DetainedVehicle dv = new DetainedVehicle();
        dv.setId(33L);
        dv.setDispatchOrderId(1L);
        dv.setStatus("IN_YARD");
        when(detainedVehicleMapper.findInYardByDispatchOrderId(1L)).thenReturn(dv);

        DetainedVehicle found = service.getTaskDetain(9L, 1L);

        assertNotNull(found);
        assertEquals(33L, found.getId());
    }

    @Test
    void getTaskDetainReturnsNullWhenMissing() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("COMPLETED"));
        when(detainedVehicleMapper.findInYardByDispatchOrderId(1L)).thenReturn(null);

        assertNull(service.getTaskDetain(9L, 1L));
    }

    @Test
    void addDetainMediaRejectsDetainOfOtherRescuer() {
        DetainedVehicle dv = new DetainedVehicle();
        dv.setId(33L);
        dv.setDispatchOrderId(1L);
        when(detainedVehicleMapper.findById(33L)).thenReturn(dv);
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.addDetainMedia(9L, 33L, "PARK", file));

        assertTrue(ex.getMessage().contains("无权"));
        verifyNoInteractions(fileStorageService);
        verify(detainMediaMapper, never()).insert(any());
    }

    @Test
    void addDetainMediaRejectsSceneBizType() {
        DetainedVehicle dv = new DetainedVehicle();
        dv.setId(33L);
        dv.setDispatchOrderId(1L);
        when(detainedVehicleMapper.findById(33L)).thenReturn(dv);
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.addDetainMedia(9L, 33L, "SCENE", file));

        assertTrue(ex.getMessage().contains("PARK"));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void addDetainMediaStoresParkPhoto() throws Exception {
        DetainedVehicle dv = new DetainedVehicle();
        dv.setId(33L);
        dv.setDispatchOrderId(1L);
        when(detainedVehicleMapper.findById(33L)).thenReturn(dv);
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));
        when(fileStorageService.storeDetainMedia(eq(33L), any())).thenReturn("detain/33/a.jpg");
        when(detainMediaMapper.insert(any(DetainMedia.class))).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});

        DetainMedia media = service.addDetainMedia(9L, 33L, "PARK", file);

        assertEquals("PARK", media.getBizType());
        assertEquals("detain/33/a.jpg", media.getFilePath());
        assertEquals(33L, media.getDetainId());
        assertEquals(9L, media.getUploadedBy());
        verify(detainMediaMapper).insert(any(DetainMedia.class));
    }

    @Test
    void listEnabledParkingLotsReadsFromServiceResult() {
        ParkingLot lot = new ParkingLot();
        lot.setId(2L);
        lot.setName("福田停车场");
        when(parkingLotService.list(isNull(), eq("ENABLED"), any(PageParams.class)))
                .thenReturn(Map.of("list", List.of(lot)));

        List<ParkingLot> lots = service.listEnabledParkingLots();

        assertEquals(1, lots.size());
        assertEquals(2L, lots.get(0).getId());
    }
```

- [ ] **Step 3: 运行测试确认失败**

Run: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=RescuerMobileServiceTest test`
Expected: 编译失败 —— `cannot find symbol: method detainIn(...)` / `getTaskDetain(...)` / `addDetainMedia(...)` / `listEnabledParkingLots()`。

- [ ] **Step 4: 实现 Service**

在 `RescuerMobileService` 的 import 区追加：

```java
import com.example.backend.common.PageParams;
import com.example.backend.dto.DetainInRequest;
import com.example.backend.entity.DetainMedia;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.DispatchAccidentVehicle;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainMediaMapper;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.util.PlateNos;
import java.util.ArrayList;
import java.util.Map;
```

在字段区追加：

```java
    @Autowired
    private DetainedVehicleService detainedVehicleService;

    @Autowired
    private DetainedVehicleMapper detainedVehicleMapper;

    @Autowired
    private DetainMediaMapper detainMediaMapper;

    @Autowired
    private ParkingLotService parkingLotService;

    @Autowired
    private ParkingAreaService parkingAreaService;
```

在 `savePark` 方法之后插入：

```java
    @Transactional
    public DetainedVehicle detainIn(Long userId, Long orderId, DetainInRequest req) {
        if (req == null) {
            throw new RuntimeException("入库参数无效");
        }
        DispatchVehicleAssignment assignment = requireAssignmentAccepted(orderId, userId);
        DispatchOrder order = dispatchOrderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        String plate = req.getPlateNo() == null ? "" : req.getPlateNo().trim();
        if (plate.isEmpty()) {
            throw new RuntimeException("车牌不能为空");
        }
        if (!matchesAccidentVehicle(orderId, plate)) {
            throw new RuntimeException("车牌与工单事故车不一致");
        }
        User user = userMapper.findById(userId);
        String rescuerName = null;
        if (user != null) {
            rescuerName = user.getRealName() != null && !user.getRealName().isBlank()
                    ? user.getRealName() : user.getUsername();
        }
        req.setDispatchOrderId(orderId);
        req.setRescuerName(rescuerName);

        DetainedVehicle created = detainedVehicleService.checkIn(req, userId);

        DispatchFieldRecord record = getOrCreateRecord(orderId, assignment.getId());
        record.setParkAddress(buildParkAddress(req));
        record.setParkRemark(req.getRemark());
        record.setParkSubmittedAt(LocalDateTime.now());
        upsert(record);
        return created;
    }

    public DetainedVehicle getTaskDetain(Long userId, Long orderId) {
        requireAssignment(orderId, userId);
        return detainedVehicleMapper.findInYardByDispatchOrderId(orderId);
    }

    public List<ParkingLot> listEnabledParkingLots() {
        PageParams pp = PageParams.normalize(1, 100);
        Map<String, Object> result = parkingLotService.list(null, "ENABLED", pp);
        Object rows = result == null ? null : result.get("list");
        if (rows instanceof List<?> list) {
            List<ParkingLot> lots = new ArrayList<>();
            for (Object row : list) {
                if (row instanceof ParkingLot lot) {
                    lots.add(lot);
                }
            }
            return lots;
        }
        return List.of();
    }

    public List<ParkingArea> listParkingAreas(Long lotId) {
        return parkingAreaService.list(lotId, "ENABLED");
    }

    @Transactional
    public DetainMedia addDetainMedia(Long userId, Long detainId, String bizType, MultipartFile file) {
        DetainedVehicle dv = detainedVehicleMapper.findById(detainId);
        if (dv == null) {
            throw new RuntimeException("扣留车辆不存在");
        }
        if (dv.getDispatchOrderId() == null) {
            throw new RuntimeException("扣留车辆未关联工单");
        }
        requireAssignmentAccepted(dv.getDispatchOrderId(), userId);
        if (!"PARK".equals(bizType)) {
            throw new RuntimeException("媒体类型无效，仅支持 PARK");
        }
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请上传文件");
        }
        String relativePath;
        try {
            relativePath = fileStorageService.storeDetainMedia(detainId, file);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败");
        }
        DetainMedia media = new DetainMedia();
        media.setDetainId(detainId);
        media.setBizType("PARK");
        media.setFilePath(relativePath);
        media.setSortOrder(0);
        media.setUploadedBy(userId);
        detainMediaMapper.insert(media);
        return media;
    }

    private boolean matchesAccidentVehicle(Long orderId, String plate) {
        List<DispatchAccidentVehicle> vehicles = accidentVehicleMapper.findByOrderId(orderId);
        if (vehicles == null || vehicles.isEmpty()) {
            return false;
        }
        String needle = PlateNos.normalize(plate);
        for (DispatchAccidentVehicle av : vehicles) {
            if (av != null && needle.equals(PlateNos.normalize(av.getPlateNo()))) {
                return true;
            }
        }
        return false;
    }

    private String buildParkAddress(DetainInRequest req) {
        ParkingLot lot = parkingLotService.findById(req.getParkingLotId());
        StringBuilder sb = new StringBuilder(lot != null && lot.getName() != null ? lot.getName() : "停车场");
        if (req.getParkingAreaId() != null) {
            ParkingArea area = parkingAreaService.findById(req.getParkingAreaId());
            if (area != null && area.getName() != null) {
                sb.append('/').append(area.getName());
            }
        }
        if (req.getStallNo() != null && !req.getStallNo().isBlank()) {
            sb.append(" 车位").append(req.getStallNo().trim());
        }
        return sb.toString();
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -Dtest=RescuerMobileServiceTest test`
Expected: BUILD SUCCESS，13 个用例全绿。

- [ ] **Step 6: 提交**

```powershell
git add backend/src/main/java/com/example/backend/mapper/DetainedVehicleMapper.java backend/src/main/java/com/example/backend/service/RescuerMobileService.java backend/src/test/java/com/example/backend/service/RescuerMobileServiceTest.java
git commit -m "feat(rescuer): add detain-in, lookup and park photo services"
```

---

### Task 3: 后端 — 救援员移动端入库接口

**Files:**
- Modify: `backend/src/main/java/com/example/backend/controller/RescuerMobileController.java`

**Interfaces:**
- Consumes: Task 2 的 5 个 Service 方法（签名见 Task 2 的 Produces）。
- Produces:
  - `GET /api/mobile/rescuer/parking-lots` → `Result<List<ParkingLot>>`
  - `GET /api/mobile/rescuer/parking-lots/{lotId}/areas` → `Result<List<ParkingArea>>`
  - `POST /api/mobile/rescuer/tasks/{id}/detain-in` → `Result<DetainedVehicle>`
  - `GET /api/mobile/rescuer/tasks/{id}/detain` → `Result<DetainedVehicle>`（无记录时 `data` 为 `null`）
  - `POST /api/mobile/rescuer/detain/{detainId}/media` → `Result<DetainMedia>`

- [ ] **Step 1: 追加 import**

在 `RescuerMobileController` 的 import 区加入：

```java
import com.example.backend.dto.DetainInRequest;
import com.example.backend.entity.DetainMedia;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
```

- [ ] **Step 2: 在 `savePark` 方法之后插入接口**

```java
    @GetMapping("/parking-lots")
    @PreAuthorize("hasAuthority('rescuer:park')")
    public Result<List<ParkingLot>> listParkingLots() {
        try {
            return Result.success(rescuerMobileService.listEnabledParkingLots());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/parking-lots/{lotId}/areas")
    @PreAuthorize("hasAuthority('rescuer:park')")
    public Result<List<ParkingArea>> listParkingAreas(@PathVariable Long lotId) {
        try {
            return Result.success(rescuerMobileService.listParkingAreas(lotId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/detain-in")
    @PreAuthorize("hasAuthority('rescuer:park')")
    public Result<DetainedVehicle> detainIn(@PathVariable Long id, @RequestBody DetainInRequest request) {
        try {
            return Result.success(rescuerMobileService.detainIn(currentUserId(), id, request));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/tasks/{id}/detain")
    @PreAuthorize("hasAuthority('rescuer:park')")
    public Result<DetainedVehicle> getTaskDetain(@PathVariable Long id) {
        try {
            return Result.success(rescuerMobileService.getTaskDetain(currentUserId(), id));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/detain/{detainId}/media")
    @PreAuthorize("hasAuthority('rescuer:park')")
    public Result<DetainMedia> addDetainMedia(@PathVariable Long detainId,
                                              @RequestParam MultipartFile file,
                                              @RequestParam String bizType) {
        try {
            return Result.success(rescuerMobileService.addDetainMedia(currentUserId(), detainId, bizType, file));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
```

- [ ] **Step 3: 编译校验**

Run: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q -DskipTests compile`
Expected: BUILD SUCCESS，无编译错误。

- [ ] **Step 4: 提交**

```powershell
git add backend/src/main/java/com/example/backend/controller/RescuerMobileController.java
git commit -m "feat(rescuer): expose mobile detain-in endpoints"
```

---

### Task 4: 数据库 — 权限迁移

**Files:**
- Create: `database/migrate_2026-10-27_detain_in_to_rescuer.sql`
- Modify: `database/init.sql:474-476`

**Interfaces:**
- Consumes: 现有 `permission` 表（id 48 = `detain:add`，id 61 = `rescuer:park`）与 `role_permission` 表。
- Produces: 运行后 `PARKING_ADMIN(4)` 不再拥有 `detain:add`，`TOW_DRIVER(3)` 仍拥有 `rescuer:park`。

- [ ] **Step 1: 新建迁移脚本**

`database/migrate_2026-10-27_detain_in_to_rescuer.sql`：

```sql
-- 入库改由救援员移动端填写
-- 1) 停车场管理员不再拥有扣留入库权限（48 = detain:add）
DELETE FROM `role_permission` WHERE `role_id` = 4 AND `permission_id` = 48;

-- 2) 救援员保留 rescuer:park（61 = 入库登记），缺失时补上
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, p.id FROM `permission` p
WHERE p.`permission` = 'rescuer:park'
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp WHERE rp.`role_id` = 3 AND rp.`permission_id` = p.id
  );
```

- [ ] **Step 2: 同步 init.sql**

把 `database/init.sql` 中：

```sql
-- PARKING_ADMIN 拥有停车场与扣留车辆管理
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 4, id FROM `permission` WHERE id = 19 OR id BETWEEN 42 AND 52;
```

改为：

```sql
-- PARKING_ADMIN 拥有停车场与扣留车辆管理（扣留入库 48 已移至救援员移动端）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 4, id FROM `permission` WHERE id = 19 OR (id BETWEEN 42 AND 52 AND id <> 48);
```

- [ ] **Step 3: 在本地库执行迁移**

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -uroot -pminth@888 --protocol=tcp -h127.0.0.1 -P3306 vue_springboot_system -e "source database/migrate_2026-10-27_detain_in_to_rescuer.sql"
```

- [ ] **Step 4: 验证权限结果**

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -uroot -pminth@888 --protocol=tcp -h127.0.0.1 -P3306 vue_springboot_system -e "SELECT r.role_id, p.permission FROM role_permission r JOIN permission p ON p.id = r.permission_id WHERE (r.role_id = 4 AND p.permission = 'detain:add') OR (r.role_id = 3 AND p.permission = 'rescuer:park');"
```

Expected: 仅返回一行 `3  rescuer:park`；`4 detain:add` 无结果。

- [ ] **Step 5: 提交**

```powershell
git add database/migrate_2026-10-27_detain_in_to_rescuer.sql database/init.sql
git commit -m "chore(db): move detain-in permission to rescuer mobile"
```

---

### Task 5: 移动端 — API 客户端

**Files:**
- Modify: `mobile-rescuer/src/api/rescuer.js`（文件末尾追加）
- Modify: `mobile-rescuer/src/api/detain.js`（删除已废弃的三个函数）

**Interfaces:**
- Consumes: Task 3 的 5 个 HTTP 接口。
- Produces（Task 6 依赖）:
  - `listRescuerParkingLots()` → `Promise<Result<ParkingLot[]>>`
  - `listRescuerParkingAreas(lotId)` → `Promise<Result<ParkingArea[]>>`
  - `detainInTask(id, data)` → `Promise<Result<DetainedVehicle>>`
  - `getTaskDetain(id)` → `Promise<Result<DetainedVehicle|null>>`
  - `uploadDetainPhoto(detainId, filePath)` → `Promise<Result<DetainMedia>>`

- [ ] **Step 1: 追加 rescuer API**

在 `mobile-rescuer/src/api/rescuer.js` 末尾追加：

```js
export function listRescuerParkingLots() {
  return request({ url: `${PREFIX}/parking-lots` })
}

export function listRescuerParkingAreas(lotId) {
  return request({ url: `${PREFIX}/parking-lots/${lotId}/areas` })
}

export function detainInTask(id, data) {
  return request({ url: `${PREFIX}/tasks/${id}/detain-in`, method: 'POST', data })
}

export function getTaskDetain(id) {
  return request({ url: `${PREFIX}/tasks/${id}/detain` })
}

export function uploadDetainPhoto(detainId, filePath) {
  return uploadFile({
    url: `${PREFIX}/detain/${detainId}/media`,
    filePath,
    name: 'file',
    formData: { bizType: 'PARK' }
  })
}
```

- [ ] **Step 2: 删除废弃的 detain API**

在 `mobile-rescuer/src/api/detain.js` 中删除 `checkInDetain`、`listActiveOrders`、`uploadDetainMedia` 三个导出函数（其余 `listDetains`、`getDetain`、`checkOutDetain`、`listDetainMedia` 保留）。删除后文件内容应为：

```js
import { request } from '../utils/request'

export function listDetains(params) {
  return request({ url: '/detain/list', data: params })
}

export function getDetain(id) {
  return request({ url: `/detain/${id}` })
}

export function checkOutDetain(id) {
  return request({ url: `/detain/${id}/out`, method: 'POST' })
}

export function listDetainMedia(id) {
  return request({ url: `/detain/${id}/media` })
}
```

注意：`uploadFile` 的 import 也一并去掉（第 1 行改为 `import { request } from '../utils/request'`）。

- [ ] **Step 3: 提交**

```powershell
git add mobile-rescuer/src/api/rescuer.js mobile-rescuer/src/api/detain.js
git commit -m "feat(mobile): add rescuer detain-in api client"
```

（此时删除 `checkin.vue` 尚未完成，`mobile-rescuer` 构建会因 `checkin.vue` 仍引用被删函数而失败，这是预期的；Task 7 会处理，构建校验放到 Task 7 一起做。）

---

### Task 6: 移动端 — 重写 `pages/task/park.vue` 为三步入库

**Files:**
- Modify: `mobile-rescuer/src/pages/task/park.vue`（整文件替换）

**Interfaces:**
- Consumes: `getTask`、`listMedia`、`uploadMedia`（`api/rescuer.js` 既有）、Task 5 的 5 个新函数、`getMe`（`api/auth.js`）、`mediaUrl`（`utils/request.js`）、`requirePageAccess`（`utils/guard.js`）。
- Produces: 路由 `/pages/task/park?id=<orderId>` 的三步入库页；已入库时展示只读摘要。

- [ ] **Step 1: 整体替换文件**

`mobile-rescuer/src/pages/task/park.vue` 完整内容：

```vue
<template>
  <view class="page">
    <view v-if="existing" class="card">
      <view class="section-title">该车辆已入库</view>
      <view class="line">扣押编号：{{ existing.detainNo || '-' }}</view>
      <view class="line">入场编号：{{ existing.entryNo || '-' }}</view>
      <view class="line">车牌：{{ existing.plateNo || '-' }}</view>
      <view class="line">停放场地：{{ existingLotName || '-' }}</view>
      <view class="line">入库时间：{{ formatTime(existing.inTime) }}</view>
      <view class="btn-ghost" style="margin-top: 24rpx" @click="goBack">返回</view>
    </view>

    <view v-else>
      <view class="stepper">
        <view class="step" :class="{ done: step > 1, current: step === 1 }">
          <view class="dot">{{ step > 1 ? '✓' : '1' }}</view>
          <text class="step-title">车辆信息</text>
          <text class="step-sub">待录入</text>
        </view>
        <view class="bar" :class="{ on: step > 1 }" />
        <view class="step" :class="{ done: step > 2, current: step === 2 }">
          <view class="dot">{{ step > 2 ? '✓' : '2' }}</view>
          <text class="step-title">施救信息</text>
          <text class="step-sub">待录入</text>
        </view>
        <view class="bar" :class="{ on: step > 2 }" />
        <view class="step" :class="{ current: step === 3 }">
          <view class="dot">3</view>
          <text class="step-title">停放信息</text>
          <text class="step-sub">待录入</text>
        </view>
      </view>

      <view v-show="step === 1" class="card">
        <view class="field">
          <view class="field-label">扣押编码 *</view>
          <input class="field-input" v-model="form.detainNo" placeholder="请填写强制扣押凭证编码" />
        </view>
        <view class="field">
          <view class="field-label">车牌号码 *</view>
          <view class="plate-row">
            <input class="field-input plate-input" v-model="form.plateNo" placeholder="请填写车牌号码" />
            <text v-if="accidentVehicles.length > 1" class="link" @click="vehiclePickerVisible = true">选择事故车</text>
          </view>
          <view v-if="accidentVehicles.length" class="hint">
            工单事故车：{{ accidentPlates }}
          </view>
        </view>
        <view class="field">
          <view class="field-label">厂牌型号</view>
          <input class="field-input" v-model="form.brandModel" placeholder="请填写厂牌型号" />
        </view>
        <view class="field">
          <view class="field-label">车辆类型</view>
          <input class="field-input" v-model="form.vehicleType" placeholder="请填写车辆类型" />
        </view>
        <view class="field">
          <view class="field-label">车辆颜色</view>
          <picker mode="selector" :range="colorNames" @change="onColorPick">
            <view class="field-input picker-value" :class="{ placeholder: !form.vehicleColor }">
              {{ form.vehicleColor || '请选择车辆颜色' }}
            </view>
          </picker>
        </view>
        <view class="field">
          <view class="field-label">行驶里程</view>
          <input class="field-input" v-model="form.mileage" placeholder="请填写行驶里程" />
        </view>
        <view class="field">
          <view class="field-label">重要装备</view>
          <input class="field-input" v-model="form.importantEquipment" placeholder="无" />
        </view>
        <view class="field">
          <view class="field-label">有无钥匙</view>
          <view class="radios">
            <view class="radio" :class="{ on: form.hasKey === 'YES' }" @click="form.hasKey = 'YES'">有</view>
            <view class="radio" :class="{ on: form.hasKey === 'NO' }" @click="form.hasKey = 'NO'">无</view>
          </view>
        </view>
      </view>

      <view v-show="step === 2" class="card">
        <view class="field">
          <view class="field-label">扣押部门</view>
          <input class="field-input" v-model="form.detainDept" placeholder="请填写扣押部门" />
        </view>
        <view class="field">
          <view class="field-label">施救人员</view>
          <view class="field-input picker-value">{{ form.rescuerName || '当前账号' }}</view>
        </view>
        <view class="field">
          <view class="field-label">施救原因</view>
          <view class="radios">
            <view
              v-for="r in reasons"
              :key="r.value"
              class="radio"
              :class="{ on: form.rescueReason === r.value }"
              @click="form.rescueReason = r.value"
            >{{ r.label }}</view>
          </view>
        </view>
        <view class="field">
          <view class="field-label">施救方式</view>
          <input class="field-input" v-model="form.rescueMethod" placeholder="请填写施救方式" />
        </view>
        <view class="field">
          <view class="field-label">施救时间</view>
          <input class="field-input" v-model="form.rescueTime" placeholder="yyyy-MM-dd HH:mm:ss" />
        </view>
        <view class="field">
          <view class="field-label">施救地点</view>
          <view class="loc-row">
            <input class="field-input loc-input" v-model="form.rescueAddress" placeholder="请填写施救地址" />
            <view class="loc-btn" @click="fillLocation">定位</view>
          </view>
        </view>
        <view class="field">
          <view class="field-label">现场照片</view>
          <view class="media-grid">
            <image
              v-for="(m, i) in damagePhotos"
              :key="(m && m.id) || 'd' + i"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewServer(i)"
            />
            <view class="thumb add" @click="pickScenePhoto">点击上传</view>
          </view>
          <view class="hint">现场照片同时用于工单现场采集，入库后将复制为扣车现场照片</view>
        </view>
      </view>

      <view v-show="step === 3" class="card">
        <view class="field">
          <view class="field-label">入场编号</view>
          <view class="field-input picker-value placeholder">提交后生成</view>
        </view>
        <view class="field">
          <view class="field-label">停放场地 *</view>
          <picker
            mode="selector"
            :range="lotNames"
            :value="lotIndex"
            :disabled="!lots.length"
            @change="onLotChange"
          >
            <view class="field-input picker-value" :class="{ placeholder: !selectedLotName }">
              {{ selectedLotName || '请选择停车场' }}
            </view>
          </picker>
        </view>
        <view class="field">
          <view class="field-label">停放区域</view>
          <picker
            mode="selector"
            :range="areaNames"
            :value="areaIndex"
            :disabled="!areas.length"
            @change="onAreaChange"
          >
            <view class="field-input picker-value" :class="{ placeholder: !selectedAreaName }">
              {{ selectedAreaName || (areas.length ? '请选择停放区域' : '暂无区域') }}
            </view>
          </picker>
        </view>
        <view class="field">
          <view class="field-label">车位编号</view>
          <input class="field-input" v-model="form.stallNo" placeholder="请填写车位编号" />
        </view>
        <view class="field">
          <view class="field-label">停放照片</view>
          <view class="media-grid">
            <image
              v-for="(p, i) in parkPhotos"
              :key="'p' + i"
              class="thumb"
              :src="p"
              mode="aspectFill"
              @click="previewLocal(parkPhotos, i)"
            />
            <view class="thumb add" @click="pickParkPhoto">点击上传</view>
          </view>
        </view>
        <view class="field">
          <view class="field-label">停放位置说明 / 备注</view>
          <textarea class="field-input area" v-model="form.remark" placeholder="入库备注" />
        </view>
      </view>

      <view class="btn-primary" :class="{ 'btn-disabled': submitting }" @click="onPrimary">
        {{ primaryLabel }}
      </view>
    </view>

    <view v-if="vehiclePickerVisible" class="picker-mask" @click.self="vehiclePickerVisible = false">
      <view class="picker-sheet" @click.stop>
        <view class="picker-title">选择事故车辆</view>
        <view
          v-for="v in accidentVehicles"
          :key="v.id"
          class="picker-item"
          @click="selectVehicle(v)"
        >
          <text>{{ v.plateNo }}</text>
          <text class="picker-sub">{{ v.vehicleTypeName || '—' }}</text>
        </view>
        <view class="picker-cancel" @click="vehiclePickerVisible = false">取消</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getTask,
  listMedia,
  uploadMedia,
  listRescuerParkingLots,
  listRescuerParkingAreas,
  detainInTask,
  getTaskDetain,
  uploadDetainPhoto
} from '../../api/rescuer'
import { getMe } from '../../api/auth'
import { mediaUrl } from '../../utils/request'
import { requirePageAccess } from '../../utils/guard.js'

const reasons = [
  { label: '事故', value: 'ACCIDENT' },
  { label: '违法', value: 'ILLEGAL' },
  { label: '救援', value: 'RESCUE' }
]
const colorNames = ['黑', '白', '银', '灰', '红', '蓝', '绿', '黄', '其他']

const id = ref(null)
const accidentVehicles = ref([])
const step = ref(1)
const existing = ref(null)
const lots = ref([])
const areas = ref([])
const damagePhotos = ref([])
const parkPhotos = ref([])
const submitting = ref(false)
const vehiclePickerVisible = ref(false)

const form = reactive({
  detainNo: '',
  plateNo: '',
  brandModel: '',
  vehicleType: '',
  vehicleColor: '',
  mileage: '',
  importantEquipment: '无',
  hasKey: '',
  detainDept: '',
  rescuerName: '',
  rescueReason: 'ACCIDENT',
  rescueMethod: '',
  rescueTime: formatNow(),
  rescueAddress: '',
  parkingLotId: '',
  parkingAreaId: '',
  stallNo: '',
  remark: ''
})

const accidentPlates = computed(() =>
  accidentVehicles.value.map((v) => v.plateNo).filter(Boolean).join('、')
)
const lotNames = computed(() => lots.value.map((lot) => lot.name || `停车场#${lot.id}`))
const lotIndex = computed(() => {
  const idx = lots.value.findIndex((lot) => String(lot.id) === String(form.parkingLotId))
  return idx < 0 ? 0 : idx
})
const selectedLotName = computed(() => {
  const lot = lots.value.find((row) => String(row.id) === String(form.parkingLotId))
  return lot ? (lot.name || `停车场#${lot.id}`) : ''
})
const existingLotName = computed(() => {
  if (!existing.value) return ''
  const lot = lots.value.find((row) => String(row.id) === String(existing.value.parkingLotId))
  return lot ? (lot.name || `停车场#${lot.id}`) : ''
})
const areaNames = computed(() => areas.value.map((a) => a.name || `区域#${a.id}`))
const areaIndex = computed(() => {
  const idx = areas.value.findIndex((a) => String(a.id) === String(form.parkingAreaId))
  return idx < 0 ? 0 : idx
})
const selectedAreaName = computed(() => {
  const area = areas.value.find((row) => String(row.id) === String(form.parkingAreaId))
  return area ? area.name : ''
})
const primaryLabel = computed(() => {
  if (submitting.value) return '提交中…'
  if (step.value < 3) return '下一步'
  return '确定提交'
})

function formatNow() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onLoad(async (q) => {
  if (!requirePageAccess('rescuer')) return
  id.value = q.id
  await Promise.all([loadTask(), loadLots(), loadMe(), loadExisting()])
})

async function loadTask() {
  try {
    const res = await getTask(id.value)
    const o = res.data?.order || null
    accidentVehicles.value = o?.accidentVehicles || []
    if (accidentVehicles.value.length) applyVehicle(accidentVehicles.value[0])
    if (o?.accidentAddress && !form.rescueAddress) form.rescueAddress = o.accidentAddress
    await loadDamage()
  } catch (_) {}
}

function applyVehicle(v) {
  if (!v) return
  form.plateNo = v.plateNo || ''
  if (v.vehicleTypeName) form.vehicleType = v.vehicleTypeName
}

function selectVehicle(v) {
  applyVehicle(v)
  vehiclePickerVisible.value = false
}

async function loadDamage() {
  try {
    const res = await listMedia(id.value)
    damagePhotos.value = (res.data || []).filter((m) => m.bizType === 'DAMAGE')
  } catch (_) {
    damagePhotos.value = []
  }
}

async function loadMe() {
  try {
    const res = await getMe()
    const data = res.data || {}
    form.rescuerName = data.realName || data.username || ''
  } catch (_) {}
}

async function loadExisting() {
  try {
    const res = await getTaskDetain(id.value)
    existing.value = res.data || null
  } catch (_) {
    existing.value = null
  }
}

async function loadLots() {
  try {
    const res = await listRescuerParkingLots()
    lots.value = res.data || []
  } catch (_) {
    lots.value = []
    uni.showToast({ title: '停车场列表加载失败', icon: 'none' })
  }
}

async function loadAreas(lotId) {
  areas.value = []
  form.parkingAreaId = ''
  if (!lotId) return
  try {
    const res = await listRescuerParkingAreas(lotId)
    areas.value = res.data || []
  } catch (_) {
    areas.value = []
  }
}

function onColorPick(e) {
  const i = Number(e.detail.value)
  form.vehicleColor = colorNames[i] || ''
}

function onLotChange(e) {
  const idx = Number(e.detail.value)
  const lot = lots.value[idx]
  form.parkingLotId = lot ? String(lot.id) : ''
  loadAreas(lot ? lot.id : null)
}

function onAreaChange(e) {
  const idx = Number(e.detail.value)
  const area = areas.value[idx]
  form.parkingAreaId = area ? String(area.id) : ''
}

function pickScenePhoto() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      try {
        await uploadMedia(id.value, path, 'DAMAGE')
        uni.showToast({ title: '上传成功', icon: 'success' })
        await loadDamage()
      } catch (_) {}
    }
  })
}

function pickParkPhoto() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      parkPhotos.value = [...parkPhotos.value, path]
    }
  })
}

function previewLocal(list, index) {
  uni.previewImage({ urls: list, current: list[index] })
}

function previewServer(index) {
  const urls = damagePhotos.value.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: urls[index] })
}

function fillLocation() {
  const apply = (lng, lat) => {
    const text = `${lat.toFixed(6)},${lng.toFixed(6)}`
    form.rescueAddress = form.rescueAddress ? `${form.rescueAddress} ${text}` : text
  }
  if (typeof navigator !== 'undefined' && navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (pos) => apply(pos.coords.longitude, pos.coords.latitude),
      () => uni.showToast({ title: '定位失败', icon: 'none' }),
      { enableHighAccuracy: true, timeout: 10000 }
    )
    return
  }
  uni.getLocation({
    type: 'gcj02',
    success: (r) => apply(r.longitude, r.latitude),
    fail: () => uni.showToast({ title: '定位失败', icon: 'none' })
  })
}

function onPrimary() {
  if (submitting.value) return
  if (step.value === 1) {
    if (!String(form.detainNo || '').trim() || !String(form.plateNo || '').trim()) {
      uni.showToast({ title: '请填写扣押编码和车牌', icon: 'none' })
      return
    }
    step.value = 2
    return
  }
  if (step.value === 2) {
    step.value = 3
    return
  }
  onSubmit()
}

function toIso(value) {
  const text = String(value || '').trim()
  if (!text) return null
  return text.replace(' ', 'T')
}

function goBack() {
  uni.navigateBack()
}

async function onSubmit() {
  const plate = String(form.plateNo || '').trim()
  const detainNo = String(form.detainNo || '').trim()
  const lotId = Number(form.parkingLotId)
  if (!detainNo || !plate || !Number.isFinite(lotId) || !lotId) {
    uni.showToast({ title: '请填写扣押编码、车牌并选择停车场', icon: 'none' })
    return
  }
  const body = { detainNo, plateNo: plate, parkingLotId: lotId }
  if (form.parkingAreaId) body.parkingAreaId = Number(form.parkingAreaId)
  const put = (key, val) => {
    const s = String(val || '').trim()
    if (s) body[key] = s
  }
  put('vehicleType', form.vehicleType)
  put('brandModel', form.brandModel)
  put('vehicleColor', form.vehicleColor)
  put('mileage', form.mileage)
  put('importantEquipment', form.importantEquipment)
  put('hasKey', form.hasKey)
  put('detainDept', form.detainDept)
  put('rescueReason', form.rescueReason)
  put('rescueMethod', form.rescueMethod)
  put('rescueAddress', form.rescueAddress)
  put('stallNo', form.stallNo)
  put('remark', form.remark)
  const iso = toIso(form.rescueTime)
  if (iso) body.rescueTime = iso

  submitting.value = true
  try {
    const res = await detainInTask(id.value, body)
    const created = res.data || {}
    const detainId = created.id
    if (detainId) {
      for (const path of parkPhotos.value) {
        try {
          await uploadDetainPhoto(detainId, path)
        } catch (_) {}
      }
    }
    uni.showToast({ title: `已入库 ${created.detainNo || detainNo}`, icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (_) {
    submitting.value = false
  }
}
</script>

<style scoped>
.stepper {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12rpx 8rpx 28rpx;
}
.step {
  width: 140rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #999;
}
.step.current,
.step.done {
  color: #2979ff;
}
.dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  border: 2rpx solid #ccc;
  text-align: center;
  line-height: 44rpx;
  font-size: 24rpx;
  background: #fff;
  margin-bottom: 8rpx;
}
.step.current .dot,
.step.done .dot {
  border-color: #2979ff;
  background: #2979ff;
  color: #fff;
}
.step-title {
  font-size: 24rpx;
}
.step-sub {
  font-size: 20rpx;
  color: #bbb;
}
.bar {
  flex: 1;
  height: 4rpx;
  background: #ddd;
  margin-top: 24rpx;
}
.bar.on {
  background: #2979ff;
}
.picker-value.placeholder {
  color: #999;
  -webkit-text-fill-color: #999;
}
.radios {
  display: flex;
  gap: 24rpx;
}
.radio {
  padding: 12rpx 28rpx;
  border-radius: 8rpx;
  border: 1px solid #ddd;
  color: #666;
  font-size: 28rpx;
}
.radio.on {
  border-color: #2979ff;
  color: #2979ff;
  background: #e8f0ff;
}
.plate-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.plate-input {
  flex: 1;
}
.link {
  color: #2979ff;
  font-size: 26rpx;
  flex-shrink: 0;
}
.hint {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #999;
  line-height: 1.5;
}
.area {
  min-height: 140rpx;
}
.line {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #333;
}
.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.thumb {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #f0f3f8;
}
.thumb.add {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 24rpx;
  border: 1px dashed #ccc;
}
.loc-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.loc-input {
  flex: 1;
}
.loc-btn {
  color: #2979ff;
  padding: 20rpx 12rpx;
  font-size: 28rpx;
  flex-shrink: 0;
}
.picker-mask {
  position: fixed;
  left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0,0,0,0.4);
  z-index: 20;
  display: flex;
  align-items: flex-end;
}
.picker-sheet {
  width: 100%;
  background: #fff;
  border-radius: 16rpx 16rpx 0 0;
  padding: 24rpx 32rpx 48rpx;
}
.picker-title { font-size: 30rpx; font-weight: 600; margin-bottom: 16rpx; }
.picker-item {
  padding: 20rpx 0;
  border-bottom: 1px solid #eee;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.picker-sub { color: #999; font-size: 24rpx; }
.picker-cancel { text-align: center; color: #666; padding: 28rpx 0 8rpx; }
</style>
```

- [ ] **Step 2: 构建校验（此时会因 checkin.vue 报错，属预期）**

Run（在 `mobile-rescuer`）: `npm run build:h5`
Expected: FAIL，报错指向 `src/pages/detain/checkin.vue` 引用了不存在的导出（`checkInDetain` 等）。若报错出现在 `park.vue`，必须先修好 `park.vue` 再继续。

- [ ] **Step 3: 提交**

```powershell
git add mobile-rescuer/src/pages/task/park.vue
git commit -m "feat(mobile): rewrite park registration as 3-step detain-in"
```

---

### Task 7: 移动端 — 移除停车场管理员入库入口与旧页面

**Files:**
- Modify: `mobile-rescuer/src/components/DetainList.vue`
- Delete: `mobile-rescuer/src/pages/detain/checkin.vue`
- Modify: `mobile-rescuer/src/pages.json:80-84`

**Interfaces:**
- Consumes: Task 5 已删掉 `checkInDetain` / `listActiveOrders` / `uploadDetainMedia`。
- Produces: 扣车工作台仅保留「扫码」与查询；`/pages/detain/checkin` 路由不再存在。

- [ ] **Step 1: 删除 `DetainList.vue` 的入库按钮与权限判断**

模板头部改为：

```vue
    <view class="header">
      <view class="btn-ghost" @click="goScan">扫码</view>
    </view>
```

空态提示改为：

```vue
    <view v-else-if="!list.length" class="muted center">暂无在库车辆</view>
```

脚本中删除：
- `import { getUserState } from '../stores/user'` 这一行；
- `const canAdd = ref(false)`；
- `function refreshPerms() { ... }` 整个函数；
- `refreshPerms()` 的两处调用（模块顶层那一行，以及 `load()` 里的首行）；
- `function goCheckin() { ... }` 整个函数。

删除后 `script setup` 的响应式声明应为：

```js
const plateNo = ref('')
const detainNo = ref('')
const page = ref(1)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const loadFailed = ref(false)
```

`load()` 应以 `loading.value = true` 开头。

- [ ] **Step 2: 删除旧入库页并移除路由**

1. 删除文件 `mobile-rescuer/src/pages/detain/checkin.vue`（整文件）。
2. 从 `mobile-rescuer/src/pages.json` 中删除该对象：

```json
    {
      "path": "pages/detain/checkin",
      "style": {
        "navigationBarTitleText": "入库"
      }
    },
```

- [ ] **Step 3: 构建校验**

Run（在 `mobile-rescuer`）: `npm run build:h5`
Expected: 构建成功，无 `checkInDetain` / `checkin.vue` 相关报错。

- [ ] **Step 4: 顺手校验工作台入口**

Run: `rg -n "detain/checkin|checkInDetain|uploadDetainMedia|listActiveOrders" mobile-rescuer/src`
Expected: 无输出。

- [ ] **Step 5: 提交**

```powershell
git add mobile-rescuer/src/components/DetainList.vue mobile-rescuer/src/pages.json mobile-rescuer/src/pages/detain/checkin.vue
git commit -m "feat(mobile): remove parking admin detain-in entry"
```

---

### Task 8: PC — 移除扣留车辆「入库」按钮与新建弹窗

**Files:**
- Modify: `frontend/src/views/detain/DetainedVehicleList.vue`

**Interfaces:**
- Consumes: 既有 `updateDetain`、`listParkingAreas`、`listDetainMedia`。
- Produces: 扣留车辆页只剩编辑/出库/清理/打印；弹窗标题固定「编辑扣留车辆」。

- [ ] **Step 1: 移除顶部按钮并调整副标题**

模板头部改为：

```vue
    <div class="page-header">
      <div>
        <h1>扣留车辆</h1>
        <p class="subtitle">出库、清理与吊牌打印</p>
      </div>
    </div>
```

- [ ] **Step 2: 弹窗只保留编辑态**

把弹窗标题改为：

```vue
        <h2>编辑扣留车辆</h2>
```

把扣押编码两块 `v-if="!editingId"` / `v-else` 改为单块：

```vue
        <label>
          扣押编码
          <input :value="form.detainNo" disabled />
        </label>
```

把入场编号改为：

```vue
        <label>
          入场编号
          <input :value="form.entryNo" disabled />
        </label>
```

- [ ] **Step 3: 清理脚本**

1. 删除 import 中的 `checkInDetain`（保留 `listDetains`、`updateDetain`、`checkOutDetain`、`clearDetain`、`listDetainMedia`）。
2. 删除 `openCreate()` 整个函数。
3. `onSubmit` 改为只走编辑：

```js
async function onSubmit() {
  formError.value = ''
  saving.value = true
  try {
    await updateDetain(editingId.value, payload())
    formVisible.value = false
    await loadList()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}
```

4. `payload()` 删除这三行：

```js
  if (!editingId.value) {
    data.detainNo = form.detainNo
  }
```

5. `formParkingOptions` 的 computed 改为（去掉“create: ENABLED only”分支，始终允许当前已停用停车场）：

```js
/** Edit: include current lot even if DISABLED; reject other disabled lots. */
const formParkingOptions = computed(() => {
  const enabled = enabledParkings.value
  if (!form.parkingLotId) {
    return enabled
  }
  const currentId = String(form.parkingLotId)
  if (enabled.some((lot) => String(lot.id) === currentId)) {
    return enabled
  }
  const current = parkingLots.value.find((lot) => String(lot.id) === currentId)
  return current ? [current, ...enabled] : enabled
})
```

- [ ] **Step 4: 构建校验**

Run（在 `frontend`）: `npm run build`
Expected: 构建成功，无 `checkInDetain` 未定义告警。

- [ ] **Step 5: 提交**

```powershell
git add frontend/src/views/detain/DetainedVehicleList.vue
git commit -m "feat(pc): remove detain check-in entry from admin list"
```

---

### Task 9: 全量验证

**Files:** 无改动（只跑校验与手工流程）。

**Interfaces:**
- Consumes: Task 1–8 全部改动。
- Produces: 可交付的验收证据（命令输出 + 手工流程记录）。

- [ ] **Step 1: 后端全量测试**

Run: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml -q test`
Expected: BUILD SUCCESS，无失败用例。

- [ ] **Step 2: 启动后端**

Run: `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\.tools\apache-maven\bin\mvn.cmd -f backend\pom.xml spring-boot:run`
Expected: 日志出现 `Tomcat started on port 8080 (http)`。

- [ ] **Step 3: 验证救援员接口权限边界**

登录 `towdriver`（密码 `admin123`）取 token 后：

```powershell
# 停车场下拉
curl.exe -s -H "Authorization: Bearer <TOW_TOKEN>" http://localhost:8080/api/mobile/rescuer/parking-lots
# 工单入库回显（无记录时应为 data:null）
curl.exe -s -H "Authorization: Bearer <TOW_TOKEN>" http://localhost:8080/api/mobile/rescuer/tasks/1/detain
```

Expected: 两个请求都返回 `{"code":200,...}`。

- [ ] **Step 4: 验证停车场管理员失去入库权限**

用 `parking`（停车场管理员）账号取 token：

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer <PARKING_TOKEN>" -H "Content-Type: application/json" -X POST -d '{"detainNo":"DV-T1","plateNo":"粤B00001","parkingLotId":1}' http://localhost:8080/api/detain
```

Expected: `403`。

- [ ] **Step 5: 手工走通救援员入库（浏览器）**

1. 打开移动端（`mobile-rescuer` dev server），用 `towdriver / admin123` 登录。
2. 进入一个 `ACCEPTED` 工单详情 → 点「入库登记」。
3. 第一步：扣押编码手填、车牌默认第一辆事故车；如工单多辆事故车点「选择事故车」切换。
4. 第二步：施救人员显示当前账号（只读），现场照片可上传。
5. 第三步：选择停车场/区域、车位、上传停放照片、填备注 → 提交。
6. 预期：toast「已入库 DVxxx」，返回详情页；再次进入「入库登记」显示只读摘要。
7. PC 打开「扣留车辆」：新记录状态「在库」，停车场/区域正确；照片含 SCENE（工单 DAMAGE 复制）与 PARK。
8. PC 工单详情「现场摘要」的停放地址显示「停车场/区域 车位X」。
9. 停车场管理员移动端工作台：无「入库」按钮，仍可查看/出库/清理。
10. PC「扣留车辆」页：无「入库」按钮，仅编辑/出库/清理/打印可用。

- [ ] **Step 6: 记录结果并提交（如有需修的内容）**

```powershell
git status
```

Expected: 无未提交的功能代码；若 Step 5 暴露问题，修复后按对应模块的提交文案追加提交。

---

## Self-Review

**1. Spec coverage**

| Spec 内容 | 对应 Task |
|-----------|-----------|
| `GET /parking-lots` | Task 2（Service）+ Task 3（接口） |
| `GET /parking-lots/{lotId}/areas` | Task 2 + Task 3 |
| `POST /tasks/{id}/detain-in` | Task 1（车牌校验）+ Task 2 + Task 3 |
| `GET /tasks/{id}/detain` 回显 | Task 2 + Task 3 + Task 6（只读分支） |
| `POST /detain/{detainId}/media`（仅 PARK） | Task 2 + Task 3 + Task 6（提交后上传） |
| 回写 `dispatch_field_record`（parkAddress/parkRemark/parkSubmittedAt） | Task 2（`detainIn` + `buildParkAddress`） |
| 强制 `dispatchOrderId` 与 `rescuerName` | Task 2（`detainIn`）+ 单测 |
| 权限迁移（移除 PARKING_ADMIN 的 detain:add） | Task 4 |
| 移动端三步表单 + 事故车选择 + 施救人默认 | Task 6 |
| 删除停车场管理员入口 | Task 7 |
| 删除 PC 入库 | Task 8 |
| 测试与手工验证 | Task 1/2 单测 + Task 9 |
| 「明确不做」项 | 计划中未出现自动编码、批量入库、备用入口、PC 入库改造 |

**2. Placeholder scan**

已检查：无 TBD / TODO / “类似 Task N” / “适当处理错误”。所有代码块均为可直接粘贴的完整实现；所有命令都带期望输出。

**3. Type consistency**

- `detainIn(Long, Long, DetainInRequest) : DetainedVehicle` —— Task 2 定义，Task 3 调用，Task 6 对应 `detainInTask(id, data)`。
- `getTaskDetain(Long, Long) : DetainedVehicle` —— Task 2/3 一致；Task 6 用 `res.data || null`。
- `listEnabledParkingLots() : List<ParkingLot>`、`listParkingAreas(Long) : List<ParkingArea>` —— Task 2/3/6 一致。
- `addDetainMedia(Long, Long, String, MultipartFile) : DetainMedia` —— Task 2/3 一致；Task 6 通过 `uploadDetainPhoto(detainId, filePath)` 调用。
- `findInYardByDispatchOrderId(Long) : DetainedVehicle` —— Task 2 定义并在 `getTaskDetain` 使用。
