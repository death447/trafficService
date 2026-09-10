package com.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DetainUpdateRequest {
    private String plateNo;
    private String vehicleType;
    private String brandModel;
    private String vehicleColor;
    private String mileage;
    private String importantEquipment;
    private String hasKey;
    private Long parkingLotId;
    private Long parkingAreaId;
    private String stallNo;
    private Long dispatchOrderId;
    private String detainDept;
    private String rescuerName;
    private String rescueReason;
    private String rescueMethod;
    private LocalDateTime rescueTime;
    private String rescueAddress;
    private String remark;
}
