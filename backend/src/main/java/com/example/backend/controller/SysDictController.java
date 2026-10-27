package com.example.backend.controller;

import com.example.backend.common.PageParams;
import com.example.backend.common.Result;
import com.example.backend.dto.DictItemRequest;
import com.example.backend.entity.SysDictItem;
import com.example.backend.service.SysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
@CrossOrigin(origins = "*")
public class SysDictController {

    @Autowired
    private SysDictService dictService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('dict:query')")
    public Result<Map<String, Object>> list(
            @RequestParam String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageParams pp = PageParams.normalize(page, size);
        try {
            return Result.success(dictService.list(type, status, pp));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/enabled")
    public Result<List<SysDictItem>> listEnabled(@RequestParam String type) {
        try {
            return Result.success(dictService.listEnabled(type));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('dict:add')")
    public Result<Void> create(@RequestBody DictItemRequest request) {
        try {
            if (dictService.create(request)) {
                return Result.success(null);
            }
            return Result.error("创建字典项失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictItemRequest request) {
        try {
            if (dictService.update(id, request)) {
                return Result.success(null);
            }
            return Result.error("更新字典项失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            if (dictService.delete(id)) {
                return Result.success(null);
            }
            return Result.error("删除字典项失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
