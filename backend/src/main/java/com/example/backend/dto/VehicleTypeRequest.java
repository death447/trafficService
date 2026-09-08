package com.example.backend.dto;

import lombok.Data;

@Data
public class VehicleTypeRequest {
    private String name;
    private Integer sortOrder;
    private String status;
    private String remark;
}
