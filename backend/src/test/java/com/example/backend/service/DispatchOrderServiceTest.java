package com.example.backend.service;

import com.example.backend.entity.AccidentVehicleType;
import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchOrderEvaluation;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.Role;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.entity.DispatchAccidentVehicle;
import com.example.backend.entity.DispatchVehicleAssignment;
import com.example.backend.mapper.DispatchAccidentVehicleMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.DispatchVehicleAssignmentMapper;
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
    @Mock DispatchFieldRecordMapper fieldRecordMapper;
    @Mock DispatchMediaMapper mediaMapper;
    @Mock DispatchOrderEvaluationMapper evaluationMapper;
    @Mock DispatchVehicleAssignmentMapper assignmentMapper;
    @Mock DispatchAccidentVehicleMapper accidentVehicleMapper;
    @InjectMocks DispatchOrderService service;

    private DispatchOrder basePendingOrder() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("测试路");
        order.setRescueReason("追尾");
        return order;
    }

    @Test
    void createRequiresAtLeastOneAccidentVehicleWithPlate() {
        DispatchOrder order = basePendingOrder();
        order.setAccidentVehicles(List.of());
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(order, 7L));
        assertTrue(ex.getMessage().contains("至少"));
    }

    @Test
    void createRejectsDuplicatePlates() {
        DispatchOrder order = basePendingOrder();
        DispatchAccidentVehicle a = new DispatchAccidentVehicle();
        a.setPlateNo("粤B1");
        DispatchAccidentVehicle b = new DispatchAccidentVehicle();
        b.setPlateNo(" 粤b1 ");
        order.setAccidentVehicles(List.of(a, b));
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(order, 7L));
        assertTrue(ex.getMessage().contains("重复"));
    }

    @Test
    void createPersistsTwoAccidentVehiclesAndSyncsFirstToOrder() {
        DispatchOrder order = basePendingOrder();
        DispatchAccidentVehicle first = new DispatchAccidentVehicle();
        first.setPlateNo("粤B11111");
        first.setPartyName("甲");
        first.setPartyPhone("13800000001");
        first.setVehicleTypeId(6L);
        DispatchAccidentVehicle second = new DispatchAccidentVehicle();
        second.setPlateNo("粤B22222");
        second.setPartyName("乙");
        order.setAccidentVehicles(List.of(first, second));
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        AccidentVehicleType t = new AccidentVehicleType();
        t.setId(6L); t.setName("厢式货车"); t.setStatus("ENABLED");
        when(accidentVehicleTypeService.requireEnabled(6L)).thenReturn(t);
        when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(100L);
            return 1;
        });

        assertTrue(service.create(order, 7L));
        assertEquals("粤B11111", order.getPlateNo());
        assertEquals(6L, order.getVehicleTypeId());
        assertEquals("厢式货车", order.getVehicleTypeName());
        assertEquals("甲", order.getPartyName());
        assertEquals("13800000001", order.getPartyPhone());
        verify(accidentVehicleMapper).deleteByOrderId(100L);
        verify(accidentVehicleMapper, times(2)).insert(any(DispatchAccidentVehicle.class));
    }

    @Test
    void createKeepsPendingAndPrefillsWithoutMarkBusy() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("测试路");
        order.setRescueReason("追尾");
        order.setPlateNo("粤B00001");
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
        when(rescueVehicleService.requireAssignable(1L)).thenReturn(v);
        when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });

        assertTrue(service.create(order, 99L));
        assertEquals("PENDING", order.getStatus());
        assertEquals(2L, order.getDispatcherId());
        assertEquals(3L, order.getRescuerId());
        assertEquals(1L, order.getVehicleId());
        verify(rescueVehicleService, never()).markBusy(any());
        verify(dispatchOrderMapper).insert(order);
        verify(accidentVehicleMapper).insert(any(DispatchAccidentVehicle.class));
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
        when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });

        assertTrue(service.create(order, 7L));
        assertEquals("粤B12345", order.getPlateNo());
        assertEquals(6L, order.getVehicleTypeId());
        assertEquals("厢式货车", order.getVehicleTypeName());
    }

    @Test
    void createNormalizesBlankPartyFieldsToNull() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setPlateNo("粤B00002");
        order.setPartyName("  ");
        order.setPartyPhone("");
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });

        assertTrue(service.create(order, 7L));
        assertNull(order.getPartyName());
        assertNull(order.getPartyPhone());
        verify(dispatchOrderMapper).insert(order);
    }

    @Test
    void createPersistsTrimmedPartyFields() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setPlateNo("粤B00003");
        order.setPartyName(" 张三 ");
        order.setPartyPhone(" 13800138000 ");
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });

        assertTrue(service.create(order, 7L));
        assertEquals("张三", order.getPartyName());
        assertEquals("13800138000", order.getPartyPhone());
        verify(dispatchOrderMapper).insert(order);
    }

    @Test
    void updateCopiesPartyFieldsWhenPending() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setDispatcherId(1L);
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);
        when(dispatchOrderMapper.update(any())).thenReturn(1);

        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(1L)).thenReturn(List.of(d));

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("地址");
        patch.setRescueReason("原因");
        patch.setDispatcherId(1L);
        patch.setPlateNo("粤B00009");
        patch.setPartyName(" 李四 ");
        patch.setPartyPhone(" 13900139000 ");

        assertTrue(service.update(patch));
        assertEquals("李四", existing.getPartyName());
        assertEquals("13900139000", existing.getPartyPhone());
        assertEquals("粤B00009", existing.getPlateNo());
        verify(dispatchOrderMapper).update(existing);
        verify(accidentVehicleMapper).deleteByOrderId(9L);
        verify(accidentVehicleMapper).insert(any(DispatchAccidentVehicle.class));
    }

    @Test
    void createRejectsDisabledVehicleType() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setPlateNo("粤B00099");
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
        order.setPlateNo("粤B00004");
        order.setVehicleTypeId(null);
        order.setVehicleTypeName("应被清空");
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });
        assertTrue(service.create(order, 7L));
        assertNull(order.getVehicleTypeId());
        assertNull(order.getVehicleTypeName());
    }

    @Test
    void createDefaultsDispatcherToCurrentUser() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setPlateNo("粤B00005");
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(dispatchOrderMapper.insert(any())).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        });
        assertTrue(service.create(order, 7L));
        assertEquals(7L, order.getDispatcherId());
    }

    @Test
    void createRejectsInvalidRescuerRole() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setPlateNo("粤B00006");
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
        order.setPlateNo("粤B00007");
        order.setVehicleId(404L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(rescueVehicleService.requireAssignable(404L)).thenThrow(new RuntimeException("车辆不存在"));
        assertThrows(RuntimeException.class, () -> service.create(order, 7L));
    }

    @Test
    void createRejectsOfflineVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setAccidentAddress("A");
        order.setRescueReason("B");
        order.setPlateNo("粤B00008");
        order.setVehicleId(1L);
        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(d));
        when(rescueVehicleService.requireAssignable(1L)).thenThrow(new RuntimeException("车辆已离线，无法派单"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(order, 7L));
        assertTrue(ex.getMessage().contains("离线"));
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
        when(rescueVehicleService.requireAssignable(1L)).thenReturn(v);

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("新地址");
        patch.setRescueReason("新原因");
        patch.setLongitude(new BigDecimal("120.1"));
        patch.setLatitude(new BigDecimal("30.2"));
        patch.setPlateNo("粤B00010");
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
    void assignCreatesAssignmentAndMarksBusy() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("PENDING");
        RescueVehicle vehicle = new RescueVehicle();
        vehicle.setId(3L);
        vehicle.setStatus("BUSY");
        vehicle.setDriverUserId(7L);
        Role driver = new Role();
        driver.setRoleCode("TOW_DRIVER");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(assignmentMapper.countByOrderIdAndVehicleId(9L, 3L)).thenReturn(0);
        when(rescueVehicleService.requireAssignable(3L)).thenReturn(vehicle);
        when(userMapper.findRolesByUserId(7L)).thenReturn(List.of(driver));
        when(assignmentMapper.insert(any())).thenAnswer(inv -> {
            DispatchVehicleAssignment a = inv.getArgument(0);
            a.setId(100L);
            return 1;
        });
        DispatchVehicleAssignment created = new DispatchVehicleAssignment();
        created.setId(100L);
        created.setDispatchOrderId(9L);
        created.setVehicleId(3L);
        created.setRescuerId(7L);
        created.setStatus("DISPATCHED");
        when(assignmentMapper.findByOrderId(9L)).thenReturn(List.of(created));

        service.assign(9L, 3L, null);

        verify(assignmentMapper).insert(argThat(a ->
                a.getVehicleId().equals(3L) && "DISPATCHED".equals(a.getStatus())
                        && a.getRescuerId().equals(7L)));
        verify(rescueVehicleService).markBusy(3L);
        assertEquals("DISPATCHED", order.getStatus());
        assertEquals(3L, order.getVehicleId());
        assertEquals(7L, order.getRescuerId());
    }

    @Test
    void assignRejectsCompletedOrder() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        assertThrows(RuntimeException.class, () -> service.assign(9L, 3L, null));
        verify(assignmentMapper, never()).insert(any());
    }

    @Test
    void assignRejectsOfflineVehicle() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("PENDING");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(assignmentMapper.countByOrderIdAndVehicleId(9L, 3L)).thenReturn(0);
        when(rescueVehicleService.requireAssignable(3L))
                .thenThrow(new RuntimeException("车辆已离线，无法派单"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.assign(9L, 3L, null));
        assertTrue(ex.getMessage().contains("离线"));
        verify(rescueVehicleService, never()).markBusy(any());
    }

    @Test
    void completeByRescuerMarksAssignmentAndMayCompleteOrder() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("ACCEPTED");
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setId(1L);
        a.setDispatchOrderId(9L);
        a.setVehicleId(3L);
        a.setRescuerId(7L);
        a.setStatus("ACCEPTED");
        a.setCheckedInAt(LocalDateTime.now());
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(assignmentMapper.findByOrderIdAndRescuerId(9L, 7L)).thenReturn(a);
        when(assignmentMapper.update(any())).thenReturn(1);
        when(assignmentMapper.findByOrderId(9L)).thenAnswer(inv -> {
            a.setStatus("COMPLETED");
            return List.of(a);
        });
        when(assignmentMapper.countActiveByVehicleId(3L)).thenReturn(0);

        service.complete(9L, 7L);

        assertEquals("COMPLETED", a.getStatus());
        verify(rescueVehicleService).markIdle(3L);
        assertEquals("COMPLETED", order.getStatus());
    }

    @Test
    void pcCompleteRejectsWhenActiveAssignmentsRemain() {
        DispatchOrder order = new DispatchOrder();
        order.setId(9L);
        order.setStatus("DISPATCHED");
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(assignmentMapper.findByOrderId(9L)).thenReturn(List.of(a));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.complete(9L));
        assertTrue(ex.getMessage().contains("未完成"));
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
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setId(1L);
        a.setVehicleId(3L);
        a.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(9L)).thenReturn(order);
        when(assignmentMapper.findByOrderId(9L)).thenReturn(List.of(a));
        when(assignmentMapper.update(any())).thenReturn(1);
        when(assignmentMapper.countActiveByVehicleId(3L)).thenReturn(0);

        service.abort(9L, "无法到达");

        assertEquals("ABORTED", order.getStatus());
        assertEquals("无法到达", order.getAbortReason());
        assertEquals("ABORTED", a.getStatus());
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
        when(rescueVehicleService.requireAssignable(1L)).thenReturn(v);

        DispatchOrder patch = new DispatchOrder();
        patch.setId(9L);
        patch.setAccidentAddress("仅改地址");
        patch.setPlateNo("粤B00011");

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
    void updateRevalidatesVehicleTypeOnReplace() {
        DispatchOrder existing = new DispatchOrder();
        existing.setId(9L);
        existing.setStatus("PENDING");
        existing.setDispatcherId(1L);
        existing.setVehicleTypeId(6L);
        existing.setVehicleTypeName("旧快照");
        when(dispatchOrderMapper.findById(9L)).thenReturn(existing);
        when(dispatchOrderMapper.update(any())).thenReturn(1);

        Role d = new Role(); d.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(1L)).thenReturn(List.of(d));
        AccidentVehicleType t = new AccidentVehicleType();
        t.setId(6L); t.setName("厢式货车"); t.setStatus("ENABLED");
        when(accidentVehicleTypeService.requireEnabled(6L)).thenReturn(t);

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
        assertEquals("厢式货车", existing.getVehicleTypeName());
        verify(accidentVehicleTypeService).requireEnabled(6L);
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
        patch.setPlateNo("粤B00012");
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
        patch.setPlateNo("粤B00013");
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
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setId(10L); a.setStatus("DISPATCHED"); a.setRescuerId(3L); a.setVehicleId(8L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 3L)).thenReturn(a);
        when(assignmentMapper.update(any())).thenReturn(1);
        when(assignmentMapper.findByOrderId(1L)).thenAnswer(inv -> {
            a.setStatus("ACCEPTED");
            return List.of(a);
        });
        service.accept(1L, 3L);
        assertEquals("ACCEPTED", a.getStatus());
        assertNotNull(a.getAcceptedAt());
        assertEquals("ACCEPTED", o.getStatus());
    }

    @Test
    void acceptRejectsWrongRescuer() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 3L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.accept(1L, 3L));
    }

    @Test
    void rejectClearsAssignmentAndReleasesVehicle() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("DISPATCHED"); o.setRescuerId(3L); o.setVehicleId(8L);
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setId(10L); a.setStatus("DISPATCHED"); a.setRescuerId(3L); a.setVehicleId(8L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 3L)).thenReturn(a);
        when(assignmentMapper.update(any())).thenReturn(1);
        when(assignmentMapper.findByOrderId(1L)).thenAnswer(inv -> {
            a.setStatus("REJECTED");
            return List.of(a);
        });
        when(assignmentMapper.countActiveByVehicleId(8L)).thenReturn(0);
        service.reject(1L, 3L, "无法到达");
        assertEquals("REJECTED", a.getStatus());
        assertEquals("PENDING", o.getStatus());
        verify(rescueVehicleService).markIdle(8L);
    }

    @Test
    void rejectFailsAfterCheckin() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED");
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setStatus("ACCEPTED");
        a.setCheckedInAt(LocalDateTime.now());
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 3L)).thenReturn(a);
        assertThrows(RuntimeException.class, () -> service.reject(1L, 3L, "x"));
    }

    @Test
    void checkinAutoFailsWhenTooFar() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED");
        o.setLongitude(new BigDecimal("121.0000000"));
        o.setLatitude(new BigDecimal("31.0000000"));
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 3L)).thenReturn(a);
        assertThrows(RuntimeException.class, () ->
            service.checkin(1L, 3L, new BigDecimal("121.0000000"), new BigDecimal("31.0100000"), "AUTO", null));
    }

    @Test
    void completeAcceptedRequiresCheckin() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED");
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setStatus("ACCEPTED");
        a.setVehicleId(8L);
        a.setRescuerId(3L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderIdAndRescuerId(1L, 3L)).thenReturn(a);
        assertThrows(RuntimeException.class, () -> service.complete(1L, 3L));
    }

    @Test
    void abortAcceptedReleasesVehicle() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L); o.setStatus("ACCEPTED"); o.setVehicleId(8L); o.setRescuerId(3L);
        DispatchVehicleAssignment a = new DispatchVehicleAssignment();
        a.setStatus("ACCEPTED"); a.setVehicleId(8L); a.setRescuerId(3L);
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderId(1L)).thenReturn(List.of(a));
        when(assignmentMapper.update(any())).thenReturn(1);
        when(assignmentMapper.countActiveByVehicleId(8L)).thenReturn(0);
        service.abort(1L, "取消");
        assertEquals("ABORTED", o.getStatus());
        assertEquals("ABORTED", a.getStatus());
        verify(rescueVehicleService).markIdle(8L);
    }

    @Test
    void findByIdAttachesSceneCollection() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L);
        o.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderId(1L)).thenReturn(List.of());
        when(assignmentMapper.findByOrderId(1L)).thenReturn(List.of());
        DispatchFieldRecord record = new DispatchFieldRecord();
        record.setDispatchOrderId(1L);
        record.setPlateNo("浙F12345");
        record.setDamageDesc("左前损伤");
        when(fieldRecordMapper.findByOrderId(1L)).thenReturn(record);
        DispatchMedia photo = new DispatchMedia();
        photo.setId(9L);
        photo.setDispatchOrderId(1L);
        photo.setBizType("DAMAGE");
        photo.setFilePath("dispatch/1/a.jpg");
        when(mediaMapper.findByOrderId(1L)).thenReturn(List.of(photo));

        DispatchOrder detail = service.findById(1L);

        assertNotNull(detail.getFieldRecord());
        assertEquals("浙F12345", detail.getFieldRecord().getPlateNo());
        assertEquals("左前损伤", detail.getFieldRecord().getDamageDesc());
        assertEquals(1, detail.getMedias().size());
        assertEquals("DAMAGE", detail.getMedias().get(0).getBizType());
    }

    @Test
    void findByIdAttachesEvaluation() {
        DispatchOrder o = new DispatchOrder();
        o.setId(1L);
        o.setStatus("COMPLETED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(o);
        when(assignmentMapper.findByOrderId(1L)).thenReturn(List.of());
        DispatchOrderEvaluation evaluation = new DispatchOrderEvaluation();
        evaluation.setDispatchOrderId(1L);
        evaluation.setScorePunctual(5);
        evaluation.setScoreStandard(4);
        evaluation.setScoreSafety(5);
        evaluation.setScoreAttitude(3);
        evaluation.setComment("处置规范");
        when(evaluationMapper.findByOrderId(1L)).thenReturn(evaluation);

        DispatchOrder detail = service.findById(1L);

        assertNotNull(detail.getEvaluation());
        assertEquals(5, detail.getEvaluation().getScorePunctual());
        assertEquals(4, detail.getEvaluation().getScoreStandard());
        assertEquals(5, detail.getEvaluation().getScoreSafety());
        assertEquals(3, detail.getEvaluation().getScoreAttitude());
        assertEquals("处置规范", detail.getEvaluation().getComment());
    }

    @Test
    void overviewCountsTodayAndListsActiveAccidents() {
        when(dispatchOrderMapper.countCreatedBetween(any(), any())).thenReturn(12L);
        when(dispatchOrderMapper.countDispatchedBetween(any(), any())).thenReturn(8L);
        when(dispatchOrderMapper.countCompletedBetween(any(), any())).thenReturn(5L);
        when(dispatchOrderMapper.countActive()).thenReturn(3L);
        DispatchOrder accident = new DispatchOrder();
        accident.setId(9L);
        accident.setOrderNo("RO202609120001");
        accident.setStatus("ACCEPTED");
        accident.setAccidentAddress("南湖大道");
        accident.setLongitude(new BigDecimal("120.75"));
        accident.setLatitude(new BigDecimal("30.75"));
        when(dispatchOrderMapper.findActiveWithCoords()).thenReturn(List.of(accident));

        var overview = service.overview();

        assertEquals(12L, overview.getTodayTotal());
        assertEquals(8L, overview.getTodayDispatched());
        assertEquals(5L, overview.getTodayCompleted());
        assertEquals(3L, overview.getInProgress());
        assertEquals(1, overview.getAccidents().size());
        assertEquals(9L, overview.getAccidents().get(0).getId());
        assertEquals("南湖大道", overview.getAccidents().get(0).getAccidentAddress());
    }
}
