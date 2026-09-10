package com.example.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParkingArea {
    private Long id;
    private Long parkingLotId;
    private String name;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
