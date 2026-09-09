package com.example.backend.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DispatchOrder {
    private Long id;
    private String orderNo;
    private String accidentAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String rescueReason;
    private String plateNo;
    private Long vehicleTypeId;
    private String vehicleTypeName;
    private String partyName;
    private String partyPhone;
    private String status;
    private Long dispatcherId;
    private Long vehicleId;
    private Long rescuerId;
    private String abortReason;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime checkedInAt;
    private BigDecimal checkinLng;
    private BigDecimal checkinLat;
    private String checkinMode;
    private String checkinRemark;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** Non-table fields for detail display */
    private String vehiclePlate;
    private String dispatcherName;
    private String rescuerName;
}
