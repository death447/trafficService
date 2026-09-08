package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccidentVehicleType {
    private Long id;
    private String name;
    private Integer sortOrder;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
