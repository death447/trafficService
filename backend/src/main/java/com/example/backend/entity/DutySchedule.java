package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DutySchedule {
    private Long id;
    private LocalDate dutyDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long userId;
    private String roleType;
    private Long districtId;
    private Long vehicleId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
