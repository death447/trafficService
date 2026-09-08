package com.example.backend.service;

import com.example.backend.entity.AccidentVehicleType;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchOrderServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock RescueVehicleService rescueVehicleService;
    @Mock AccidentVehicleTypeService accidentVehicleTypeService;
    @Mock UserMapper userMapper;
    @InjectMocks DispatchOrderService service;

    @Test
    void createKeepsPendingAndPrefillsWithoutMarkBusy() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("测试路");
        order.setRescueReason("追尾");
        order.setDispatcherId(2L);
        order.setRescuerId(999L); // client-sent value should be overwritten by vehicle driver
        order.setVehicleId(1L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        Role t = new Role(); t.setRoleCode("TOW_DRIVER");
        when(userMapper.findRolesByUserId(2L)).thenReturn(List.of(d));
        when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
        RescueVehicle v = new RescueVehicle();
        v.setId(1L);
        v.setStatus("IDLE");
        v.setDriverUserId(3L);
        when(rescueVehicleService.requireIdle(1L)).thenReturn(v);
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
    void createWritesVehicleTypeSnapshot() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setPlateNo(" 粤B12345 ");
        order.setVehicleTypeId(6L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        AccidentVehicleType t = new AccidentVehicleType();
        t.setId(6L); t.setName("厢式货车"); t.setStatus("ENABLED");
        when(accidentVehicleTypeService.requireEnabled(6L)).thenReturn(t);
        when(dispatchOrderMapper.insert(any())).thenReturn(1);

        assertTrue(service.create(order, 7L));
        assertEquals("粤B12345", order.getPlateNo());
        assertEquals(6L, order.getVehicleTypeId());
        assertEquals("厢式货车", order.getVehicleTypeName());
    }

    @Test
    void createRejectsDisabledVehicleType() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setVehicleTypeId(99L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(accidentVehicleTypeService.requireEnabled(99L))
                .thenThrow(new RuntimeException("车型不存在或已停用"));
        assertThrows(RuntimeException.class, () -> service.create(order, 7L));
    }

    @Test
    void createClearsTypeWhenIdNull() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setVehicleTypeId(null);
        order.setVehicleTypeName("应被清空");
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(dispatchOrderMapper.insert(any())).thenReturn(1);
        assertTrue(service.create(order, 7L));
        assertNull(order.getVehicleTypeId());
        assertNull(order.getVehicleTypeName());
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
        when(rescueVehicleService.requireIdle(404L)).thenThrow(new RuntimeException("车辆不存在"));
        assertThrows(RuntimeException.class, () -> service.create(order, 7L));
    }

    @Test
    void createRejectsNonIdleVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setVehicleId(1L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(rescueVehicleService.requireIdle(1L)).thenThrow(new RuntimeException("车辆非空闲，无法派单"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(order, 7L));
        assertTrue(ex.getMessage().contains("空闲"));
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
        RescueVehicle v = new RescueVehicle();
        v.setId(1L);
        v.setStatus("IDLE");
        v.setDriverUserId(3L);
        when(rescueVehicleService.requireIdle(1L)).thenReturn(v);

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("新地址");
        patch.setRescueReason("新原因");
        patch.setLongitude(new BigDecimal("120.1"));
        patch.setLatitude(new BigDecimal("30.2"));
        patch.setDispatcherId(2L);
        patch.setRescuerId(999L);
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
    void updateAddressOnlyPreservesPrefillFields() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setDispatcherId(1L);
        existing.setRescuerId(3L);
        existing.setVehicleId(1L);
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);
        when(dispatchOrderMapper.update(any())).thenReturn(1);

        Role d = new Role(); d.setRoleCode("DISPATCHER");
        Role t = new Role(); t.setRoleCode("TOW_DRIVER");
        when(userMapper.findRolesByUserId(1L)).thenReturn(List.of(d));
        when(userMapper.findRolesByUserId(3L)).thenReturn(List.of(t));
        RescueVehicle v = new RescueVehicle();
        v.setId(1L);
        v.setStatus("IDLE");
        v.setDriverUserId(3L);
        when(rescueVehicleService.requireIdle(1L)).thenReturn(v);

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("仅改地址");

        assertTrue(service.update(patch));
        assertEquals("仅改地址", existing.getAccidentAddress());
        assertEquals(1L, existing.getDispatcherId());
        assertEquals(3L, existing.getRescuerId());
        assertEquals(1L, existing.getVehicleId());
        assertEquals("PENDING", existing.getStatus());
        verify(rescueVehicleService, never()).markBusy(any());
        verify(dispatchOrderMapper).update(existing);
    }

    @Test
    void updateKeepsSameDisabledVehicleTypeWithoutRequireEnabled() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setDispatcherId(1L);
        existing.setVehicleTypeId(6L);
        existing.setVehicleTypeName("已停用车型");
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);
        when(dispatchOrderMapper.update(any())).thenReturn(1);

        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(1L)).thenReturn(List.of(d));

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("地址");
        patch.setRescueReason("原因");
        patch.setPlateNo("粤B99999");
        patch.setVehicleTypeId(6L);
        patch.setDispatcherId(1L);

        assertTrue(service.update(patch));
        assertEquals("粤B99999", existing.getPlateNo());
        assertEquals(6L, existing.getVehicleTypeId());
        assertEquals("已停用车型", existing.getVehicleTypeName());
        verify(accidentVehicleTypeService, never()).requireEnabled(any());
        verify(dispatchOrderMapper).update(existing);
    }

    @Test
    void updateChangingVehicleTypeRequiresEnabled() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setDispatcherId(1L);
        existing.setVehicleTypeId(6L);
        existing.setVehicleTypeName("旧车型");
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);
        when(dispatchOrderMapper.update(any())).thenReturn(1);

        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(1L)).thenReturn(List.of(d));
        AccidentVehicleType t = new AccidentVehicleType();
        t.setId(7L); t.setName("新车型"); t.setStatus("ENABLED");
        when(accidentVehicleTypeService.requireEnabled(7L)).thenReturn(t);

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("地址");
        patch.setRescueReason("原因");
        patch.setVehicleTypeId(7L);
        patch.setDispatcherId(1L);

        assertTrue(service.update(patch));
        assertEquals(7L, existing.getVehicleTypeId());
        assertEquals("新车型", existing.getVehicleTypeName());
        verify(accidentVehicleTypeService).requireEnabled(7L);
        verify(accidentVehicleTypeService, never()).requireEnabled(6L);
    }

    @Test
    void updateRejectsChangingToDisabledVehicleType() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setDispatcherId(1L);
        existing.setVehicleTypeId(6L);
        existing.setVehicleTypeName("旧车型");
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);

        when(accidentVehicleTypeService.requireEnabled(99L))
                .thenThrow(new RuntimeException("车型不存在或已停用"));

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("地址");
        patch.setRescueReason("原因");
        patch.setVehicleTypeId(99L);
        patch.setDispatcherId(1L);

        assertThrows(RuntimeException.class, () -> service.update(patch));
        verify(accidentVehicleTypeService).requireEnabled(99L);
        verify(dispatchOrderMapper, never()).update(any());
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

    @Test
    void acceptMovesDispatchedToAccepted() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("DISPATCHED"); o.setRescuerId(3L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(dispatchOrderMapper.update(any())).thenReturn(1);
        service.accept(1L, 3L);
        assertEquals("ACCEPTED", o.getStatus());
        assertNotNull(o.getAcceptedAt());
    }

    @Test
    void acceptRejectsWrongRescuer() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("DISPATCHED"); o.setRescuerId(9L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        assertThrows(RuntimeException.class, () -> service.accept(1L, 3L));
    }

    @Test
    void rejectClearsAssignmentAndReleasesVehicle() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("DISPATCHED"); o.setRescuerId(3L); o.setVehicleId(8L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(dispatchOrderMapper.update(any())).thenReturn(1);
        when(dispatchOrderMapper.countDispatchedByVehicleId(8L)).thenReturn(0);
        service.reject(1L, 3L, "无法到达");
        assertEquals("PENDING", o.getStatus());
        assertNull(o.getVehicleId());
        assertNull(o.getRescuerId());
        verify(rescueVehicleService).markIdle(8L);
    }

    @Test
    void rejectFailsAfterCheckin() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED"); o.setRescuerId(3L);
        o.setCheckedInAt(LocalDateTime.now());
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        assertThrows(RuntimeException.class, () -> service.reject(1L, 3L, "x"));
    }

    @Test
    void checkinAutoFailsWhenTooFar() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED"); o.setRescuerId(3L);
        o.setLongitude(new BigDecimal("121.0000000"));
        o.setLatitude(new BigDecimal("31.0000000"));
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        // ~0.01 deg lat ≈ 1.1km
        assertThrows(RuntimeException.class, () ->
            service.checkin(1L, 3L, new BigDecimal("121.0000000"), new BigDecimal("31.0100000"), "AUTO", null));
    }

    @Test
    void completeAcceptedRequiresCheckin() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED"); o.setRescuerId(3L); o.setVehicleId(8L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        assertThrows(RuntimeException.class, () -> service.complete(1L));
    }

    @Test
    void abortAcceptedReleasesVehicle() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED"); o.setVehicleId(8L); o.setRescuerId(3L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(dispatchOrderMapper.update(any())).thenReturn(1);
        when(dispatchOrderMapper.countDispatchedByVehicleId(8L)).thenReturn(0);
        service.abort(1L, "取消");
        assertEquals("ABORTED", o.getStatus());
        assertEquals(3L, o.getRescuerId()); // 保留以便 aborted 列表
        verify(rescueVehicleService).markIdle(8L);
    }
}
