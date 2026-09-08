package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.common.Result;
import com.example.backend.dto.DetainInRequest;
import com.example.backend.dto.DetainUpdateRequest;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.service.DetainedVehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/detain")
@CrossOrigin(origins = "*")
public class DetainedVehicleController {

    @Autowired
    private DetainedVehicleService detainedVehicleService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('detain:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) String detainNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String detainDept) {
        PageParams pp = PageParams.normalize(page, size);
        return Result.success(detainedVehicleService.list(
                plateNo, detainNo, status, parkingLotId, detainDept, pp));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('detain:query')")
    public Result<DetainedVehicle> getById(@PathVariable Long id) {
        DetainedVehicle vehicle = detainedVehicleService.findById(id);
        if (vehicle == null) {
            return Result.error("扣留车辆不存在");
        }
        return Result.success(vehicle);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('detain:add')")
    public Result<Void> checkIn(@RequestBody DetainInRequest request) {
        try {
            boolean success = detainedVehicleService.checkIn(request, currentUserId());
            if (success) {
                return Result.success(null);
            }
            return Result.error("入库失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('detain:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody DetainUpdateRequest request) {
        try {
            boolean success = detainedVehicleService.update(id, request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("更新失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/out")
    @PreAuthorize("hasAuthority('detain:out')")
    public Result<Void> checkOut(@PathVariable Long id) {
        try {
            boolean success = detainedVehicleService.checkOut(id, currentUserId());
            if (success) {
                return Result.success(null);
            }
            return Result.error("出库失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{id}/clear")
    @PreAuthorize("hasAuthority('detain:clear')")
    public Result<Void> clear(@PathVariable Long id) {
        try {
            boolean success = detainedVehicleService.clear(id);
            if (success) {
                return Result.success(null);
            }
            return Result.error("清理失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private Long currentUserId() {
        CustomUserDetails principal =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getId();
    }
}
