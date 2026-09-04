package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.NearbyVehiclesResponse;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.service.RescueVehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicle")
@CrossOrigin(origins = "*")
public class VehicleController {

    @Autowired
    private RescueVehicleService rescueVehicleService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('vehicle:query')")
    public Result<Map<String, Object>> getVehicleList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vehicleType) {

        List<RescueVehicle> vehicles = rescueVehicleService.list(keyword, status, vehicleType);

        Map<String, Object> result = new HashMap<>();
        result.put("list", vehicles);
        result.put("total", vehicles.size());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/nearby")
    @PreAuthorize("hasAuthority('dispatch:dispatch')")
    public Result<NearbyVehiclesResponse> findNearby(
            @RequestParam BigDecimal lng,
            @RequestParam BigDecimal lat,
            @RequestParam(required = false) Integer limit) {
        try {
            return Result.success(rescueVehicleService.findNearby(lng, lat, limit));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle:query')")
    public Result<RescueVehicle> getVehicleById(@PathVariable Long id) {
        RescueVehicle vehicle = rescueVehicleService.findById(id);
        if (vehicle == null) {
            return Result.error("车辆不存在");
        }
        return Result.success(vehicle);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('vehicle:add')")
    public Result<RescueVehicle> createVehicle(@RequestBody RescueVehicle vehicle) {
        try {
            boolean success = rescueVehicleService.createVehicle(vehicle);
            if (success) {
                return Result.success(vehicle);
            }
            return Result.error("创建车辆失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle:edit')")
    public Result<RescueVehicle> updateVehicle(@PathVariable Long id, @RequestBody RescueVehicle vehicle) {
        RescueVehicle existing = rescueVehicleService.findById(id);
        if (existing == null) {
            return Result.error("车辆不存在");
        }
        vehicle.setId(id);
        try {
            boolean success = rescueVehicleService.updateVehicle(vehicle);
            if (success) {
                return Result.success(vehicle);
            }
            return Result.error("更新车辆失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle:delete')")
    public Result<Void> deleteVehicle(@PathVariable Long id) {
        try {
            boolean success = rescueVehicleService.deleteVehicle(id);
            if (success) {
                return Result.success(null);
            }
            return Result.error("删除车辆失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
