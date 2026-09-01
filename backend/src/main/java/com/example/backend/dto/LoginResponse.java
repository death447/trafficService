package com.example.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private List<String> permissions;
    private List<String> roles;
}
