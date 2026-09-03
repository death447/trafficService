package com.example.backend.service;

import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.mapper.DispatchOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchOrderServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock RescueVehicleService rescueVehicleService;
    @InjectMocks DispatchOrderService service;

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
