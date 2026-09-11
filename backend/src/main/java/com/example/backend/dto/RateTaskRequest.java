package com.example.backend.dto;

import lombok.Data;

@Data
public class RateTaskRequest {
    private Integer scorePunctual;
    private Integer scoreStandard;
    private Integer scoreSafety;
    private Integer scoreAttitude;
    private String comment;
}
