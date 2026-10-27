package com.example.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MeResponse {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private List<String> permissions;
    private List<String> roles;
}
