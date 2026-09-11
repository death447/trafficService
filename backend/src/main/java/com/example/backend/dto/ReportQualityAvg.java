package com.example.backend.dto;

import lombok.Data;

@Data
public class ReportQualityAvg {
    private long ratedCount;
    private Double avgPunctual;
    private Double avgStandard;
    private Double avgSafety;
    private Double avgAttitude;
}
