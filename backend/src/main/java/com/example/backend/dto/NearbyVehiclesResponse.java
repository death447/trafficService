package com.example.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class NearbyVehiclesResponse {
    private MatchedDistrictVO matchedDistrict;
    private List<NearbyVehicleVO> vehicles;
}
