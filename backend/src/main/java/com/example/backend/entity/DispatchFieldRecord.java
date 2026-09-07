package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchFieldRecord {
    private Long id;
    private Long dispatchOrderId;
    private String plateNo;
    private String vehicleType;
    private String damageDesc;
    private String sceneRemark;
    private String parkAddress;
    private String parkRemark;
    private LocalDateTime sceneSubmittedAt;
    private LocalDateTime parkSubmittedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
