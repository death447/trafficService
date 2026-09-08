package com.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LocationReportResponse {
    private Long vehicleId;
    private LocalDateTime locationUpdatedAt;
}
