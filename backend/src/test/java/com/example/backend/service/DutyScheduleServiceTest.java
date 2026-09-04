package com.example.backend.service;

import com.example.backend.dto.ScheduleRequest;
import com.example.backend.entity.District;
import com.example.backend.entity.DutySchedule;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.mapper.DistrictMapper;
import com.example.backend.mapper.DutyScheduleMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DutyScheduleServiceTest {

    @Mock DutyScheduleMapper dutyScheduleMapper;
    @Mock UserMapper userMapper;
    @Mock RescueVehicleMapper rescueVehicleMapper;
    @Mock DistrictMapper districtMapper;

    @InjectMocks DutyScheduleService service;

    @Test
    void createRejectsTowDriverWithoutVehicle() {
        ScheduleRequest req = baseTowRequest();
        req.setVehicleId(null);
        stubUserWithRole(10L, "TOW_DRIVER");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("车辆"));
        verify(dutyScheduleMapper, never()).insert(any());
    }

    @Test
    void createRejectsDispatcherWithVehicle() {
        ScheduleRequest req = baseDispatcherRequest();
        req.setVehicleId(1L);
        stubUserWithRole(20L, "DISPATCHER");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("车辆") || ex.getMessage().contains("调度"));
        verify(dutyScheduleMapper, never()).insert(any());
    }

    @Test
    void createRejectsUserTimeOverlap() {
        ScheduleRequest req = baseTowRequest();
        stubUserWithRole(10L, "TOW_DRIVER");
        when(rescueVehicleMapper.findById(1L)).thenReturn(vehicle(1L));
        when(dutyScheduleMapper.findOverlappingByUser(eq(10L), any(), any(), isNull()))
                .thenReturn(List.of(existingSchedule(99L)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("重叠") || ex.getMessage().contains("冲突")
                || ex.getMessage().contains("排班"));
        verify(dutyScheduleMapper, never()).insert(any());
    }

    @Test
    void createRejectsVehicleTimeOverlap() {
        ScheduleRequest req = baseTowRequest();
        stubUserWithRole(10L, "TOW_DRIVER");
        when(rescueVehicleMapper.findById(1L)).thenReturn(vehicle(1L));
        when(dutyScheduleMapper.findOverlappingByUser(eq(10L), any(), any(), isNull()))
                .thenReturn(Collections.emptyList());
        when(dutyScheduleMapper.findOverlappingByVehicle(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(existingSchedule(88L)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("重叠") || ex.getMessage().contains("冲突")
                || ex.getMessage().contains("车辆") || ex.getMessage().contains("排班"));
        verify(dutyScheduleMapper, never()).insert(any());
    }

    @Test
    void createRejectsCrossDayOverlapWithNextMorning() {
        // overnight shift ending next morning overlaps a morning shift
        ScheduleRequest req = baseTowRequest();
        req.setStartTime(LocalDateTime.of(2026, 9, 4, 22, 0));
        req.setEndTime(LocalDateTime.of(2026, 9, 5, 6, 0));
        stubUserWithRole(10L, "TOW_DRIVER");
        when(rescueVehicleMapper.findById(1L)).thenReturn(vehicle(1L));
        when(dutyScheduleMapper.findOverlappingByUser(eq(10L),
                eq(LocalDateTime.of(2026, 9, 4, 22, 0)),
                eq(LocalDateTime.of(2026, 9, 5, 6, 0)),
                isNull()))
                .thenReturn(List.of(existingSchedule(77L)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("重叠") || ex.getMessage().contains("冲突")
                || ex.getMessage().contains("排班"));
        verify(dutyScheduleMapper, never()).insert(any());
    }

    @Test
    void createSucceedsAndSetsDutyDateFromStartTime() {
        ScheduleRequest req = baseTowRequest();
        req.setStartTime(LocalDateTime.of(2026, 9, 4, 8, 0));
        req.setEndTime(LocalDateTime.of(2026, 9, 4, 17, 0));
        stubUserWithRole(10L, "TOW_DRIVER");
        when(rescueVehicleMapper.findById(1L)).thenReturn(vehicle(1L));
        when(dutyScheduleMapper.findOverlappingByUser(anyLong(), any(), any(), isNull()))
                .thenReturn(Collections.emptyList());
        when(dutyScheduleMapper.findOverlappingByVehicle(anyLong(), any(), any(), isNull()))
                .thenReturn(Collections.emptyList());
        when(dutyScheduleMapper.insert(any(DutySchedule.class))).thenReturn(1);

        assertTrue(service.create(req));

        ArgumentCaptor<DutySchedule> captor = ArgumentCaptor.forClass(DutySchedule.class);
        verify(dutyScheduleMapper).insert(captor.capture());
        assertEquals(LocalDate.of(2026, 9, 4), captor.getValue().getDutyDate());
        assertEquals(10L, captor.getValue().getUserId());
        assertEquals("TOW_DRIVER", captor.getValue().getRoleType());
        assertEquals(1L, captor.getValue().getVehicleId());
    }

    @Test
    void createRejectsUserWithoutMatchingRole() {
        ScheduleRequest req = baseTowRequest();
        User user = new User();
        user.setId(10L);
        when(userMapper.findById(10L)).thenReturn(user);
        Role dispatcher = new Role();
        dispatcher.setRoleCode("DISPATCHER");
        when(userMapper.findRolesByUserId(10L)).thenReturn(List.of(dispatcher));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("角色"));
        verify(dutyScheduleMapper, never()).insert(any());
    }

    @Test
    void createRejectsDisabledDistrict() {
        ScheduleRequest req = baseDispatcherRequest();
        req.setDistrictId(5L);
        stubUserWithRole(20L, "DISPATCHER");
        District disabled = new District();
        disabled.setId(5L);
        disabled.setStatus("DISABLED");
        when(districtMapper.findById(5L)).thenReturn(disabled);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("片区"));
        verify(dutyScheduleMapper, never()).insert(any());
    }

    private void stubUserWithRole(Long userId, String roleCode) {
        User user = new User();
        user.setId(userId);
        when(userMapper.findById(userId)).thenReturn(user);
        Role role = new Role();
        role.setRoleCode(roleCode);
        when(userMapper.findRolesByUserId(userId)).thenReturn(List.of(role));
    }

    private static ScheduleRequest baseTowRequest() {
        ScheduleRequest req = new ScheduleRequest();
        req.setStartTime(LocalDateTime.of(2026, 9, 4, 8, 0));
        req.setEndTime(LocalDateTime.of(2026, 9, 4, 17, 0));
        req.setUserId(10L);
        req.setRoleType("TOW_DRIVER");
        req.setVehicleId(1L);
        return req;
    }

    private static ScheduleRequest baseDispatcherRequest() {
        ScheduleRequest req = new ScheduleRequest();
        req.setStartTime(LocalDateTime.of(2026, 9, 4, 8, 0));
        req.setEndTime(LocalDateTime.of(2026, 9, 4, 17, 0));
        req.setUserId(20L);
        req.setRoleType("DISPATCHER");
        req.setVehicleId(null);
        return req;
    }

    private static RescueVehicle vehicle(Long id) {
        RescueVehicle v = new RescueVehicle();
        v.setId(id);
        return v;
    }

    private static DutySchedule existingSchedule(Long id) {
        DutySchedule s = new DutySchedule();
        s.setId(id);
        return s;
    }
}
