package com.example.backend.service;

import com.example.backend.entity.Permission;
import com.example.backend.mapper.PermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PermissionService {

    @Autowired
    private PermissionMapper permissionMapper;

    public Permission findById(Long id) {
        return permissionMapper.findById(id);
    }

    public Permission findByPermissionCode(String permissionCode) {
        return permissionMapper.findByPermissionCode(permissionCode);
    }

    public List<Permission> findByParentId(Long parentId) {
        return permissionMapper.findByParentId(parentId);
    }

    public List<Permission> findAll() {
        return permissionMapper.findAll();
    }

    public List<Permission> findByPermissionType(String permissionType) {
        return permissionMapper.findByPermissionType(permissionType);
    }

    public List<Permission> findByPermissionTypeAndParentId(String permissionType, Long parentId) {
        return permissionMapper.findByPermissionTypeAndParentId(permissionType, parentId);
    }

    public List<Permission> findByKeyword(String keyword) {
        return permissionMapper.findByKeyword(keyword);
    }

    public List<Permission> getMenuTree() {
        return buildMenuTree(0L);
    }

    private List<Permission> buildMenuTree(Long parentId) {
        List<Permission> permissions = permissionMapper.findByParentId(parentId);
        for (Permission permission : permissions) {
            permission.setChildren(buildMenuTree(permission.getId()));
        }
        return permissions;
    }

    @Transactional
    public boolean createPermission(Permission permission) {
        return permissionMapper.insert(permission) > 0;
    }

    @Transactional
    public boolean updatePermission(Permission permission) {
        return permissionMapper.update(permission) > 0;
    }

    @Transactional
    public boolean deletePermission(Long id) {
        int childrenCount = permissionMapper.countChildrenById(id);
        if (childrenCount > 0) {
            throw new RuntimeException("该权限下有" + childrenCount + "个子权限，无法删除");
        }
        return permissionMapper.deleteById(id) > 0;
    }

    @Transactional
    public boolean deletePermissionAndChildren(Long id) {
        List<Permission> children = permissionMapper.findByParentId(id);
        for (Permission child : children) {
            deletePermissionAndChildren(child.getId());
        }
        return permissionMapper.deleteById(id) > 0;
    }
}
