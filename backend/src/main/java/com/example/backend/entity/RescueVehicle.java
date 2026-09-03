package com.example.backend.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RescueVehicle {
    private Long id;
    private String plateNo;
    private String vehicleType;
    private String color;
    private String equipment;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String status;
    private Long districtId;
    private Long driverUserId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
