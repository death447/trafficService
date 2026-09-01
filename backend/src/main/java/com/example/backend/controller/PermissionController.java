package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.Permission;
import com.example.backend.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permission")
@CrossOrigin(origins = "*")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('permission:query')")
    public Result<Map<String, Object>> getPermissionList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String permissionType) {

        List<Permission> permissions;
        if (keyword != null && !keyword.isEmpty()) {
            permissions = permissionService.findByKeyword(keyword);
        } else if (permissionType != null && !permissionType.isEmpty()) {
            permissions = permissionService.findByPermissionType(permissionType);
        } else {
            permissions = permissionService.findAll();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", permissions);
        result.put("total", permissions.size());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('permission:query')")
    public Result<List<Permission>> getPermissionTree() {
        List<Permission> permissions = permissionService.getMenuTree();
        return Result.success(permissions);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:query')")
    public Result<Permission> getPermissionById(@PathVariable Long id) {
        Permission permission = permissionService.findById(id);
        if (permission == null) {
            return Result.error("权限不存在");
        }
        return Result.success(permission);
    }

    @GetMapping("/code/{permissionCode}")
    @PreAuthorize("hasAuthority('permission:query')")
    public Result<Permission> getPermissionByCode(@PathVariable String permissionCode) {
        Permission permission = permissionService.findByPermissionCode(permissionCode);
        if (permission == null) {
            return Result.error("权限不存在");
        }
        return Result.success(permission);
    }

    @GetMapping("/parent/{parentId}")
    @PreAuthorize("hasAuthority('permission:query')")
    public Result<List<Permission>> getPermissionsByParentId(@PathVariable Long parentId) {
        List<Permission> permissions = permissionService.findByParentId(parentId);
        return Result.success(permissions);
    }

    @GetMapping("/type/{permissionType}")
    @PreAuthorize("hasAuthority('permission:query')")
    public Result<List<Permission>> getPermissionsByType(@PathVariable String permissionType) {
        List<Permission> permissions = permissionService.findByPermissionType(permissionType);
        return Result.success(permissions);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('permission:add')")
    public Result<Permission> createPermission(@RequestBody Permission permission) {
        if (permissionService.findByPermissionCode(permission.getPermissionCode()) != null) {
            return Result.error("权限代码已存在");
        }

        boolean success = permissionService.createPermission(permission);
        if (success) {
            return Result.success(permission);
        } else {
            return Result.error("创建权限失败");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:edit')")
    public Result<Permission> updatePermission(@PathVariable Long id, @RequestBody Permission permission) {
        Permission existingPermission = permissionService.findById(id);
        if (existingPermission == null) {
            return Result.error("权限不存在");
        }

        Permission codePermission = permissionService.findByPermissionCode(permission.getPermissionCode());
        if (codePermission != null && !codePermission.getId().equals(id)) {
            return Result.error("权限代码已存在");
        }

        permission.setId(id);
        boolean success = permissionService.updatePermission(permission);
        if (success) {
            return Result.success(permission);
        } else {
            return Result.error("更新权限失败");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:delete')")
    public Result<Void> deletePermission(@PathVariable Long id) {
        try {
            boolean success = permissionService.deletePermission(id);
            if (success) {
                return Result.success(null);
            } else {
                return Result.error("删除权限失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/recursive")
    @PreAuthorize("hasAuthority('permission:delete')")
    public Result<Void> deletePermissionRecursive(@PathVariable Long id) {
        try {
            boolean success = permissionService.deletePermissionAndChildren(id);
            if (success) {
                return Result.success(null);
            } else {
                return Result.error("删除权限失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/menu-tree")
    @PreAuthorize("hasAuthority('permission:query')")
    public Result<List<Permission>> getMenuTree() {
        List<Permission> permissions = permissionService.getMenuTree();
        return Result.success(permissions);
    }
}
