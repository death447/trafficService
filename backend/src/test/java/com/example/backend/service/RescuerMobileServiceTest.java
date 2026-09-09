package com.example.backend.service;

import com.example.backend.dto.BindVehicleRequest;
import com.example.backend.dto.LocationReportRequest;
import com.example.backend.dto.LocationReportResponse;
import com.example.backend.dto.SceneRequest;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescuerMobileServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock DispatchFieldRecordMapper fieldRecordMapper;
    @Mock DispatchMediaMapper mediaMapper;
    @Mock RescueVehicleMapper rescueVehicleMapper;
    @Mock UserMapper userMapper;
    @Mock LocalFileStorageService fileStorageService;
    @InjectMocks RescuerMobileService service;

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

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.addMedia(9L, 1L, "OTHER", file));
        assertTrue(ex.getMessage().contains("bizType") || ex.getMessage().contains("类型"));
        verifyNoInteractions(fileStorageService);
        verify(mediaMapper, never()).insert(any());
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
    void getTaskAssignedVehicleNullWhenNoVehicleId() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("DISPATCHED");
        order.setVehicleId(null);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);

        var detail = service.getTask(9L, 1L);

        assertNull(detail.getAssignedVehicle());
        verify(rescueVehicleMapper, never()).findById(any());
    }

    @Test
    void getTaskAssignedVehicleNullWhenVehicleMissing() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("ACCEPTED");
        order.setVehicleId(99L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);
        when(fieldRecordMapper.findByOrderId(1L)).thenReturn(null);
        when(rescueVehicleMapper.findById(99L)).thenReturn(null);

        var detail = service.getTask(9L, 1L);

        assertNull(detail.getAssignedVehicle());
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
}
