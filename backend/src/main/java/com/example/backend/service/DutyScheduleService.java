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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DutyScheduleService {

    private static final String ROLE_DISPATCHER = "DISPATCHER";
    private static final String ROLE_TOW_DRIVER = "TOW_DRIVER";

    @Autowired
    private DutyScheduleMapper dutyScheduleMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RescueVehicleMapper rescueVehicleMapper;
    @Autowired
    private DistrictMapper districtMapper;

    public List<DutySchedule> list(LocalDate from, LocalDate to, String roleType, Long districtId, Long userId) {
        return dutyScheduleMapper.findList(from, to, roleType, districtId, userId);
    }

    public DutySchedule findById(Long id) {
        return dutyScheduleMapper.findById(id);
    }

    @Transactional
    public boolean create(ScheduleRequest req) {
        DutySchedule schedule = new DutySchedule();
        applyAndValidate(schedule, req, null);
        return dutyScheduleMapper.insert(schedule) > 0;
    }

    @Transactional
    public boolean update(Long id, ScheduleRequest req) {
        DutySchedule existing = dutyScheduleMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("排班不存在");
        }
        applyAndValidate(existing, req, id);
        return dutyScheduleMapper.update(existing) > 0;
    }

    @Transactional
    public boolean delete(Long id) {
        DutySchedule existing = dutyScheduleMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("排班不存在");
        }
        return dutyScheduleMapper.deleteById(id) > 0;
    }

    private void applyAndValidate(DutySchedule schedule, ScheduleRequest req, Long excludeId) {
        LocalDateTime startTime = req.getStartTime();
        LocalDateTime endTime = req.getEndTime();
        if (startTime == null || endTime == null) {
            throw new RuntimeException("开始时间和结束时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new RuntimeException("结束时间必须晚于开始时间");
        }

        String roleType = req.getRoleType();
        if (!ROLE_DISPATCHER.equals(roleType) && !ROLE_TOW_DRIVER.equals(roleType)) {
            throw new RuntimeException("角色类型仅允许 DISPATCHER 或 TOW_DRIVER");
        }

        Long userId = req.getUserId();
        if (userId == null) {
            throw new RuntimeException("用户不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        List<Role> roles = userMapper.findRolesByUserId(userId);
        boolean hasRole = roles != null && roles.stream()
                .anyMatch(r -> roleType.equals(r.getRoleCode()));
        if (!hasRole) {
            throw new RuntimeException("用户不具备对应角色");
        }

        Long vehicleId = req.getVehicleId();
        if (ROLE_DISPATCHER.equals(roleType)) {
            if (vehicleId != null) {
                throw new RuntimeException("调度员班次不能绑定车辆");
            }
        } else {
            if (vehicleId == null) {
                throw new RuntimeException("施救员班次必须绑定车辆");
            }
            RescueVehicle vehicle = rescueVehicleMapper.findById(vehicleId);
            if (vehicle == null) {
                throw new RuntimeException("车辆不存在");
            }
        }

        Long districtId = req.getDistrictId();
        if (districtId != null) {
            District district = districtMapper.findById(districtId);
            if (district == null || !"ENABLED".equals(district.getStatus())) {
                throw new RuntimeException("片区不存在或已禁用");
            }
        }

        List<DutySchedule> userOverlaps = dutyScheduleMapper.findOverlappingByUser(
                userId, startTime, endTime, excludeId);
        if (userOverlaps != null && !userOverlaps.isEmpty()) {
            throw new RuntimeException("该用户在该时段已有排班重叠");
        }
        if (vehicleId != null) {
            List<DutySchedule> vehicleOverlaps = dutyScheduleMapper.findOverlappingByVehicle(
                    vehicleId, startTime, endTime, excludeId);
            if (vehicleOverlaps != null && !vehicleOverlaps.isEmpty()) {
                throw new RuntimeException("该车辆在该时段已有排班重叠");
            }
        }

        schedule.setDutyDate(startTime.toLocalDate());
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setUserId(userId);
        schedule.setRoleType(roleType);
        schedule.setDistrictId(districtId);
        schedule.setVehicleId(vehicleId);
        schedule.setRemark(req.getRemark());
    }
}
