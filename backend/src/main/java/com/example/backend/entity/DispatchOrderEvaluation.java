package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchOrderEvaluation {
    private Long id;
    private Long dispatchOrderId;
    private Long raterUserId;
    private Integer scorePunctual;
    private Integer scoreStandard;
    private Integer scoreSafety;
    private Integer scoreAttitude;
    private String comment;
    private LocalDateTime createTime;
}
