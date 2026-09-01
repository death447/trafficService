package com.example.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class User {
    private Long id;
    private String username;
    private String email;
    @Getter(onMethod_ = @JsonIgnore)
    private String password;
    private String phone;
    private String realName;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 非数据库字段，用于存储用户角色
    private List<Role> roles;

    // 非数据库字段，用于存储用户权限
    private List<Permission> permissions;
}