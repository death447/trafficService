package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchSceneVehicle {
    private Long id;
    private Long dispatchOrderId;
    private Long assignmentId;
    private Long accidentVehicleId;
    private String plateNo;
    private String vehicleType;
    private String damageDesc;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
