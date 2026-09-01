package com.example.backend.service;

import com.example.backend.entity.Role;
import com.example.backend.entity.Permission;
import com.example.backend.mapper.RoleMapper;
import com.example.backend.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    public Role findById(Long id) {
        return roleMapper.findById(id);
    }

    public Role findByRoleCode(String roleCode) {
        return roleMapper.findByRoleCode(roleCode);
    }

    public List<Role> findAll() {
        return roleMapper.findAll();
    }

    public List<Role> findByKeyword(String keyword) {
        return roleMapper.findByKeyword(keyword);
    }

    public List<Role> findByStatus(Integer status) {
        return roleMapper.findByStatus(status);
    }

    @Transactional
    public boolean createRole(Role role) {
        return roleMapper.insert(role) > 0;
    }

    @Transactional
    public boolean updateRole(Role role) {
        return roleMapper.update(role) > 0;
    }

    @Transactional
    public boolean deleteRole(Long id) {
        // 检查是否有用户使用该角色
        int userCount = roleMapper.countUsersByRoleId(id);
        if (userCount > 0) {
            throw new RuntimeException("该角色下有" + userCount + "个用户，无法删除");
        }
        roleMapper.deleteAllPermissionsByRoleId(id);
        return roleMapper.deleteById(id) > 0;
    }

    public List<Permission> getRolePermissions(Long roleId) {
        return roleMapper.findPermissionsByRoleId(roleId);
    }

    @Transactional
    public boolean assignPermissions(Long roleId, List<Long> permissionIds) {
        roleMapper.deleteAllPermissionsByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                roleMapper.addPermission(roleId, permissionId);
            }
        }
        return true;
    }

    @Transactional
    public boolean addPermission(Long roleId, Long permissionId) {
        return roleMapper.addPermission(roleId, permissionId) > 0;
    }

    @Transactional
    public boolean removePermission(Long roleId, Long permissionId) {
        return roleMapper.deletePermission(roleId, permissionId) > 0;
    }

    public int getUserCountByRoleId(Long roleId) {
        return roleMapper.countUsersByRoleId(roleId);
    }
}