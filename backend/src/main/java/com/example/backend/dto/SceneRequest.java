package com.example.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class SceneRequest {
    private String plateNo;
    private String vehicleType;
    private String damageDesc;
    private String sceneRemark;
    /** 逐车现场信息；为空时按旧的单车字段保存。 */
    private List<SceneVehicleItem> vehicles;
}
