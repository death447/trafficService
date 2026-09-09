package com.example.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AssignedVehicleSnapshot {
    private Long id;
    private String plateNo;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime locationUpdatedAt;
}
