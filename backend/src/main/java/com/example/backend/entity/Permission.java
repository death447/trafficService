package com.example.backend.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Permission {
    private Long id;
    private String permissionName;
    private String permissionCode;
    private String permissionType;
    private Long parentId;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Permission> children;
}
