package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DetainMedia {
    private Long id;
    private Long detainId;
    private String bizType;
    private String filePath;
    private Integer sortOrder;
    private Long uploadedBy;
    private LocalDateTime createTime;
}
