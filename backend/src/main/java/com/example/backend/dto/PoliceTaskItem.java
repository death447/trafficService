package com.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PoliceTaskItem {
    private Long id;
    private String orderNo;
    private String status;
    private String accidentAddress;
    private String plateNo;
    private String partyName;
    private String partyPhone;
    private LocalDateTime createTime;
    private boolean rated;
}
