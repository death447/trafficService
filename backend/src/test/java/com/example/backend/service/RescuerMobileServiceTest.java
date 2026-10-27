package com.example.backend.service;

import com.example.backend.dto.BindVehicleRequest;
import com.example.backend.dto.DetainInRequest;
import com.example.backend.dto.LocationReportRequest;
import com.example.backend.dto.LocationReportResponse;
import com.example.backend.dto.SceneRequest;
import com.example.backend.entity.DetainMedia;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.DispatchAccidentVehicle;
import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchOrderEvaluation;
import com.example.backend.entity.DispatchVehicleAssignment;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.User;
import com.example.backend.mapper.DetainMediaMapper;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.DispatchVehicleAssignmentMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import com.example.backend.support.EntryNoGenerator;
import com.example.backend.common.PageParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescuerMobileServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock DispatchVehicleAssignmentMapper assignmentMapper;
    @Mock com.example.backend.mapper.DispatchAccidentVehicleMapper accidentVehicleMapper;
    @Mock DispatchFieldRecordMapper fieldRecordMapper;
    @Mock DispatchMediaMapper mediaMapper;
    @Mock DispatchOrderEvaluationMapper evaluationMapper;
    @Mock RescueVehicleMapper rescueVehicleMapper;
    @Mock UserMapper userMapper;
    @Mock LocalFileStorageService fileStorageService;
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @Mock DetainMediaMapper detainMediaMapper;
    @Mock DetainedVehicleService detainedVehicleService;
    @Mock ParkingLotService parkingLotService;
    @Mock ParkingAreaService parkingAreaService;
    @Mock EntryNoGenerator entryNoGenerator;
    @InjectMocks RescuerMobileService service;

    private DispatchVehicleAssignment assignment(String status) {
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setId(50L);
        a.setDispatchOrderId(1L);
        a.setVehicleId(5L);
        a.setRescuerId(9L);
        a.setStatus(status);
        return a;
    }

    @Test
    void bindVehicleParsesRvPayloadAndClearsOldBind() {
        RescueVehicle vehicle = new RescueVehicle();
        vehicle.setId(1L);
        vehicle.setPlateNo("粤B救援1");
        when(rescueVehicleMapper.findById(1L)).thenReturn(vehicle);
        when(rescueVehicleMapper.update(any())).thenReturn(1);

        BindVehicleRequest req = new BindVehicleRequest();
        req.setQrPayload("RV:1");
        RescueVehicle bound = service.bindVehicle(9L, req);

        verify(rescueVehicleMapper).clearDriverByUserId(9L);
        ArgumentCaptor<RescueVehicle> captor = ArgumentCaptor.forClass(RescueVehicle.class);
        verify(rescueVehicleMapper).update(captor.capture());
        assertEquals(9L, captor.getValue().getDriverUserId());
        assertEquals("IDLE", captor.getValue().getStatus());
        assertEquals(1L, bound.getId());
    }

    @Test
    void saveSceneFailsWhenNotAccepted() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("DISPATCHED"));

        SceneRequest req = new SceneRequest();
        req.setPlateNo("粤B12345");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.saveScene(9L, 1L, req));
        assertTrue(ex.getMessage().contains("接单") || ex.getMessage().contains("状态"));
        verify(fieldRecordMapper, never()).insert(any());
        verify(fieldRecordMapper, never()).update(any());
    }

    @Test
    void addMediaFailsOnIllegalBizType() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.addMedia(9L, 1L, "OTHER", file));
        assertTrue(ex.getMessage().contains("bizType") || ex.getMessage().contains("类型"));
        verifyNoInteractions(fileStorageService);
        verify(mediaMapper, never()).insert(any());
    }

    @Test
    void deleteMediaRemovesRecordAndFile() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));
        DispatchMedia media = new DispatchMedia();
        media.setId(5L);
        media.setDispatchOrderId(1L);
        media.setBizType("DAMAGE");
        media.setFilePath("dispatch/1/a.jpg");
        when(mediaMapper.findById(5L)).thenReturn(media);

        service.deleteMedia(9L, 1L, 5L);

        verify(fileStorageService).deleteDispatchMedia("dispatch/1/a.jpg");
        verify(mediaMapper).deleteById(5L);
    }

    @Test
    void deleteMediaFailsWhenNotAccepted() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("DISPATCHED"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteMedia(9L, 1L, 5L));
        assertTrue(ex.getMessage().contains("接单") || ex.getMessage().contains("状态"));
        verify(mediaMapper, never()).deleteById(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void reportLocationFailsWhenNotBound() {
        when(rescueVehicleMapper.findByDriverUserId(9L)).thenReturn(null);
        LocationReportRequest req = new LocationReportRequest();
        req.setLng(new BigDecimal("114.05"));
        req.setLat(new BigDecimal("22.54"));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.reportLocation(9L, req));
        assertTrue(ex.getMessage().contains("绑定"));
        verify(rescueVehicleMapper, never()).updateLocation(any(), any(), any(), any());
    }

    @Test
    void getTaskFillsAssignedVehicleWhenOrderHasVehicleId() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("DISPATCHED");
        order.setVehicleId(7L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        DispatchVehicleAssignment a = assignment("DISPATCHED");
        a.setVehicleId(7L);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(a);
        when(fieldRecordMapper.findByAssignmentId(50L)).thenReturn(null);
        when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);

        RescueVehicle vehicle = new RescueVehicle();
        vehicle.setId(7L);
        vehicle.setPlateNo("粤B救援1");
        vehicle.setLongitude(new BigDecimal("114.05"));
        vehicle.setLatitude(new BigDecimal("22.54"));
        vehicle.setLocationUpdatedAt(java.time.LocalDateTime.of(2026, 9, 9, 10, 0));
        when(rescueVehicleMapper.findById(7L)).thenReturn(vehicle);

        var detail = service.getTask(9L, 1L);

        assertNotNull(detail.getAssignedVehicle());
        assertEquals(7L, detail.getAssignedVehicle().getId());
        assertEquals("粤B救援1", detail.getAssignedVehicle().getPlateNo());
        assertEquals(0, new BigDecimal("114.05").compareTo(detail.getAssignedVehicle().getLongitude()));
        assertEquals(0, new BigDecimal("22.54").compareTo(detail.getAssignedVehicle().getLatitude()));
        assertNotNull(detail.getAssignedVehicle().getLocationUpdatedAt());
    }

    @Test
    void getTaskAssignedVehicleNullWhenVehicleMissing() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("ACCEPTED");
        order.setVehicleId(99L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        DispatchVehicleAssignment a = assignment("ACCEPTED");
        a.setVehicleId(99L);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(a);
        when(fieldRecordMapper.findByAssignmentId(50L)).thenReturn(null);
        when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);
        when(rescueVehicleMapper.findById(99L)).thenReturn(null);

        var detail = service.getTask(9L, 1L);

        assertNull(detail.getAssignedVehicle());
    }

    @Test
    void getTaskAttachesEvaluation() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("COMPLETED"));
        when(fieldRecordMapper.findByAssignmentId(50L)).thenReturn(null);
        when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);
        when(rescueVehicleMapper.findById(5L)).thenReturn(null);
        DispatchOrderEvaluation evaluation = new DispatchOrderEvaluation();
        evaluation.setDispatchOrderId(1L);
        evaluation.setScorePunctual(5);
        evaluation.setScoreStandard(4);
        evaluation.setScoreSafety(5);
        evaluation.setScoreAttitude(3);
        evaluation.setComment("处置规范");
        when(evaluationMapper.findByOrderId(1L)).thenReturn(evaluation);

        var detail = service.getTask(9L, 1L);

        assertNotNull(detail.getEvaluation());
        assertEquals(5, detail.getEvaluation().getScorePunctual());
        assertEquals(4, detail.getEvaluation().getScoreStandard());
        assertEquals(5, detail.getEvaluation().getScoreSafety());
        assertEquals(3, detail.getEvaluation().getScoreAttitude());
        assertEquals("处置规范", detail.getEvaluation().getComment());
    }

    @Test
    void reportLocationUpdatesBoundVehicle() {
        RescueVehicle bound = new RescueVehicle();
        bound.setId(7L);
        when(rescueVehicleMapper.findByDriverUserId(9L)).thenReturn(bound);
        when(rescueVehicleMapper.updateLocation(eq(7L), any(), any(), any())).thenReturn(1);

        LocationReportRequest req = new LocationReportRequest();
        req.setLng(new BigDecimal("114.057868"));
        req.setLat(new BigDecimal("22.543099"));
        LocationReportResponse resp = service.reportLocation(9L, req);

        assertEquals(7L, resp.getVehicleId());
        assertNotNull(resp.getLocationUpdatedAt());
        verify(rescueVehicleMapper).updateLocation(eq(7L),
                eq(req.getLng()), eq(req.getLat()), any());
    }

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
    void detainInPassesThroughSubmittedEntryNo() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));
        DispatchAccidentVehicle only = new DispatchAccidentVehicle();
        only.setDispatchOrderId(1L);
        only.setPlateNo("粤B11111");
        when(accidentVehicleMapper.findByOrderId(1L)).thenReturn(List.of(only));
        when(userMapper.findById(9L)).thenReturn(null);

        DetainedVehicle created = new DetainedVehicle();
        created.setId(34L);
        when(detainedVehicleService.checkIn(any(DetainInRequest.class), eq(9L))).thenReturn(created);
        when(fieldRecordMapper.findByAssignmentId(50L)).thenReturn(null);
        when(fieldRecordMapper.insert(any(DispatchFieldRecord.class))).thenReturn(1);

        DetainInRequest req = new DetainInRequest();
        req.setPlateNo("粤B11111");
        req.setParkingLotId(2L);
        req.setEntryNo("202610271418370");

        service.detainIn(9L, 1L, req);

        ArgumentCaptor<DetainInRequest> reqCaptor = ArgumentCaptor.forClass(DetainInRequest.class);
        verify(detainedVehicleService).checkIn(reqCaptor.capture(), eq(9L));
        assertEquals("202610271418370", reqCaptor.getValue().getEntryNo());
    }

    @Test
    void previewEntryNoReturnsGeneratedCodeForOwnTask() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(assignment("ACCEPTED"));
        when(entryNoGenerator.generate()).thenReturn("202610271418370");

        assertEquals("202610271418370", service.previewEntryNo(9L, 1L));
    }

    @Test
    void previewEntryNoRejectsForeignTask() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 9L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.previewEntryNo(9L, 1L));

        assertEquals("无权操作该工单", ex.getMessage());
        verifyNoInteractions(entryNoGenerator);
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
}
