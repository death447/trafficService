package com.example.backend.dto;

import lombok.Data;

@Data
public class RescuerProfileUpdateRequest {
    private String realName;
    private String phone;
    private String email;
}
