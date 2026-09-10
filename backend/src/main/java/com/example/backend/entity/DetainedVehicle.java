package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DetainedVehicle {
    private Long id;
    private String detainNo;
    private String entryNo;
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
    private String status;
    private LocalDateTime inTime;
    private LocalDateTime outTime;
    private LocalDateTime clearedAt;
    private Long operatorInId;
    private Long operatorOutId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** Non-persistent enrich fields */
    private String parkingLotName;
    private String parkingAreaName;
    private String orderNo;
}
