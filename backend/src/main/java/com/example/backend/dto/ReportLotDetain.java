package com.example.backend.dto;

import lombok.Data;

@Data
public class ReportLotDetain {
    private Long parkingLotId;
    private String parkingLotName;
    private long inbound;
    private long inYard;
    private long outbound;
}
