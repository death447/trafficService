package com.example.backend.service;

import com.example.backend.dto.ActiveDispatchSummary;
import com.example.backend.dto.DetainInRequest;
import com.example.backend.dto.DetainUpdateRequest;
import com.example.backend.entity.DetainMedia;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainMediaMapper;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetainedVehicleServiceTest {
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @Mock ParkingLotService parkingLotService;
    @Mock ParkingAreaService parkingAreaService;
    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock DetainMediaMapper detainMediaMapper;
    @Mock DispatchMediaMapper dispatchMediaMapper;
    @Mock LocalFileStorageService fileStorageService;
    @InjectMocks DetainedVehicleService service;

    @Test
    void checkInUsesManualDetainNoAndGeneratesEntryNo() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo(" 凭证001 ");
        req.setPlateNo(" 粤B停01 ");
        req.setParkingLotId(1L);
        req.setVehicleType("小型车");
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("凭证001")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B停01")).thenReturn(0);
        when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
        when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
            DetainedVehicle v = inv.getArgument(0);
            v.setId(88L);
            return 1;
        });

        DetainedVehicle created = service.checkIn(req, 4L);

        assertEquals(88L, created.getId());
        assertEquals("粤B停01", created.getPlateNo());
        assertEquals("IN_YARD", created.getStatus());
        assertEquals(4L, created.getOperatorInId());
        assertEquals("凭证001", created.getDetainNo());
        assertNotNull(created.getEntryNo());
        assertTrue(created.getEntryNo().length() >= 12);
    }

    @Test
    void checkInRejectsDuplicateDetainNo() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV1");
        req.setPlateNo("粤B1");
        req.setParkingLotId(1L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV1")).thenReturn(new DetainedVehicle());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 1L));
        assertTrue(ex.getMessage().contains("扣押编号"));
        verify(detainedVehicleMapper, never()).insert(any());
    }

    @Test
    void checkInRejectsAreaFromOtherLot() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV2");
        req.setPlateNo("粤B2");
        req.setParkingLotId(1L);
        req.setParkingAreaId(9L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV2")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B2")).thenReturn(0);
        doThrow(new RuntimeException("停放区域不属于所选停车场"))
                .when(parkingAreaService).requireEnabledForLot(9L, 1L);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 1L));
        assertTrue(ex.getMessage().contains("不属于"));
        verify(detainedVehicleMapper, never()).insert(any());
    }

    @Test
    void checkInRejectsWhenPlateAlreadyInYard() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV3");
        req.setPlateNo(" 粤B12345 ");
        req.setParkingLotId(1L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV3")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(1);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 9L));
        assertTrue(ex.getMessage().contains("在库") || ex.getMessage().contains("车牌"));
        verify(detainedVehicleMapper, never()).insert(any());
    }

    @Test
    void checkOutThenClearHappyPath() {
        DetainedVehicle v = inYard(10L, "粤B1");
        when(detainedVehicleMapper.findById(10L)).thenReturn(v);
        when(detainedVehicleMapper.update(any())).thenReturn(1);
        assertTrue(service.checkOut(10L, 3L));
        assertEquals("OUT", v.getStatus());
        assertNotNull(v.getOutTime());
        assertEquals(3L, v.getOperatorOutId());

        assertTrue(service.clear(10L));
        assertEquals("CLEARED", v.getStatus());
        assertNotNull(v.getClearedAt());
    }

    @Test
    void clearRejectsWhenStillInYard() {
        when(detainedVehicleMapper.findById(1L)).thenReturn(inYard(1L, "粤B1"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.clear(1L));
        assertTrue(ex.getMessage().contains("出库") || ex.getMessage().contains("清理"));
    }

    @Test
    void updateRejectsPlateConflict() {
        DetainedVehicle v = inYard(5L, "粤B旧");
        when(detainedVehicleMapper.findById(5L)).thenReturn(v);
        DetainUpdateRequest req = new DetainUpdateRequest();
        req.setPlateNo("粤B新");
        req.setParkingLotId(1L);
        when(detainedVehicleMapper.countInYardByPlateNoExcludingId("粤B新", 5L)).thenReturn(1);
        assertThrows(RuntimeException.class, () -> service.update(5L, req));
        verify(parkingLotService, never()).requireEnabled(any());
    }

    @Test
    void listActiveOrdersRejectsShortPlate() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.listActiveOrdersByPlate(" ·A. "));
        assertEquals("请输入车牌", ex.getMessage());
        verify(dispatchOrderMapper, never()).findActiveWithPlate();
    }

    @Test
    void listActiveOrdersMatchesNormalizedPlateNewestFirst() {
        when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(
                order(3L, "ACCEPTED", "粤B12345"),
                order(2L, "PENDING", "粤b·12345"),
                order(1L, "DISPATCHED", "粤A00000")
        ));

        List<ActiveDispatchSummary> list = service.listActiveOrdersByPlate(" 粤B.12345 ");

        assertEquals(2, list.size());
        assertEquals(3L, list.get(0).getId());
        assertEquals(2L, list.get(1).getId());
        assertEquals("粤b·12345", list.get(1).getPlateNo());
    }

    @Test
    void listActiveOrdersReturnsEmptyWhenNoMatch() {
        when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(
                order(1L, "PENDING", "粤A1")
        ));
        assertTrue(service.listActiveOrdersByPlate("粤B99999").isEmpty());
    }

    @Test
    void listActiveOrdersIncludesCheckinAndDamagePathsNotPark() {
        DispatchOrder row = order(3L, "ACCEPTED", "粤B12345");
        row.setCheckedInAt(LocalDateTime.of(2026, 9, 9, 16, 38, 15));
        when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(row));
        DispatchMedia dmg = new DispatchMedia();
        dmg.setFilePath("dispatch/3/a.jpg");
        dmg.setBizType("DAMAGE");
        DispatchMedia park = new DispatchMedia();
        park.setFilePath("dispatch/3/p.jpg");
        park.setBizType("PARK");
        when(dispatchMediaMapper.findByOrderIdAndBizType(3L, "DAMAGE")).thenReturn(List.of(dmg));

        List<ActiveDispatchSummary> list = service.listActiveOrdersByPlate("粤B12345");

        assertEquals(1, list.size());
        assertEquals(LocalDateTime.of(2026, 9, 9, 16, 38, 15), list.get(0).getCheckedInAt());
        assertEquals(List.of("dispatch/3/a.jpg"), list.get(0).getDamagePhotoPaths());
    }

    @Test
    void listActiveOrdersNullCheckinAndEmptyPhotos() {
        when(dispatchOrderMapper.findActiveWithPlate()).thenReturn(List.of(
                order(2L, "PENDING", "粤B12345")
        ));
        when(dispatchMediaMapper.findByOrderIdAndBizType(2L, "DAMAGE")).thenReturn(List.of());

        ActiveDispatchSummary s = service.listActiveOrdersByPlate("粤B12345").get(0);
        assertNull(s.getCheckedInAt());
        assertNotNull(s.getDamagePhotoPaths());
        assertTrue(s.getDamagePhotoPaths().isEmpty());
    }

    @Test
    void checkInRejectsFinishedLinkedOrder() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-F");
        req.setPlateNo("粤B12345");
        req.setParkingLotId(1L);
        req.setDispatchOrderId(9L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-F")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
        DispatchOrder finished = order(9L, "COMPLETED", "粤B12345");
        when(dispatchOrderMapper.findById(9L)).thenReturn(finished);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 4L));
        assertTrue(ex.getMessage().contains("已结束"));
        verify(detainedVehicleMapper, never()).insert(any());
    }

    @Test
    void checkInRejectsLinkedOrderPlateMismatch() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-M");
        req.setPlateNo("粤B12345");
        req.setParkingLotId(1L);
        req.setDispatchOrderId(8L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-M")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
        when(dispatchOrderMapper.findById(8L)).thenReturn(order(8L, "ACCEPTED", "粤A00000"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 4L));
        assertTrue(ex.getMessage().contains("车牌"));
        verify(detainedVehicleMapper, never()).insert(any());
    }

    @Test
    void checkInAcceptsLinkedOrderWhenNormalizedPlateMatches() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-OK");
        req.setPlateNo("粤B12345");
        req.setParkingLotId(1L);
        req.setDispatchOrderId(7L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-OK")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
        when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
        when(dispatchOrderMapper.findById(7L)).thenReturn(order(7L, "ACCEPTED", "粤b·12345"));
        when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
            DetainedVehicle v = inv.getArgument(0);
            v.setId(101L);
            return 1;
        });
        when(dispatchMediaMapper.findByOrderIdAndBizType(7L, "DAMAGE")).thenReturn(List.of());

        DetainedVehicle created = service.checkIn(req, 4L);
        assertEquals(7L, created.getDispatchOrderId());
    }

    @Test
    void checkInCopiesDamagePhotosToSceneAndSkipsMissing() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-CP");
        req.setPlateNo("粤B12345");
        req.setParkingLotId(1L);
        req.setDispatchOrderId(7L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-CP")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
        when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
        when(dispatchOrderMapper.findById(7L)).thenReturn(order(7L, "ACCEPTED", "粤B12345"));
        when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
            DetainedVehicle v = inv.getArgument(0);
            v.setId(101L);
            return 1;
        });
        DispatchMedia keep = new DispatchMedia();
        keep.setFilePath("dispatch/7/a.jpg");
        DispatchMedia miss = new DispatchMedia();
        miss.setFilePath("dispatch/7/gone.jpg");
        when(dispatchMediaMapper.findByOrderIdAndBizType(7L, "DAMAGE")).thenReturn(List.of(keep, miss));
        when(fileStorageService.copyToDetainMedia(101L, "dispatch/7/a.jpg")).thenReturn("detain/101/1.jpg");
        when(fileStorageService.copyToDetainMedia(101L, "dispatch/7/gone.jpg")).thenReturn(null);
        when(detainMediaMapper.insert(any())).thenReturn(1);

        service.checkIn(req, 4L);

        org.mockito.ArgumentCaptor<DetainMedia> cap = org.mockito.ArgumentCaptor.forClass(DetainMedia.class);
        verify(detainMediaMapper, times(1)).insert(cap.capture());
        assertEquals("SCENE", cap.getValue().getBizType());
        assertEquals("detain/101/1.jpg", cap.getValue().getFilePath());
        assertEquals(101L, cap.getValue().getDetainId());
        assertEquals(4L, cap.getValue().getUploadedBy());
    }

    @Test
    void checkInPropagatesCopyToDetainMediaFailure() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-IO");
        req.setPlateNo("粤B12345");
        req.setParkingLotId(1L);
        req.setDispatchOrderId(7L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-IO")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(0);
        when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
        when(dispatchOrderMapper.findById(7L)).thenReturn(order(7L, "ACCEPTED", "粤B12345"));
        when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
            DetainedVehicle v = inv.getArgument(0);
            v.setId(101L);
            return 1;
        });
        DispatchMedia dmg = new DispatchMedia();
        dmg.setFilePath("dispatch/7/a.jpg");
        when(dispatchMediaMapper.findByOrderIdAndBizType(7L, "DAMAGE")).thenReturn(List.of(dmg));
        when(fileStorageService.copyToDetainMedia(101L, "dispatch/7/a.jpg"))
                .thenThrow(new RuntimeException("文件复制失败"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 4L));
        assertEquals("文件复制失败", ex.getMessage());
        verify(detainMediaMapper, never()).insert(any());
    }

    @Test
    void checkInWithoutOrderDoesNotCopyMedia() {
        DetainInRequest req = new DetainInRequest();
        req.setDetainNo("DV-NO");
        req.setPlateNo("粤B停01");
        req.setParkingLotId(1L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.findByDetainNo("DV-NO")).thenReturn(null);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B停01")).thenReturn(0);
        when(detainedVehicleMapper.countByEntryNoPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(0);
        when(detainedVehicleMapper.insert(any(DetainedVehicle.class))).thenAnswer(inv -> {
            DetainedVehicle v = inv.getArgument(0);
            v.setId(88L);
            return 1;
        });

        service.checkIn(req, 4L);

        verify(dispatchMediaMapper, never()).findByOrderIdAndBizType(any(), any());
        verify(fileStorageService, never()).copyToDetainMedia(any(), any());
    }

    @Test
    void updateKeepsSameDisabledLotWithoutRequireEnabled() {
        DetainedVehicle v = inYard(7L, "粤B停用场");
        v.setParkingLotId(99L);
        v.setVehicleType("小型车");
        v.setDetainDept("交警一大队");
        v.setRemark("原备注");
        when(detainedVehicleMapper.findById(7L)).thenReturn(v);
        when(detainedVehicleMapper.countInYardByPlateNoExcludingId("粤B停用场", 7L)).thenReturn(0);
        when(detainedVehicleMapper.update(any())).thenReturn(1);

        DetainUpdateRequest req = new DetainUpdateRequest();
        req.setPlateNo("粤B停用场");
        req.setParkingLotId(99L);
        req.setVehicleType(null);
        req.setDetainDept(null);
        req.setRemark("新备注");

        assertTrue(service.update(7L, req));
        verify(parkingLotService, never()).requireEnabled(any());
        assertNull(v.getVehicleType());
        assertNull(v.getDetainDept());
        assertEquals("新备注", v.getRemark());
        assertEquals(99L, v.getParkingLotId());
    }

    private static DispatchOrder order(long id, String status, String plate) {
        DispatchOrder o = new DispatchOrder();
        o.setId(id);
        o.setOrderNo("RO" + id);
        o.setStatus(status);
        o.setPlateNo(plate);
        o.setVehicleTypeName("小型汽车");
        o.setAccidentAddress("测试路" + id);
        return o;
    }

    private static DetainedVehicle inYard(Long id, String plate) {
        DetainedVehicle v = new DetainedVehicle();
        v.setId(id);
        v.setPlateNo(plate);
        v.setStatus("IN_YARD");
        v.setParkingLotId(1L);
        v.setInTime(java.time.LocalDateTime.now());
        return v;
    }
}
