package com.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleRequest {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long userId;
    private String roleType;
    private Long districtId;
    private Long vehicleId;
    private String remark;
}
