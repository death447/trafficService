package com.example.backend.dto;

import com.example.backend.entity.RescueVehicle;
import lombok.Data;

@Data
public class NearbyVehicleVO {
    private RescueVehicle vehicle;
    private Double distanceMeters;
    private boolean inMatchedDistrict;
    private boolean locationFresh;
    private java.time.LocalDateTime locationUpdatedAt;
}
