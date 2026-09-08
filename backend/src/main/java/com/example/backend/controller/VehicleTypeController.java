package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.VehicleTypeRequest;
import com.example.backend.entity.AccidentVehicleType;
import com.example.backend.service.AccidentVehicleTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicle-type")
@CrossOrigin(origins = "*")
public class VehicleTypeController {

    @Autowired
    private AccidentVehicleTypeService vehicleTypeService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('vehicle-type:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        List<AccidentVehicleType> types = vehicleTypeService.list(status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", types);
        result.put("total", types.size());
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    @GetMapping("/enabled")
    public Result<List<AccidentVehicleType>> listEnabled() {
        return Result.success(vehicleTypeService.listEnabled());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle-type:query')")
    public Result<AccidentVehicleType> getById(@PathVariable Long id) {
        AccidentVehicleType type = vehicleTypeService.findById(id);
        if (type == null) {
            return Result.error("车型不存在");
        }
        return Result.success(type);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('vehicle-type:add')")
    public Result<Void> create(@RequestBody VehicleTypeRequest request) {
        try {
            boolean success = vehicleTypeService.create(request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("创建车型失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle-type:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody VehicleTypeRequest request) {
        try {
            boolean success = vehicleTypeService.update(id, request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("更新车型失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
