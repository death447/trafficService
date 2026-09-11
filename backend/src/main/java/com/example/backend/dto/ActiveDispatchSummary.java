package com.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ActiveDispatchSummary {
    private Long id;
    private String orderNo;
    private String status;
    private String plateNo;
    private String vehicleTypeName;
    private String accidentAddress;
    private LocalDateTime checkedInAt;
    private List<String> damagePhotoPaths = new ArrayList<>();
}
