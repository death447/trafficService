package com.example.backend.dto;

import lombok.Data;

@Data
public class SceneRequest {
    private String plateNo;
    private String vehicleType;
    private String damageDesc;
    private String sceneRemark;
}
