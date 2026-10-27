package com.example.backend.dto;

import lombok.Data;

@Data
public class SceneVehicleItem {
    private Long accidentVehicleId;
    private String plateNo;
    private String vehicleType;
    private String damageDesc;
}
