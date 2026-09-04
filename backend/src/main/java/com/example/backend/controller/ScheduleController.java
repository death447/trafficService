package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.ScheduleRequest;
import com.example.backend.entity.DutySchedule;
import com.example.backend.service.DutyScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@CrossOrigin(origins = "*")
public class ScheduleController {

    @Autowired
    private DutyScheduleService dutyScheduleService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('schedule:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long userId) {
        List<DutySchedule> schedules = dutyScheduleService.list(from, to, roleType, districtId, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", schedules);
        result.put("total", schedules.size());
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:query')")
    public Result<DutySchedule> getById(@PathVariable Long id) {
        DutySchedule schedule = dutyScheduleService.findById(id);
        if (schedule == null) {
            return Result.error("排班不存在");
        }
        return Result.success(schedule);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule:add')")
    public Result<Void> create(@RequestBody ScheduleRequest request) {
        try {
            boolean success = dutyScheduleService.create(request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("创建排班失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody ScheduleRequest request) {
        try {
            boolean success = dutyScheduleService.update(id, request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("更新排班失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('schedule:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            boolean success = dutyScheduleService.delete(id);
            if (success) {
                return Result.success(null);
            }
            return Result.error("删除排班失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
