package com.example.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LocationReportRequest {
    private BigDecimal lng;
    private BigDecimal lat;
    private BigDecimal accuracy; // optional, ignored by service if unused
}
