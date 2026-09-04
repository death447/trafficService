package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class District {
    private Long id;
    private String name;
    private String code;
    private String fenceJson;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
