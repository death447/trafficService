package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.DistrictRequest;
import com.example.backend.entity.District;
import com.example.backend.service.DistrictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/district")
@CrossOrigin(origins = "*")
public class DistrictController {

    @Autowired
    private DistrictService districtService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('district:query')")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        List<District> districts = districtService.list(keyword, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", districts);
        result.put("total", districts.size());
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority('district:resolve')")
    public Result<District> resolve(
            @RequestParam BigDecimal lng,
            @RequestParam BigDecimal lat) {
        try {
            return Result.success(districtService.resolve(lng, lat));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('district:query')")
    public Result<District> getById(@PathVariable Long id) {
        District district = districtService.findById(id);
        if (district == null) {
            return Result.error("片区不存在");
        }
        return Result.success(district);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('district:add')")
    public Result<Void> create(@RequestBody DistrictRequest request) {
        try {
            boolean success = districtService.create(request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("创建片区失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('district:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody DistrictRequest request) {
        try {
            boolean success = districtService.update(id, request);
            if (success) {
                return Result.success(null);
            }
            return Result.error("更新片区失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('district:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            boolean success = districtService.delete(id);
            if (success) {
                return Result.success(null);
            }
            return Result.error("删除片区失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
