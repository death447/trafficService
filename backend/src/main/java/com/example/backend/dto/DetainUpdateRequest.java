package com.example.backend.dto;

import lombok.Data;

@Data
public class DetainUpdateRequest {
    private String plateNo;
    private String vehicleType;
    private Long parkingLotId;
    private Long dispatchOrderId;
    private String detainDept;
    private String remark;
}
