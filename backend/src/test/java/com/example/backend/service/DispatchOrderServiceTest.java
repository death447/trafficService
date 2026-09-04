package com.example.backend.service;

import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.Role;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchOrderServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock RescueVehicleService rescueVehicleService;
    @Mock UserMapper userMapper;
    @InjectMocks DispatchOrderService service;

    @Test
    void createKeepsPendingAndPrefillsWithoutMarkBusy() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("测试路");
        order.setRescueReason("追尾");
        order.setDispatcherId(2L);
        order.setRescuerId(3L);
        order.setVehicleId(1L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        Role t = new Role(); t.setRoleCode("TOW_DRIVER");
        when(userMapper.findRolesByUserId(2L)).thenReturn(List.of(d));
        when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
        RescueVehicle v = new RescueVehicle(); v.setId(1L); v.setStatus("IDLE");
        when(rescueVehicleService.findById(1L)).thenReturn(v);
        when(dispatchOrderMapper.insert(any())).thenReturn(1);

        assertTrue(service.create(order, 99L));
        assertEquals("PENDING", order.getStatus());
        assertEquals(2L, order.getDispatcherId());
        assertEquals(3L, order.getRescuerId());
        assertEquals(1L, order.getVehicleId());
        verify(rescueVehicleService, never()).markBusy(any());
        verify(dispatchOrderMapper).insert(order);
    }

    @Test
    void createDefaultsDispatcherToCurrentUser() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(dispatchOrderMapper.insert(any())).thenReturn(1);
        assertTrue(service.create(order, 7L));
        assertEquals(7L, order.getDispatcherId());
    }

    @Test
    void createRejectsInvalidRescuerRole() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setRescuerId(3L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        Role bad = new Role(); bad.setRoleCode("ADMIN");
        when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(bad));
        assertThrows(RuntimeException.class, () -> service.create(order, 7L));
    }

    @Test
    void createRejectsMissingVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setVehicleId(404L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(rescueVehicleService.findById(404L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.create(order, 7L));
    }

    @Test
    void updatePendingAllowsPrefillFields() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setDispatcherId(1L);
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);
        when(dispatchOrderMapper.update(any())).thenReturn(1);

        Role d = new Role(); d.setRoleCode("ADMIN");
        Role t = new Role(); t.setRoleCode("TOW_DRIVER");
        when(userMapper.findRolesByUserId(2L)).thenReturn(List.of(d));
        when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
        RescueVehicle v = new RescueVehicle(); v.setId(1L); v.setStatus("IDLE");
        when(rescueVehicleService.findById(1L)).thenReturn(v);

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("新地址");
        patch.setRescueReason("新原因");
        patch.setLongitude(new BigDecimal("120.1"));
        patch.setLatitude(new BigDecimal("30.2"));
        patch.setDispatcherId(2L);
        patch.setRescuerId(3L);
        patch.setVehicleId(1L);

        assertTrue(service.update(patch));
        assertEquals("新地址", existing.getAccidentAddress());
        assertEquals("新原因", existing.getRescueReason());
        assertEquals(new BigDecimal("120.1"), existing.getLongitude());
        assertEquals(new BigDecimal("30.2"), existing.getLatitude());
        assertEquals(2L, existing.getDispatcherId());
        assertEquals(3L, existing.getRescuerId());
        assertEquals(1L, existing.getVehicleId());
        assertEquals("PENDING", existing.getStatus());
        verify(rescueVehicleService, never()).markBusy(any());
        verify(dispatchOrderMapper).update(existing);
    }

    @Test
    void assignMovesOrderAndMarksVehicleBusy() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("PENDING");
        RescueVehicle vehicle = new RescueVehicle();
        vehicle.setId(3L);
        vehicle.setStatus("IDLE");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(rescueVehicleService.requireIdle(3L)).thenReturn(vehicle);

        service.assign(9L, 3L, null);

        assertEquals("DISPATCHED", order.getStatus());
        assertEquals(3L, order.getVehicleId());
        verify(rescueVehicleService).markBusy(3L);
        verify(dispatchOrderMapper).update(order);
    }

    @Test
    void assignRejectsNonPendingOrder() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);

        assertThrows(RuntimeException.class, () -> service.assign(9L, 3L, null));
        verify(rescueVehicleService, never()).requireIdle(any());
        verify(rescueVehicleService, never()).markBusy(any());
        verify(dispatchOrderMapper, never()).update(any());
    }

    @Test
    void assignRejectsNonIdleVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("PENDING");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(rescueVehicleService.requireIdle(3L))
                .thenThrow(new RuntimeException("车辆非空闲，无法派单"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.assign(9L, 3L, null));
        assertTrue(ex.getMessage().contains("空闲") || ex.getMessage().contains("IDLE")
                || ex.getMessage().contains("派单"));
        verify(rescueVehicleService, never()).markBusy(any());
        verify(dispatchOrderMapper, never()).update(any());
    }

    @Test
    void completeMarksCompletedAndReleasesVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("DISPATCHED");
        order.setVehicleId(3L);
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(dispatchOrderMapper.countDispatchedByVehicleId(3L)).thenReturn(0);

        service.complete(9L);

        assertEquals("COMPLETED", order.getStatus());
        assertNotNull(order.getCompletedAt());
        verify(dispatchOrderMapper).update(order);
        verify(rescueVehicleService).markIdle(3L);
    }

    @Test
    void abortPendingDoesNotReleaseVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("PENDING");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);

        service.abort(9L, "取消任务");

        assertEquals("ABORTED", order.getStatus());
        assertEquals("取消任务", order.getAbortReason());
        verify(dispatchOrderMapper).update(order);
        verify(rescueVehicleService, never()).markIdle(any());
    }

    @Test
    void abortDispatchedReleasesVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("DISPATCHED");
        order.setVehicleId(3L);
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(dispatchOrderMapper.countDispatchedByVehicleId(3L)).thenReturn(0);

        service.abort(9L, "无法到达");

        assertEquals("ABORTED", order.getStatus());
        assertEquals("无法到达", order.getAbortReason());
        verify(dispatchOrderMapper).update(order);
        verify(rescueVehicleService).markIdle(3L);
    }

    @Test
    void updateRejectsNonPendingOrder() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("新地址");

        assertThrows(RuntimeException.class, () -> service.update(patch));
        verify(dispatchOrderMapper, never()).update(any());
    }
}
