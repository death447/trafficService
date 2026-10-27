package com.example.backend.dto;

import com.example.backend.entity.DispatchMedia;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SceneVehicleVO {
    private Long id;
    private Long accidentVehicleId;
    private String plateNo;
    private String vehicleType;
    private String damageDesc;
    private List<DispatchMedia> medias = new ArrayList<>();
}
