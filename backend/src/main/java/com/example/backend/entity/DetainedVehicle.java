package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DetainedVehicle {
    private Long id;
    private String detainNo;
    private String plateNo;
    private String vehicleType;
    private Long parkingLotId;
    private Long dispatchOrderId;
    private String detainDept;
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
    private String orderNo;
}
