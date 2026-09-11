package com.example.backend.dto;

import lombok.Data;

@Data
public class ActiveDispatchSummary {
    private Long id;
    private String orderNo;
    private String status;
    private String plateNo;
    private String vehicleTypeName;
    private String accidentAddress;
}
