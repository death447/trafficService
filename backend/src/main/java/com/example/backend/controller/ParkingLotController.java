package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.common.Result;
import com.example.backend.dto.ParkingLotRequest;
import com.example.backend.entity.ParkingLot;
import com.example.backend.service.ParkingLotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/parking")
@CrossOrigin(origins = "*")
public class ParkingLotController {

    @Autowired
    private ParkingLotService parkingLotService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('parking:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        PageParams pp = PageParams.normalize(page, size);
        return Result.success(parkingLotService.list(keyword, status, pp));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('parking:query')")
    public Result<ParkingLot> getById(@PathVariable Long id) {
        ParkingLot lot = parkingLotService.findById(id);
        if (lot == null) {
            return Result.error("停车场不存在");
        }
        return Result.success(lot);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('parking:add')")
    public Result<Void> create(@RequestBody ParkingLotRequest request) {
        try {
            boolean success = parkingLotService.create(request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("创建停车场失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('parking:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody ParkingLotRequest request) {
        try {
            boolean success = parkingLotService.update(id, request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("更新停车场失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('parking:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            boolean success = parkingLotService.delete(id);
            if (success) {
                return Result.success(null);
            }
            return Result.error("删除停车场失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
