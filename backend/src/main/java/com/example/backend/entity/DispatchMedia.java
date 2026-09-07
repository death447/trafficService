package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchMedia {
    private Long id;
    private Long dispatchOrderId;
    private String bizType;
    private String filePath;
    private Integer sortOrder;
    private Long uploadedBy;
    private LocalDateTime createTime;
}
