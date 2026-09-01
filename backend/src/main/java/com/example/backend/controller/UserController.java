package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.User;
import com.example.backend.entity.Role;
import com.example.backend.entity.Permission;
import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('user:query')")
    public Result<Map<String, Object>> getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        List<User> users;
        if (keyword != null && !keyword.isEmpty()) {
            users = userService.findByKeyword(keyword);
        } else if (status != null) {
            users = userService.findByStatus(status);
        } else {
            users = userService.findAll();
        }

        for (User user : users) {
            List<Role> roles = userService.getUserRoles(user.getId());
            user.setRoles(roles);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", users);
        result.put("total", users.size());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:query')")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:add')")
    public Result<User> createUser(@RequestBody Map<String, Object> params) {
        User user = new User();
        user.setUsername((String) params.get("username"));
        user.setEmail((String) params.get("email"));
        user.setPassword((String) params.get("password"));
        user.setPhone((String) params.get("phone"));
        user.setRealName((String) params.get("realName"));
        user.setStatus(params.get("status") != null ? (Integer) params.get("status") : 1);

        @SuppressWarnings("unchecked")
        List<Long> roleIds = (List<Long>) params.get("roleIds");

        boolean success = userService.createUser(user, roleIds);
        if (success) {
            return Result.success(user);
        } else {
            return Result.error("创建用户失败");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:edit')")
    public Result<User> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        user.setUsername((String) params.get("username"));
        user.setEmail((String) params.get("email"));
        if (params.get("password") != null && !params.get("password").toString().isEmpty()) {
            user.setPassword((String) params.get("password"));
        } else {
            user.setPassword(null);
        }
        user.setPhone((String) params.get("phone"));
        user.setRealName((String) params.get("realName"));
        user.setStatus(params.get("status") != null ? (Integer) params.get("status") : 1);

        @SuppressWarnings("unchecked")
        List<Long> roleIds = (List<Long>) params.get("roleIds");

        boolean success = userService.updateUser(user, roleIds);
        if (success) {
            return Result.success(user);
        } else {
            return Result.error("更新用户失败");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        boolean success = userService.deleteUser(id);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error("删除用户失败");
        }
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:query')")
    public Result<List<Role>> getUserRoles(@PathVariable Long id) {
        List<Role> roles = userService.getUserRoles(id);
        return Result.success(roles);
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('user:query')")
    public Result<List<Permission>> getUserPermissions(@PathVariable Long id) {
        List<Permission> permissions = userService.getUserPermissions(id);
        return Result.success(permissions);
    }

    @PostMapping("/{userId}/assign-roles")
    @PreAuthorize("hasAuthority('user:edit')")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        User user = userService.findById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        user.setPassword(null);
        boolean success = userService.updateUser(user, roleIds);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error("分配角色失败");
        }
    }

    @GetMapping("/{userId}/check-permission/{permissionCode}")
    @PreAuthorize("hasAuthority('user:query')")
    public Result<Boolean> checkPermission(@PathVariable Long userId, @PathVariable String permissionCode) {
        boolean hasPermission = userService.hasPermission(userId, permissionCode);
        return Result.success(hasPermission);
    }
}
