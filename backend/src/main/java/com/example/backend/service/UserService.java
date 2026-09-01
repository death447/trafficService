package com.example.backend.service;

import com.example.backend.entity.User;
import com.example.backend.mapper.UserMapper;
import com.example.backend.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User findById(Long id) {
        return userMapper.findById(id);
    }

    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public List<User> findAll() {
        return userMapper.findAll();
    }

    public List<User> findByKeyword(String keyword) {
        return userMapper.findByKeyword(keyword);
    }

    public List<User> findByStatus(Integer status) {
        return userMapper.findByStatus(status);
    }

    @Transactional
    public boolean createUser(User user, List<Long> roleIds) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        int result = userMapper.insert(user);
        if (result > 0 && roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                userRoleMapper.insert(user.getId(), roleId);
            }
        }
        return result > 0;
    }

    @Transactional
    public boolean updateUser(User user, List<Long> roleIds) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            User existing = userMapper.findById(user.getId());
            user.setPassword(existing.getPassword());
        }
        int result = userMapper.update(user);
        if (result > 0) {
            userRoleMapper.deleteAllByUserId(user.getId());
            if (roleIds != null && !roleIds.isEmpty()) {
                for (Long roleId : roleIds) {
                    userRoleMapper.insert(user.getId(), roleId);
                }
            }
        }
        return result > 0;
    }

    @Transactional
    public boolean deleteUser(Long id) {
        userRoleMapper.deleteAllByUserId(id);
        return userMapper.deleteById(id) > 0;
    }

    public List<com.example.backend.entity.Role> getUserRoles(Long userId) {
        return userMapper.findRolesByUserId(userId);
    }

    public List<com.example.backend.entity.Permission> getUserPermissions(Long userId) {
        return userMapper.findPermissionsByUserId(userId);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        List<com.example.backend.entity.Permission> permissions = getUserPermissions(userId);
        return permissions.stream()
                .anyMatch(p -> p.getPermissionCode().equals(permissionCode));
    }
}