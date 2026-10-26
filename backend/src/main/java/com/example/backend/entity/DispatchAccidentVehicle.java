package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchAccidentVehicle {
    private Long id;
    private Long dispatchOrderId;
    private Integer sortOrder;
    private String plateNo;
    private Long vehicleTypeId;
    private String vehicleTypeName;
    private String partyName;
    private String partyPhone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
