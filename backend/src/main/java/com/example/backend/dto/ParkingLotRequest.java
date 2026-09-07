package com.example.backend.dto;

import lombok.Data;

@Data
public class ParkingLotRequest {
    private String name;
    private String code;
    private String address;
    private String contactName;
    private String contactPhone;
    private String status;
    private String remark;
}
