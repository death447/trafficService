package com.example.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReportSummary {
    private String from;
    private String to;
    private DispatchBlock dispatch = new DispatchBlock();
    private QualityBlock quality = new QualityBlock();
    private DetainBlock detain = new DetainBlock();

    @Data
    public static class DispatchBlock {
        private long total;
        private long completed;
        private long inProgress;
        private long aborted;
        private List<ReportDateCount> trend = new ArrayList<>();
        private List<ReportStatusCount> statusDist = new ArrayList<>();
        private List<ReportOrderRow> orders = new ArrayList<>();
    }

    @Data
    public static class QualityBlock {
        private long ratedCount;
        private Double avgPunctual;
        private Double avgStandard;
        private Double avgSafety;
        private Double avgAttitude;
        private List<ReportRescuerQuality> byRescuer = new ArrayList<>();
    }

    @Data
    public static class DetainBlock {
        private long inbound;
        private long inYard;
        private long outbound;
        private List<ReportLotDetain> byLot = new ArrayList<>();
    }
}
