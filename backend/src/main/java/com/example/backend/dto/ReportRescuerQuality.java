package com.example.backend.dto;

import lombok.Data;

@Data
public class ReportRescuerQuality {
    private Long rescuerId;
    private String rescuerName;
    private long ratedCount;
    private Double avgPunctual;
    private Double avgStandard;
    private Double avgSafety;
    private Double avgAttitude;
    private Double avgOverall;
}
