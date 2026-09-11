package com.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportOrderRow {
    private Long id;
    private String orderNo;
    private String accidentAddress;
    private String status;
    private String plateNo;
    private String rescuerName;
    private LocalDateTime createTime;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
}
