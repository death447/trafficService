package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParkingLot {
    private Long id;
    private String name;
    private String code;
    private String address;
    private String contactName;
    private String contactPhone;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
