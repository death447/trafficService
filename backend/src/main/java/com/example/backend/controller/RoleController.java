package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.Role;
import com.example.backend.entity.Permission;
import com.example.backend.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/role")
@CrossOrigin(origins = "*")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('role:query')")
    public Result<Map<String, Object>> getRoleList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        List<Role> roles;
        if (keyword != null && !keyword.isEmpty()) {
            roles = roleService.findByKeyword(keyword);
        } else if (status != null) {
            roles = roleService.findByStatus(status);
        } else {
            roles = roleService.findAll();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", roles);
        result.put("total", roles.size());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('role:query')")
    public Result<Role> getRoleById(@PathVariable Long id) {
        Role role = roleService.findById(id);
        if (role == null) {
            return Result.error("角色不存在");
        }
        return Result.success(role);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:add')")
    public Result<Role> createRole(@RequestBody Role role) {
        if (roleService.findByRoleCode(role.getRoleCode()) != null) {
            return Result.error("角色代码已存在");
        }

        boolean success = roleService.createRole(role);
        if (success) {
            return Result.success(role);
        } else {
            return Result.error("创建角色失败");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        Role existingRole = roleService.findById(id);
        if (existingRole == null) {
            return Result.error("角色不存在");
        }

        Role codeRole = roleService.findByRoleCode(role.getRoleCode());
        if (codeRole != null && !codeRole.getId().equals(id)) {
            return Result.error("角色代码已存在");
        }

        role.setId(id);
        boolean success = roleService.updateRole(role);
        if (success) {
            return Result.success(role);
        } else {
            return Result.error("更新角色失败");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:delete')")
    public Result<Void> deleteRole(@PathVariable Long id) {
        try {
            boolean success = roleService.deleteRole(id);
            if (success) {
                return Result.success(null);
            } else {
                return Result.error("删除角色失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('role:query')")
    public Result<List<Permission>> getRolePermissions(@PathVariable Long id) {
        List<Permission> permissions = roleService.getRolePermissions(id);
        return Result.success(permissions);
    }

    @PostMapping("/{id}/assign-permissions")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        boolean success = roleService.assignPermissions(id, permissionIds);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error("分配权限失败");
        }
    }

    @PostMapping("/{roleId}/add-permission/{permissionId}")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<Void> addPermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
        boolean success = roleService.addPermission(roleId, permissionId);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error("添加权限失败");
        }
    }

    @DeleteMapping("/{roleId}/remove-permission/{permissionId}")
    @PreAuthorize("hasAuthority('role:edit')")
    public Result<Void> removePermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
        boolean success = roleService.removePermission(roleId, permissionId);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error("移除权限失败");
        }
    }

    @GetMapping("/{id}/user-count")
    @PreAuthorize("hasAuthority('role:query')")
    public Result<Integer> getUserCountByRoleId(@PathVariable Long id) {
        int count = roleService.getUserCountByRoleId(id);
        return Result.success(count);
    }
}
