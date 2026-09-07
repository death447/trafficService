package com.example.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CheckinRequest {
    private BigDecimal lng;
    private BigDecimal lat;
    private String mode;
    private String remark;
}
