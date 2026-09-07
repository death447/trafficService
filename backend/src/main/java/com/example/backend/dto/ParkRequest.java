package com.example.backend.dto;

import lombok.Data;

@Data
public class ParkRequest {
    private String parkAddress;
    private String parkRemark;
}
