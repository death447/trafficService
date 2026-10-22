package com.example.backend.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DispatchVehicleAssignment {
    private Long id;
    private Long dispatchOrderId;
    private Long vehicleId;
    private Long rescuerId;
    private String status;
    private LocalDateTime dispatchedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime checkedInAt;
    private BigDecimal checkinLng;
    private BigDecimal checkinLat;
    private String checkinMode;
    private String checkinRemark;
    private String rejectReason;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** Non-table display fields */
    private String vehiclePlate;
    private String rescuerName;
}
